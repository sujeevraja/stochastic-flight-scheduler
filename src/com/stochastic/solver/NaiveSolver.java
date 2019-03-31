package com.stochastic.solver;

import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
import com.stochastic.domain.Leg;
import com.stochastic.network.Path;
import com.stochastic.postopt.Solution;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class NaiveSolver {
    /**
     * This class builds a naive rescheduling solution (i.e. solution to Benders first stage).
     *
     * For each leg with a primary delay, it reschedules the leg by the biggest amount less than or equal to the delay.
     * These delays are then propagated downstream in the original routes.
     */
    private final static Logger logger = LogManager.getLogger(NaiveSolver.class);
    private DataRegistry dataRegistry;
    private double[] expectedDelays;
    private Solution finalSolution;

    public NaiveSolver(DataRegistry dataRegistry) {
        this.dataRegistry = dataRegistry;

        final int numLegs = dataRegistry.getLegs().size();
        expectedDelays = new double[numLegs];
        Arrays.fill(expectedDelays, 0.0);
    }

    public Solution getFinalSolution() {
        return finalSolution;
    }

    public void solve() {
        buildAveragePrimaryDelays();

        ArrayList<Leg> legs = dataRegistry.getLegs();
        final int numLegs = legs.size();
        int[] reschedules = new int[numLegs];
        Arrays.fill(reschedules, 0);
        selectPrimaryReschedules(reschedules);
        propagateReschedules(reschedules);

        double objective = 0.0;
        for (int i = 0; i < numLegs; ++i) {
            if (reschedules[i] > 0)
                objective += (reschedules[i] * legs.get(i).getRescheduleCostPerMin());
        }

        logger.info("naive solver objective: " + objective);
        finalSolution = new Solution(objective, reschedules);
    }

    private void buildAveragePrimaryDelays() {
        final int numScenarios = dataRegistry.getNumScenarios();
        int[] scenarioDelays = dataRegistry.getScenarioDelays();
        double[] probabilities = dataRegistry.getScenarioProbabilities();

        for(int i = 0; i < numScenarios; ++i) {
            DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), scenarioDelays[i]);
            HashMap<Integer, Integer> legDelays = dgen.generateDelays();

            for (Map.Entry<Integer, Integer> entry : legDelays.entrySet())
                expectedDelays[entry.getKey()] += probabilities[i] * entry.getValue();
        }
    }

    private void selectPrimaryReschedules(int[] reschedules) {
        int[] durations = Parameters.getDurations();
        for (int i = 0; i < expectedDelays.length; ++i) {
            double delay = expectedDelays[i];
            if (delay <= Constants.EPS)
                continue;

            // set primary reschedule as the maximum value less than or equal to the expected delay.
            boolean rescheduleSet = false;
            for (int j = 0; j < durations.length; ++j) {
                if (durations[j] <= delay - Constants.EPS)
                    continue;

                if (j > 0) {
                    reschedules[i] = durations[j-1];
                    rescheduleSet = true;
                    break;
                }
            }

            if (!rescheduleSet)
                reschedules[i] = durations[durations.length - 1];
        }
    }

    private void propagateReschedules(int[] reschedules) {
        HashMap<Integer, Path> tailPathMap = dataRegistry.getTailOrigPathMap();
        Integer[][] slacks = dataRegistry.getOrigSlacks();

        for(Map.Entry<Integer, Path> entry : tailPathMap.entrySet()) {
            ArrayList<Leg> pathLegs = entry.getValue().getLegs();
            if (pathLegs.size() <= 1)
                continue;

            int prevLegIndex = pathLegs.get(0).getIndex();
            int totalDelay = reschedules[prevLegIndex];

            for (int i = 1; i < pathLegs.size(); ++i) {
                int index = pathLegs.get(i).getIndex();
                totalDelay = Math.max(totalDelay - slacks[prevLegIndex][index], reschedules[index]);
                reschedules[index] = totalDelay;
                prevLegIndex = index;
            }
        }
    }

    private void calculateObjective() {
    }
}
