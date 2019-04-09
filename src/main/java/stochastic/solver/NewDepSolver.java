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
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.OptException;

import java.util.ArrayList;
import java.util.HashMap;

public class NewDepSolver {
    private final static Logger logger = LogManager.getLogger(MasterSolver.class);

    public NewDepSolver() {}

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

                SubModelBuilder subModelBuilder = new SubModelBuilder(legs, tails, tailPathsMap, cplex);
                subModelBuilder.buildObjective(objExpr, s.getProbability());
                subModelBuilder.addPathVarsToConstraints();
                subModelBuilder.updateModelWithFirstStageVars(masterModelBuilder.getX());
                subModelBuilder.addConstraintsToModel();
                logger.info("added terms for scenario " + (i+1) + " of " + scenarios.length);
            }

            // solving
            logger.info("starting to solve DEP");
           cplex.addMinimize(objExpr);
           if (Parameters.isDebugVerbose())
               cplex.exportModel("logs/dep.lp");
           cplex.solve();
           double obj = cplex.getObjValue();
            logger.info("DEP objective: " + obj);
           if (Parameters.isDebugVerbose())
               cplex.writeSolution("logs/dep_solution.xml");
           cplex.end();
           logger.info("completed DEP");
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("cplex error solving DEP model");
        }
    }
}
