package com.stochastic.postopt;

import com.stochastic.controller.Controller;
import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
import com.stochastic.domain.Leg;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.solver.SubSolver;
import com.stochastic.utility.Constants;
import com.stochastic.utility.OptException;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

public class SolutionManager {
    /**
     * Class used to store stochastic programming solution, qualify it by comparing it with deterministic solutions
     * and writing final output to a text file.
     */
    private final static Logger logger = LogManager.getLogger(SolutionManager.class);
    private String instancePath;
    private DataRegistry dataRegistry;
    private int[] scenarioDelays;
    private double[] scenarioProbabilities;
    private double[][] xValues; // xValues[i][j] = 1 if durations[i] is selected for legs[j], 0 otherwise.
    private boolean solutionsCompared;

    private ArrayList<Integer> rescheduleLegIndices;
    private ArrayList<Integer> rescheduleTimes;
    private static ArrayList<Integer> testDelays;
    private int[] deterministicObjs;
    private int[] stochasticObjs;

    public SolutionManager(DataRegistry dataRegistry, double[][] xValues) {
        this.instancePath = Parameters.getInstancePath();
        this.dataRegistry = dataRegistry;
        this.scenarioDelays = dataRegistry.getScenarioDelays();
        this.scenarioProbabilities = dataRegistry.getScenarioProbabilities();
        this.xValues = xValues;
        solutionsCompared = false;

        rescheduleLegIndices = new ArrayList<>();
        rescheduleTimes = new ArrayList<>();

        int[] durations = Parameters.getDurations();
        ArrayList<Leg> legs = dataRegistry.getLegs();
        for(int i = 0; i < durations.length; ++i) {
            for(int j = 0; j < legs.size(); ++j) {
                if(xValues[i][j] >= Constants.EPS) {
                    rescheduleLegIndices.add(j);
                    rescheduleTimes.add(durations[i]);
                }
            }
        }
    }

    public final void compareSolutions(int numTestScenarios) throws OptException {
//        generateDelaysForComparison(numTestScenarios);

        deterministicObjs = new int[testDelays.size()];
        stochasticObjs = new int[testDelays.size()];
        ArrayList<Leg> legs = dataRegistry.getLegs();

        int[] zeroes = new int[dataRegistry.getLegs().size()];
        Arrays.fill(zeroes, 0);

        for(int i = 0; i < testDelays.size(); ++i) {
            Integer currDelay = testDelays.get(i);
            logger.info("Current test delay: " + currDelay);
            DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), currDelay);

            // solve model without rescheduling legs
            HashMap<Integer, Integer> legDelayMap = dgen.generateDelays();
            SubSolver determSolver = new SubSolver(dataRegistry.getTails(), dataRegistry.getLegs(), zeroes,1.0);
            determSolver.constructSecondStage(null);
            determSolver.solve();
            determSolver.end();
            deterministicObjs[i] = (int) Math.round(determSolver.getObjValue());
            
            logger.debug(" xxx-i: " + i + " : " + deterministicObjs[i] + " rescheduleLegIndices: " + rescheduleLegIndices.size());

            // Update leg departure times based on reschedule solution of stochastic model.
            // Also reduce random delays by rescheduled times wherever possible.
            for(int j = 0; j < rescheduleLegIndices.size(); ++j) {
                Integer legIndex = rescheduleLegIndices.get(j);
                Integer rescheduleTime = rescheduleTimes.get(j);

                legs.get(legIndex).reschedule(rescheduleTime);
                if(!legDelayMap.containsKey(legIndex))
                    continue;

                // If random delay time <= reschedule time, then the delay is absorbed by the flight's new scheduled
                // departure time. So, it will experience no delay. Otherwise, the net delay experienced by the flight
                // is (random delay time  - rescheduled time).
                Integer randomDelay = legDelayMap.get(legIndex);
                if(randomDelay <= rescheduleTime)
                    legDelayMap.replace(legIndex, 0); //.remove(legIndex);
                else
                    legDelayMap.replace(legIndex, randomDelay - rescheduleTime);
            }

            // solve model with reschedule legs
            SubSolver stocSolver = new SubSolver(dataRegistry.getTails(), dataRegistry.getLegs(), zeroes, 1.0);
            stocSolver.constructSecondStage(null);
            stocSolver.solve();
            stocSolver.end();
            stochasticObjs[i] = (int) Math.round(stocSolver.getObjValue());

            // reset legs back to original times
            for(int j = 0; j < rescheduleLegIndices.size(); ++j)
                legs.get(rescheduleLegIndices.get(j)).reschedule(-rescheduleTimes.get(j));
        }
        solutionsCompared = true;
    }

    public static void generateDelaysForComparison(int numTestScenarios, DataRegistry dataRegistry) {
        // generate random delay data to compare solutions.
        LogNormalDistribution logNormal = new LogNormalDistribution(Parameters.getScale(), Parameters.getShape());

        int[] delayTimes = new int[numTestScenarios];
        for (int i = 0; i < numTestScenarios; ++i)
            delayTimes[i] = (int) Math.round(logNormal.sample());
            // delayTimes[i] = scenarioDelays.get(i);

        Arrays.sort(delayTimes);
        testDelays = new ArrayList<>();

        testDelays.add(delayTimes[0]);
        for(int i = 1; i < numTestScenarios; ++i) {
            int delayTime = delayTimes[i];
            if(delayTime != testDelays.get(testDelays.size() - 1))
                testDelays.add(delayTime);
        }
    }

    public void writeOutput() throws OptException {
        try {
            String fileName = "solution_" + (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss'.txt'").format(new Date()));
            BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));

            if(solutionsCompared) {
                writer.write("number of test instances: " + testDelays.size() + "\n");

                for(int i = 0; i < testDelays.size(); ++i) {
                    String line = "test delay (min): ";
                    line += testDelays.get(i);
                    line += ", deterministic excess delay (min): ";
                    line += deterministicObjs[i];
                    line += ", stochastic excess delay (min): ";
                    line += stochasticObjs[i];
                    line += "\n";
                    writer.write(line);
                }

                double meanDetermDelay = 0;
                double meanStochDelay = 0;
                for(int i = 0; i < testDelays.size(); ++i) {
                    meanDetermDelay += deterministicObjs[i];
                    logger.debug(" meanDetermDelay: " + meanDetermDelay + " i: " + i + " deterministicObjs[i]: " +
                    		deterministicObjs[i]);                    
                    meanStochDelay += stochasticObjs[i];
                }

                meanDetermDelay /= testDelays.size();
                meanStochDelay /= testDelays.size();

                logger.debug(" meanDetermDelay: " + meanDetermDelay + " testDelays.size(): " + testDelays.size());
                
                writer.write("average excess delay without rescheduling (min): " + meanDetermDelay + "\n");
                writer.write("average excess delay with rescheduling (min): " + meanStochDelay + "\n");
                
                Controller.delayResults.add(meanDetermDelay);
                Controller.delayResults.add(meanStochDelay);
            }
            writer.close();
        } catch (IOException ie) {
            logger.error(ie);
            throw new OptException("error writing final output");
        }
    }
}
