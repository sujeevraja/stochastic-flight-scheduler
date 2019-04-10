package stochastic.solver;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.delay.Scenario;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.model.MasterModelBuilder;
import stochastic.model.SubModelBuilder;
import stochastic.network.Path;
import stochastic.output.RescheduleSolution;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.Constants;
import stochastic.utility.OptException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

public class DepSolver {
    private final static Logger logger = LogManager.getLogger(DepSolver.class);
    private double objValue;
    private double solutionTimeInSeconds;
    private RescheduleSolution depSolution;

    public DepSolver() {}

    public void solve(DataRegistry dataRegistry) throws OptException {
        try {
            logger.info("starting DEP...");
            IloCplex cplex = new IloCplex();
            if (!Parameters.isDebugVerbose())
                cplex.setOut(null);

            // master model
            ArrayList<Leg> legs = dataRegistry.getLegs();
            ArrayList<Tail> tails = dataRegistry.getTails();
            MasterModelBuilder masterModelBuilder = new MasterModelBuilder(legs, tails, cplex);

            masterModelBuilder.buildVariables();

            IloLinearNumExpr objExpr = masterModelBuilder.getObjExpr();
            masterModelBuilder.constructFirstStage();
            logger.info("added terms from master problem");

            // sub models
            Scenario[] scenarios = dataRegistry.getDelayScenarios();
            for (int i = 0; i < scenarios.length; ++i) {
                Scenario s = scenarios[i];
                int[] delays = SolverUtility.getTotalDelays(s.getPrimaryDelays(), legs.size());
                ArrayList<Path> allPaths = dataRegistry.getNetwork().enumeratePathsForTails(tails, delays);

                HashMap<Integer, ArrayList<Path>> tailPathsMap = SolverUtility.getPathsForFullEnum(allPaths, tails);

                SubModelBuilder subModelBuilder = new SubModelBuilder(i, legs, tails, tailPathsMap, cplex);
                subModelBuilder.buildObjective(objExpr, s.getProbability());
                subModelBuilder.addPathVarsToConstraints();
                subModelBuilder.updateModelWithFirstStageVars(masterModelBuilder.getX());
                subModelBuilder.addConstraintsToModel();
                logger.info("added terms for scenario " + (i + 1) + " of " + scenarios.length);
            }

            // solving
            logger.info("starting to solve DEP");
            cplex.addMinimize(objExpr);
            if (Parameters.isDebugVerbose())
                cplex.exportModel("logs/dep.lp");

            Instant start = Instant.now();
            cplex.solve();
            solutionTimeInSeconds = Duration.between(start, Instant.now()).toMillis() / 1000.0;
            logger.info("DEP solution time (seconds): " + solutionTimeInSeconds);

            objValue = cplex.getObjValue();
            logger.info("DEP objective: " + objValue);
            if (Parameters.isDebugVerbose())
                cplex.writeSolution("logs/dep_solution.xml");

            double[][] xValues = masterModelBuilder.getxValues();
            double rescheduleCost = 0;
            int[] reschedules = new int[legs.size()];
            int[] durations = Parameters.getDurations();
            for(int i = 0; i < durations.length; ++i) {
                for (int j = 0; j < legs.size(); ++j) {
                    if (xValues[i][j] >= Constants.EPS) {
                        reschedules[j] = durations[i];
                        rescheduleCost += durations[i] * legs.get(j).getRescheduleCostPerMin();
                    }
                    else {
                        reschedules[j] = 0;
                    }
                }
            }

            depSolution = new RescheduleSolution("dep", rescheduleCost, reschedules);

            cplex.end();
            logger.info("completed DEP");
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("cplex error solving DEP model");
        }
    }

    public double getObjValue() {
        return objValue;
    }

    public double getSolutionTimeInSeconds() {
        return solutionTimeInSeconds;
    }

    public RescheduleSolution getDepSolution() {
        return depSolution;
    }
}
