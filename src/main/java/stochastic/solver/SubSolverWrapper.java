package stochastic.solver;

import ilog.cplex.IloCplex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.actor.ActorManager;
import stochastic.delay.Scenario;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.OptException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wrapper class that can be used to solve the second-stage problems in parallel.
 */
class SubSolverWrapper {
    private final static Logger logger = LogManager.getLogger(SubSolverWrapper.class);
    private DataRegistry dataRegistry;
    private int[] reschedules; // planned delays from first stage solution.
    private int iter;
    private PathCache[] pathCaches;
    private BendersData bendersData; // will be shared across threads by SubSolverRunnable.

    SubSolverWrapper(DataRegistry dataRegistry, int[] reschedules, int iter, double uBound,
                     PathCache[] pathCaches) {
        this.dataRegistry = dataRegistry;
        this.reschedules = reschedules;
        this.iter = iter;
        this.pathCaches = pathCaches;
        this.bendersData = new BendersData(uBound);

        final int numLegs = dataRegistry.getLegs().size();
        final int numCuts = Parameters.isBendersMultiCut()
            ? dataRegistry.getDelayScenarios().length
            : 1;
        for (int i = 0; i < numCuts; ++i)
            bendersData.addCut(new BendersCut(0.0, numLegs));
    }

    void solveSequential(IloCplex cplex) {
        Scenario[] scenarios = dataRegistry.getDelayScenarios();
        for (int i = 0; i < scenarios.length; i++) {
            Scenario scenario = scenarios[i];
            SubSolverRunnable subSolverRunnable = new SubSolverRunnable(dataRegistry, iter, i,
                    scenario.getProbability(), reschedules, scenario.getPrimaryDelays(),
                pathCaches[i]);
            subSolverRunnable.setCplex(cplex);
            subSolverRunnable.setBendersData(bendersData);
            subSolverRunnable.run();
        }
    }

    void solveParallel() throws OptException {
        ActorManager actorManager = new ActorManager();
        actorManager.createActors(Parameters.getNumThreadsForSecondStage(),
            !Parameters.isDebugVerbose());

        Scenario[] scenarios = dataRegistry.getDelayScenarios();
        actorManager.initBendersData(bendersData, scenarios.length);

        SubSolverRunnable[] models = new SubSolverRunnable[scenarios.length];
        for (int i = 0; i < scenarios.length; i++) {
            Scenario scenario = scenarios[i];
            models[i] = new SubSolverRunnable(dataRegistry, iter, i,
                scenario.getProbability(), reschedules, scenario.getPrimaryDelays(),
                pathCaches[i]);
        }

        bendersData = actorManager.solveModels(models);
        actorManager.end();
    }

    BendersData getBendersData() {
        return bendersData;
    }
}
