package com.stochastic.solver;

import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.Constants;
import com.stochastic.utility.OptException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ilog.concert.IloException;
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

    public void solveSequential() {
        final int numScenarios = dataRegistry.getNumScenarios();
        final int[] scenarioDelays = dataRegistry.getScenarioDelays();
        final double[] probabilities = dataRegistry.getScenarioProbabilities();

        for (int i = 0; i < numScenarios; i++) {
            DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), scenarioDelays[i]);
            HashMap<Integer, Integer> legDelays = dgen.generateDelays();
            SubSolverRunnable subSolverRunnable = new SubSolverRunnable(dataRegistry, iter, i, probabilities[i],
                    reschedules, legDelays, bendersData);
            subSolverRunnable.run();
        }
    }

    public void solveParallel() throws OptException {
        try {
            final int numScenarios = dataRegistry.getNumScenarios();
            final int[] scenarioDelays = dataRegistry.getScenarioDelays();
            final double[] probabilities = dataRegistry.getScenarioProbabilities();

            ExecutorService exSrv = Executors.newFixedThreadPool(Parameters.getNumThreadsForSecondStage());
            for (int i = 0; i < numScenarios; i++) {
                DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), scenarioDelays[i]);
                HashMap<Integer, Integer> legDelays = dgen.generateDelays();

                SubSolverRunnable subSolverRunnable = new SubSolverRunnable(dataRegistry, iter, i, probabilities[i],
                        reschedules, legDelays, bendersData);
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

    public BendersData getBendersData() {
        return bendersData;
    }
}
