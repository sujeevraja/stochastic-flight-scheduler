package com.stochastic.postopt;

import com.stochastic.controller.Controller;
import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
import com.stochastic.domain.Leg;
import com.stochastic.registry.DataRegistry;
import com.stochastic.solver.SubSolver;
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
    private final static double eps = 1.0e-5;
    private String instancePath;
    private DataRegistry dataRegistry;
    private ArrayList<Integer> scenarioDelays;
    private ArrayList<Double> scenarioProbabilities;
    double[][] xValues; // xValues[i][j] = 1 if durations[i] is selected for legs[j], 0 otherwise.
    private boolean solutionsCompared;

    private ArrayList<Integer> rescheduleLegIndices;
    private ArrayList<Integer> rescheduleTimes;
    private static ArrayList<Integer> testDelays;
    private int[] deterministicObjs;
    private int[] stochasticObjs;

    public SolutionManager(String instancePath, DataRegistry dataRegistry, ArrayList<Integer> scenarioDelays,
                           ArrayList<Double> scenarioProbabilities, double[][] xValues) {
        this.instancePath = instancePath;
        this.dataRegistry = dataRegistry;
        this.scenarioDelays = scenarioDelays;
        this.scenarioProbabilities = scenarioProbabilities;
        this.xValues = xValues;
        solutionsCompared = false;

        rescheduleLegIndices = new ArrayList<>();
        rescheduleTimes = new ArrayList<>();

        ArrayList<Integer> durations = dataRegistry.getDurations();
        ArrayList<Leg> legs = dataRegistry.getLegs();
        for(int i = 0; i < durations.size(); ++i) {
            for(int j = 0; j < legs.size(); ++j) {
                if(xValues[i][j] >= eps) {
                    rescheduleLegIndices.add(j);
                    rescheduleTimes.add(durations.get(i));
                }
            }
        }
    }

    public final void compareSolutions(int numTestScenarios) throws OptException {
//        generateDelaysForComparison(numTestScenarios);

        deterministicObjs = new int[testDelays.size()];
        stochasticObjs = new int[testDelays.size()];
        ArrayList<Leg> legs = dataRegistry.getLegs();

        double[][] zeroValues = new double[dataRegistry.getDurations().size()][legs.size()];
        for(int i = 0; i < dataRegistry.getDurations().size(); ++i)
            for (int j = 0; j < legs.size(); ++j)
                zeroValues[i][j] = 0.0;

        dataRegistry.setMaxLegDelayInMin(Math.max(dataRegistry.getMaxLegDelayInMin(), 100));
        
        for(int i = 0; i < testDelays.size(); ++i) {
            Integer currDelay = testDelays.get(i);
            logger.info("Current test delay: " + currDelay);
//            dataRegistry.setMaxLegDelayInMin(Math.max(dataRegistry.getMaxLegDelayInMin(), currDelay));
            DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), currDelay);

            // solve model without rescheduling legs
            HashMap<Integer, Integer> legDelayMap = dgen.generateDelays();
            SubSolver determSolver = new SubSolver(legDelayMap, 1.0);
            determSolver.constructSecondStage(zeroValues, dataRegistry, i, 0, null);
            determSolver.solve();
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
            SubSolver stocSolver = new SubSolver(legDelayMap, 1.0);
            stocSolver.constructSecondStage(zeroValues, dataRegistry, i, 0, null);
            stocSolver.solve();
            stochasticObjs[i] = (int) Math.round(stocSolver.getObjValue());

            // reset legs back to original times
            for(int j = 0; j < rescheduleLegIndices.size(); ++j)
                legs.get(rescheduleLegIndices.get(j)).reschedule(-rescheduleTimes.get(j));
        }
        solutionsCompared = true;
    }

    public static void generateDelaysForComparison(int numTestScenarios, DataRegistry dataRegistry) {
        // generate random delay data to compare solutions.
        LogNormalDistribution logNormal = new LogNormalDistribution(dataRegistry.getScale(), dataRegistry.getShape());

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

            // print input data
            writer.write("instance name: " + instancePath + "\n");
            writer.write("number of tails: " + dataRegistry.getTails().size() + "\n");
            writer.write("number of legs: " + dataRegistry.getLegs().size() + "\n");
            writer.write("number of scenarios: " + scenarioDelays.size() + "\n");
            StringBuilder delayStr = new StringBuilder();
            StringBuilder probStr = new StringBuilder();
            delayStr.append("scenario delays: ");
            delayStr.append(scenarioDelays.get(0));
            probStr.append("scenario probabilities: ");
            probStr.append(scenarioProbabilities.get(0));

            for(int i = 1; i < scenarioDelays.size(); ++i) {
                delayStr.append(",");
                delayStr.append(scenarioDelays.get(i));
                probStr.append(",");
                probStr.append(scenarioProbabilities.get(i));
            }

            writer.write(delayStr.toString());
            writer.write('\n');
            writer.write(probStr.toString());
            writer.write("\n\n");

            // print selected reschedule times
            writer.write("leg reschedule times:\n");
            ArrayList<Leg> legs = dataRegistry.getLegs();
            for(int i = 0; i < rescheduleLegIndices.size(); ++i) {
                String line = legs.get(i).getId() + ": " + rescheduleTimes.get(i) + " minutes\n";
                writer.write(line);
            }
            writer.write("\n");

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
