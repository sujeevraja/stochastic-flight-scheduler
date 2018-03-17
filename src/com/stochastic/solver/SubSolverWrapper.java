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
    private final static double eps = 1.0e-5;
    private static DataRegistry dataRegistry;
    private static double alpha;
    private static int    iter;
    private static double[][] beta;
    private static int numThreads = 2;

    private static double[][] xValues;
    private static double uBound;
        

    public static void SubSolverWrapperInit(DataRegistry dataRegistry, double[][] xValues) throws OptException {
        try {
            SubSolverWrapper.dataRegistry = dataRegistry;
            SubSolverWrapper.xValues = xValues;

            alpha = 0;
            uBound = MasterSolver.getFSObjValue();
            beta = new double[dataRegistry.getDurations().size()][dataRegistry.getLegs().size()];
        } catch (Exception e) {
            logger.error(e.getStackTrace());
            throw new OptException("error at SubSolverWrapperInit");
        }
    }

    private synchronized static void calculateAlpha(double[] duals1, double[] duals2, double[] duals3, double[] duals4) {
        ArrayList<Leg> legs = dataRegistry.getLegs();
        
        logger.debug("initial alpha value: " + alpha);
        
        for (int j = 0; j < legs.size(); j++)
        {
            if(Math.abs(duals1[j]) >= eps) {
                logger.debug(" j: " + j + " duals1[j]: " + duals1[j]); // + " prb: " + prb);
                alpha += (duals1[j]); //*prb);
            }
        }

        for (int j = 0; j < dataRegistry.getTails().size(); j++)
        {
            if(Math.abs(duals2[j]) >= eps) {
                logger.debug(" j: " + j + " duals2[j]: " + duals2[j]); // + " prb: " + prb);
                alpha += (duals2[j]); //*prb);
            }
        }

        for (int j = 0; j < legs.size(); j++)
        {
            if(Math.abs(duals3[j]) >= eps) {
                logger.debug(" j: " + j + " duals3[j]: " + duals3[j]); // + " prb: " + prb);
                alpha += (duals3[j] * 14); //prb*14);
            }
        }
        
        for (int j = 0; j < duals4.length; j++)
        {
            if(Math.abs(duals4[j]) >= eps) {
                logger.debug(" j: " + j + " duals4[j]: " + duals4[j]); // + " prb: " + prb);
                alpha += (duals4[j]); //*prb);
            }
        }
        
        logger.debug(" final alpha value: " + alpha);
    }

    private synchronized static void calculateBeta(double[] duals) {
        ArrayList<Integer> durations = dataRegistry.getDurations();
        ArrayList<Leg> legs = dataRegistry.getLegs();

        for (int i = 0; i < durations.size(); i++)
            for (int j = 0; j < legs.size(); j++) {
                if(Math.abs(duals[j]) >= eps) {
                    beta[i][j] += duals[j] * -durations.get(i); // * prb;
                    logger.debug(" i: " + i + " j: " + j + " b: " + beta[i][j]
                            + " d: " + duals[j] + " d: " + durations.get(i)); // + prb);
                }
            }

    }

    public void solveSequential(ArrayList<Integer> scenarioDelays, ArrayList<Double> probabilities) {
        // final int numScenarios = 5;
        final int numScenarios = dataRegistry.getNumScenarios();
        for (int i = 0; i < numScenarios; i++) {
        	DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), scenarioDelays.get(i));
            HashMap<Integer, Integer> legDelays = dgen.generateDelays();
            
            // HashMap<Integer, Integer> legDelays = new HashMap<>();

            SubSolverRunnable subSolverRunnable = new SubSolverRunnable(i, legDelays, probabilities.get(i));
            subSolverRunnable.run();
            logger.info("Solved scenario " + i);
        }
    }

    public void solveParallel(ArrayList<Integer> scenarioDelays, ArrayList<Double> probabilities) throws OptException {
        try {
            ExecutorService exSrv = Executors.newFixedThreadPool(numThreads);
            
            for (int i = 0; i < dataRegistry.getNumScenarios(); i++) {
                // Thread.sleep(500);

                DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), scenarioDelays.get(i));
                HashMap<Integer, Integer> legDelays = dgen.generateDelays();

                SubSolverRunnable subSolverRunnable = new SubSolverRunnable(i, legDelays, probabilities.get(i));
                exSrv.execute(subSolverRunnable); // this calls run() method below
                logger.info("Solved scenario " + i);
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
                SubSolver s = new SubSolver(randomDelays, probability, scenarioNum);
                s.constructSecondStage(xValues, dataRegistry);
                s.solve();
                s.writeLPFile("SS", iter, this.scenarioNum);
                uBound += (s.getObjValue());
                calculateAlpha(s.getDuals1(), s.getDuals2(), s.getDuals3(), s.getDuals4());
                calculateBeta(s.getDuals3());
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
