package com.stochastic.postopt;

import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
import com.stochastic.delay.Scenario;
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
    private boolean solutionsCompared;

    private ArrayList<Integer> rescheduleLegIndices;
    private ArrayList<Integer> rescheduleTimes;
    private int[] deterministicObjs;
    private int[] stochasticObjs;

    public SolutionManager(DataRegistry dataRegistry, double[][] xValues) {
        String instancePath = Parameters.getInstancePath();
        this.dataRegistry = dataRegistry;
        Scenario[] delayScenarios = dataRegistry.getDelayScenarios();
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
        LogNormalDistribution logNormal = new LogNormalDistribution(Parameters.getScale(), Parameters.getShape());
        DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), logNormal);
        Scenario[] testScenarios = dgen.generateScenarios(numTestScenarios);

        deterministicObjs = new int[testScenarios.length];
        stochasticObjs = new int[testScenarios.length];
        ArrayList<Leg> legs = dataRegistry.getLegs();

        int[] zeroes = new int[dataRegistry.getLegs().size()];
        Arrays.fill(zeroes, 0);

        for(int i = 0; i < testScenarios.length; ++i) {
            Scenario scenario = testScenarios[i];
            logger.info("Scenario: " + i);
            logger.info("Probability: " + scenario.getProbability());

            // solve model without rescheduling legs
            HashMap<Integer, Integer> legDelayMap = scenario.getPrimaryDelays();
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

    public void writeOutput() throws IOException {
        String fileName = "solution_" + (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss'.txt'").format(new Date()));
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));

        if(solutionsCompared) {
            int numTestScenarios = deterministicObjs.length;
            writer.write("number of test instances: " + numTestScenarios + "\n");

            for(int i = 0; i < numTestScenarios; ++i) {
                String line = "Scenario: " + i;
                line += ", deterministic excess delay (min): ";
                line += deterministicObjs[i];
                line += ", stochastic excess delay (min): ";
                line += stochasticObjs[i];
                line += "\n";
                writer.write(line);
            }

            double meanDetermDelay = 0;
            double meanStochDelay = 0;
            for(int i = 0; i < numTestScenarios; ++i) {
                meanDetermDelay += deterministicObjs[i];
                logger.debug(" meanDetermDelay: " + meanDetermDelay + " i: " + i + " deterministicObjs[i]: " +
                        deterministicObjs[i]);
                meanStochDelay += stochasticObjs[i];
            }

            meanDetermDelay /= numTestScenarios;
            meanStochDelay /= numTestScenarios;

            logger.debug(" meanDetermDelay: " + meanDetermDelay + " testScenarios.length: " + numTestScenarios);

            writer.write("average excess delay without rescheduling (min): " + meanDetermDelay + "\n");
            writer.write("average excess delay with rescheduling (min): " + meanStochDelay + "\n");
        }
        writer.close();
    }
}
