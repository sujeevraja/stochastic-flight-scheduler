package stochastic.solver;

import stochastic.delay.Scenario;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.OptException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class SubSolverWrapper {
    /**
     * Wrapper class that can be used to solve the second-stage problems in parallel.
     */
    private final static Logger logger = LogManager.getLogger(SubSolverWrapper.class);
    private DataRegistry dataRegistry;
    private int[] reschedules; // planned delays from first stage solution.
    private double[] thetaValues;
    private int iter;
    private PathCache[] pathCaches;
    private BendersData bendersData; // this object will be shared across threads by SubSolverRunnable.

    SubSolverWrapper(DataRegistry dataRegistry, int[] reschedules, double[] thetaValues, int iter, double uBound,
                     PathCache[] pathCaches) {
        this.dataRegistry = dataRegistry;
        this.reschedules = reschedules;
        this.thetaValues = thetaValues;
        this.iter = iter;
        this.pathCaches = pathCaches;
        this.bendersData = new BendersData(uBound);

        final int numLegs = dataRegistry.getLegs().size();
        final int numCuts = Parameters.isBendersMultiCut() ? dataRegistry.getDelayScenarios().length : 1;
        for (int i = 0; i < numCuts; ++i)
            bendersData.addCut(new BendersCut(0.0, numLegs));
    }

    void solveSequential() {
        Scenario[] scenarios = dataRegistry.getDelayScenarios();
        for (int i = 0; i < scenarios.length; i++) {
            Scenario scenario = scenarios[i];
            SubSolverRunnable subSolverRunnable = new SubSolverRunnable(dataRegistry, iter, i,
                    scenario.getProbability(), reschedules, thetaValues[i], scenario.getPrimaryDelays(), pathCaches[i]);
            subSolverRunnable.setBendersData(bendersData);
            subSolverRunnable.run();
        }
    }

    void solveParallel() throws OptException {
        try {
            Scenario[] scenarios = dataRegistry.getDelayScenarios();
            ExecutorService exSrv = Executors.newFixedThreadPool(Parameters.getNumThreadsForSecondStage());
            for (int i = 0; i < scenarios.length; i++) {
                Scenario scenario = scenarios[i];
                SubSolverRunnable subSolverRunnable = new SubSolverRunnable(dataRegistry, iter, i,
                        scenario.getProbability(), reschedules, thetaValues[i], scenario.getPrimaryDelays(), pathCaches[i]);
                subSolverRunnable.setBendersData(bendersData);
                exSrv.execute(subSolverRunnable); // this calls SubSolverRunnable.run()
            }
            exSrv.shutdown();

            while (!exSrv.isTerminated())
                Thread.sleep(100);
        } catch (InterruptedException ie) {
            logger.error(ie.getStackTrace());
            throw new OptException("error in SolveParallel");
        }
    }

    BendersData getBendersData() {
        return bendersData;
    }
}
