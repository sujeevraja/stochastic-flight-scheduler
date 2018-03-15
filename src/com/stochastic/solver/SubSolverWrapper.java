package com.stochastic.solver;

import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
import com.stochastic.domain.Leg;
import com.stochastic.registry.DataRegistry;
import com.stochastic.utility.OptException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SubSolverWrapper {
    /**
     * Wrapper class that can be used to solve the second-stage problems in parallel.
     */
    private final static Logger logger = LogManager.getLogger(SubSolverWrapper.class);
    private static DataRegistry dataRegistry;
    private static double alpha;
    private static double[][] beta;
    private static int numThreads = 2;

    private static double[][] xValues;
    private static double uBound;

    public static void SubSolverWrapperInit(DataRegistry dataRegistry, double[][] xValues) throws OptException {
        try {
            SubSolverWrapper.dataRegistry = dataRegistry;
            SubSolverWrapper.xValues = xValues;

            alpha = 0;
            uBound = 0;
            beta = new double[dataRegistry.getDurations().size()][dataRegistry.getLegs().size()];
        } catch (Exception e) {
            logger.error(e.getStackTrace());
            throw new OptException("error at SubSolverWrapperInit");
        }
    }

    private synchronized static void calculateAlpha(double[] duals, double prb) {
        ArrayList<Leg> legs = dataRegistry.getLegs();
        for (int j = 0; j < legs.size(); j++)
            alpha = (duals[j] * -legs.get(j).getDepTimeInMin() * prb);
    }

    private synchronized static void calculateBeta(double[] duals, double prb) {
        ArrayList<Integer> durations = dataRegistry.getDurations();
        ArrayList<Leg> legs = dataRegistry.getLegs();

        for (int i = 0; i < durations.size(); i++)
            for (int j = 0; j < legs.size(); j++)
                beta[i][j] = duals[j] * xValues[i][j] * prb;
    }

    public void solveSequential(ArrayList<Integer> scenarioDelays, ArrayList<Double> probabilities) {
        final int numScenarios = dataRegistry.getNumScenarios();
        for (int i = 0; i < numScenarios; i++) {
            DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), scenarioDelays.get(i));
            HashMap<Integer, Integer> legDelays = dgen.generateDelays();

            SubSolverRunnable subSolverRunnable = new SubSolverRunnable(i, legDelays, probabilities.get(i));
            subSolverRunnable.run();
            logger.info("Solved scenario " + i);
        }
    }

    public void solveParallel(ArrayList<Integer> scenarioDelays, ArrayList<Double> probabilities) throws OptException {
        try {
            ExecutorService exSrv = Executors.newFixedThreadPool(numThreads);

            for (int i = 0; i < dataRegistry.getNumScenarios(); i++) {
                Thread.sleep(500);

                DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), scenarioDelays.get(i));
                HashMap<Integer, Integer> legDelays = dgen.generateDelays();

                SubSolverRunnable subSolverRunnable = new SubSolverRunnable(i, legDelays, probabilities.get(i));
                exSrv.execute(subSolverRunnable); // this calls run() method below
                subSolverRunnable.run();
            }

            exSrv.shutdown();
            while (!exSrv.isTerminated())
                Thread.sleep(100);
        } catch (InterruptedException ie) {
            logger.error(ie.getStackTrace());
            throw new OptException("error at buildSubModel");
        }
    }

    class SubSolverRunnable implements Runnable {
        private int scenarioNum;
        private HashMap<Integer, Integer> randomDelays;
        private double probability;

        SubSolverRunnable(int scenarioNum, HashMap<Integer, Integer> randomDelays, double probability) {
            this.scenarioNum = scenarioNum;
            this.randomDelays = randomDelays;
            this.probability = probability;
        }

        //exSrv.execute(buildSDThrObj) calls brings you here
        public void run() {
            try {
                // beta x + theta >= alpha - Benders cut
                SubSolver s = new SubSolver(randomDelays);
                s.constructSecondStage(xValues, dataRegistry);
                s.solve();
                uBound += (probability * s.getObjValue());
                calculateAlpha(s.getDuals(), probability);
                calculateBeta(s.getDuals(), probability);
                s.end();
            } catch (OptException oe) {
                logger.error("submodel run for scenario " + scenarioNum + " failed.");
                logger.error(oe);
                System.exit(17);
            }
        }
    }

    public static double getuBound() {
        return uBound;
    }

    public static void setuBound(double uBound) {
        SubSolverWrapper.uBound = uBound;
    }

    public static double getAlpha() {
        return alpha;
    }

    public static void setAlpha(double alpha) {
        SubSolverWrapper.alpha = alpha;
    }

    public static double[][] getBeta() {
        return beta;
    }

    public static void setBeta(double[][] beta) {
        SubSolverWrapper.beta = beta;
    }
}
