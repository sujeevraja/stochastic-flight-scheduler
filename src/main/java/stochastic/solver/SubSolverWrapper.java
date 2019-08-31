package stochastic.solver;

import ilog.cplex.IloCplex;
import stochastic.actor.ActorManager;
import stochastic.delay.Scenario;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.OptException;

/**
 * Wrapper class that can be used to solve the second-stage problems in parallel.
 */
class SubSolverWrapper {
    private static ActorManager actorManager;
    private DataRegistry dataRegistry;
    private int[] reschedules; // planned delays from first stage solution.
    private int iter;
    private double uBound;
    private PathCache[] pathCaches;

    SubSolverWrapper(DataRegistry dataRegistry, int[] reschedules, int iter, double uBound,
                     PathCache[] pathCaches) {
        this.dataRegistry = dataRegistry;
        this.reschedules = reschedules;
        this.iter = iter;
        this.uBound = uBound;
        this.pathCaches = pathCaches;
    }

    BendersData solveSequential(IloCplex cplex) {
        BendersData bendersData = buildBendersData();
        Scenario[] scenarios = dataRegistry.getDelayScenarios();
        for (int i = 0; i < scenarios.length; i++) {
            Scenario scenario = scenarios[i];
            final double probability = scenario.getProbability();
            SubSolverRunnable ssr = new SubSolverRunnable(dataRegistry, iter, i,
                probability, reschedules, scenario.getPrimaryDelays(),
                pathCaches[i]);
            ssr.setCplex(cplex);
            ssr.setBendersData(bendersData);
            ssr.run();

            bendersData.updateAlpha(ssr.getCutNum(), ssr.getAlpha(), probability);
            bendersData.updateBeta(ssr.getCutNum(), ssr.getDualsDelay(), probability,
                ssr.getDualRisk());
            bendersData.setUpperBound(bendersData.getUpperBound() +
                (ssr.getObjValue() * probability));
        }
        return bendersData;
    }

    BendersData solveParallel() throws OptException {
        BendersData bendersData = buildBendersData();
        Scenario[] scenarios = dataRegistry.getDelayScenarios();
        actorManager.initBendersData(bendersData, scenarios.length);

        SubSolverRunnable[] models = new SubSolverRunnable[scenarios.length];
        for (int i = 0; i < scenarios.length; i++) {
            Scenario scenario = scenarios[i];
            models[i] = new SubSolverRunnable(dataRegistry, iter, i,
                scenario.getProbability(), reschedules, scenario.getPrimaryDelays(),
                pathCaches[i]);
        }

        return actorManager.solveModels(models);
    }

    private BendersData buildBendersData() {
        BendersData bendersData = new BendersData(uBound);
        final int numLegs = dataRegistry.getLegs().size();
        final int numCuts = Parameters.isBendersMultiCut()
            ? dataRegistry.getDelayScenarios().length
            : 1;
        for (int i = 0; i < numCuts; ++i)
            bendersData.addCut(new BendersCut(0.0, numLegs));
        return bendersData;
    }


    static void initActorManager() {
        actorManager = new ActorManager();
        actorManager.createActors(Parameters.getNumThreadsForSecondStage(),
            !Parameters.isDebugVerbose());
    }

    static void clearActorManager() {
        actorManager.end();
        actorManager = null;
    }
}
