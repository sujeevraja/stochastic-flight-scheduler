package com.stochastic.solver;

import com.stochastic.delay.Scenario;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.OptException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SubSolverWrapper {
    /**
     * Wrapper class that can be used to solve the second-stage problems in parallel.
     */
    private final static Logger logger = LogManager.getLogger(SubSolverWrapper.class);
    private DataRegistry dataRegistry;
    private int[] reschedules; // planned delays from first stage solution.
    private int iter;
    private BendersData bendersData; // this object will be shared across threads by SubSolverRunnable.

    public SubSolverWrapper(DataRegistry dataRegistry, int[] reschedules, int iter, double uBound) {
        this.dataRegistry = dataRegistry;
        this.reschedules = reschedules;
        this.iter = iter;
        this.bendersData = new BendersData(uBound, 0,
                new double[Parameters.getNumDurations()][dataRegistry.getLegs().size()]);
    }

    void solveSequential() {
        Scenario[] scenarios = dataRegistry.getDelayScenarios();
        for (int i = 0; i < scenarios.length; i++) {
            Scenario scenario = scenarios[i];
            SubSolverRunnable subSolverRunnable = new SubSolverRunnable(dataRegistry, iter, i,
                    scenario.getProbability(), reschedules, scenario.getPrimaryDelays());
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
                        scenario.getProbability(), reschedules, scenario.getPrimaryDelays());
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
