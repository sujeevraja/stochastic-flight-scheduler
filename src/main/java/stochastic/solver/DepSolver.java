package stochastic.solver;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.delay.Scenario;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.main.ModelStats;
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
    private ModelStats modelStats;

    public DepSolver() {}

    public void solve(DataRegistry dataRegistry) throws OptException {
        try {
            logger.info("starting DEP...");
            IloCplex cplex = new IloCplex();
            cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, Constants.CPLEX_MIP_GAP);
            if (!Parameters.isDebugVerbose())
                cplex.setOut(null);

            // master model
            ArrayList<Leg> legs = dataRegistry.getLegs();
            ArrayList<Tail> tails = dataRegistry.getTails();
            MasterModelBuilder masterModelBuilder = new MasterModelBuilder(legs, tails,
                    dataRegistry.getRescheduleTimeBudget(), cplex);

            masterModelBuilder.buildVariables();

            IloLinearNumExpr objExpr = masterModelBuilder.getObjExpr();
            masterModelBuilder.constructFirstStage();
            logger.info("added terms from master problem");

            // sub models
            Scenario[] scenarios = dataRegistry.getDelayScenarios();
            SubModelBuilder[] subModelBuilders = new SubModelBuilder[scenarios.length];
            for (int i = 0; i < scenarios.length; ++i) {
                Scenario s = scenarios[i];
                ArrayList<Path> allPaths = dataRegistry.getNetwork().enumeratePathsForTails(
                        tails, s.getPrimaryDelays());

                HashMap<Integer, ArrayList<Path>> tailPathsMap = SolverUtility.getPathsForFullEnum(allPaths, tails);

                SubModelBuilder subModelBuilder = new SubModelBuilder(i, legs, tails, tailPathsMap, cplex);
                subModelBuilder.buildObjective(objExpr, s.getProbability());
                subModelBuilder.addPathVarsToConstraints();
                subModelBuilder.updateModelWithFirstStageVars(masterModelBuilder.getX());
                subModelBuilder.addConstraintsToModel();
                subModelBuilders[i] = subModelBuilder;
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

            // collect model stats
            objValue = cplex.getObjValue();
            logger.info("DEP objective: " + objValue);
            modelStats = new ModelStats(cplex.getNrows(), cplex.getNcols(), cplex.getNNZs(),
                                        objValue);

            if (Parameters.isDebugVerbose())
                cplex.writeSolution("logs/dep_solution.xml");

            int[] reschedules = new int[legs.size()];
            double[] xValues = masterModelBuilder.getxValues();
            double rescheduleCost = 0;
            for (int i = 0; i < xValues.length; ++i) {
                if (xValues[i] >= Constants.EPS) {
                    reschedules[i] = (int) Math.round(xValues[i]);
                    rescheduleCost += legs.get(i).getRescheduleCostPerMin() * reschedules[i];
                } else {
                    reschedules[i] = 0;
                }
            }

            logger.info("DEP reschedule cost: " + rescheduleCost);
            depSolution = new RescheduleSolution("dep", rescheduleCost, reschedules);

            masterModelBuilder.clearCplexObjects();
            for (SubModelBuilder subModelBuilder : subModelBuilders)
                subModelBuilder.clearCplexObjects();
            cplex.clearModel();
            cplex.endModel();
            cplex.end();

            logger.info("completed DEP");
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("cplex error solving DEP model");
        }
    }

    public ModelStats getModelStats() {
        return modelStats;
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
