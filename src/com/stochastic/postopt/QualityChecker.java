package com.stochastic.postopt;

import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
import com.stochastic.delay.Scenario;
import com.stochastic.domain.Leg;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.solver.SubSolverRunnable;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class QualityChecker {
    /**
     * QualityChecker generates random delays to compare different reschedule solutions including the original
     * schedule (i.e no reschedule). This is done by running the second-stage model as a MIP.
     */
    private final static Logger logger = LogManager.getLogger(QualityChecker.class);
    private DataRegistry dataRegistry;
    private int[] zeroReschedules;
    private Scenario[] testScenarios;

    private double[] objectives;
    private double[] solutionTimesInSeconds;
    private double expectedObjective;
    private double averageSolutionTime;

    QualityChecker(DataRegistry dataRegistry) {
        this.dataRegistry = dataRegistry;
        zeroReschedules = new int[dataRegistry.getLegs().size()];
        Arrays.fill(zeroReschedules, 0);
    }

    public double[] getObjectives() {
        return objectives;
    }

    public double[] getSolutionTimesInSeconds() {
        return solutionTimesInSeconds;
    }

    public double getExpectedObjective() {
        return expectedObjective;
    }

    public double getAverageSolutionTime() {
        return averageSolutionTime;
    }

    ArrayList<String> getComparisonRow(String name) {
        ArrayList<String> row = new ArrayList<>();
        row.add(name);

        for (int i = 0; i < objectives.length; ++i) {
            row.add(Double.toString(objectives[i]));
            row.add(Double.toString(solutionTimesInSeconds[i]));
        }

        row.add(Double.toString(expectedObjective));
        row.add(Double.toString(averageSolutionTime));
        return row;
    }

    void generateTestDelays() {
        LogNormalDistribution distribution = new LogNormalDistribution(Parameters.getScale(), Parameters.getShape());
        DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), distribution);
        testScenarios = dgen.generateScenarios(Parameters.getNumTestScenarios());
    }

    void testOriginalSchedule() {
        reset();

        for (int i = 0; i < testScenarios.length; ++i) {
            Scenario scen = testScenarios[i];
            SubSolverRunnable ssr = new SubSolverRunnable(dataRegistry, 0, i, scen.getProbability(),
                    zeroReschedules, scen.getPrimaryDelays());
            ssr.setSolveForQuality(true);

            Instant start = Instant.now();
            ssr.run();
            double slntime = Duration.between(start, Instant.now()).toMillis() / 1000.0;
            solutionTimesInSeconds[i] = slntime;

            double obj = ssr.getObjvalue();
            objectives[i] = obj;
            expectedObjective += (scen.getProbability() * obj);
            logger.info("tested original schedule with scenario " + (i+1) + " of " + testScenarios.length);
        }

        averageSolutionTime = Arrays.stream(solutionTimesInSeconds).average().orElse(Double.NaN);
    }

    void testSolution(RescheduleSolution sln) {
        reset();
        int[] reschedules = sln.getReschedules();

        // update leg departure time according to reschedule values.
        for (Leg leg : dataRegistry.getLegs())
            leg.reschedule(reschedules[leg.getIndex()]);

        for (int i = 0; i < testScenarios.length; ++i) {
            Scenario scen = testScenarios[i];

            // update primary delays using reschedules.
            HashMap<Integer, Integer> adjustedDelays = new HashMap<>();
            HashMap<Integer, Integer> primaryDelays = scen.getPrimaryDelays();
            for (Map.Entry<Integer, Integer> entry : primaryDelays.entrySet()) {
                Integer adjustedDelay = Math.max(entry.getValue() - reschedules[entry.getKey()], 0);
                adjustedDelays.put(entry.getKey(), adjustedDelay);
            }

            // solve routing MIP and collect solution
            SubSolverRunnable ssr = new SubSolverRunnable(dataRegistry, 0, i, scen.getProbability(),
                    zeroReschedules, adjustedDelays);
            ssr.setFilePrefix(sln.getName());
            ssr.setSolveForQuality(true);

            Instant start = Instant.now();
            ssr.run();
            double slntime = Duration.between(start, Instant.now()).toMillis() / 1000.0;
            solutionTimesInSeconds[i] = slntime;

            double obj = ssr.getObjvalue();
            objectives[i] = obj;
            expectedObjective += (scen.getProbability() * obj);
            logger.info("tested original schedule with scenario " + (i+1) + " of " + testScenarios.length);
        }
        averageSolutionTime = Arrays.stream(solutionTimesInSeconds).average().orElse(Double.NaN);

        // undo reschedules
        for (Leg leg : dataRegistry.getLegs())
            leg.revertReschedule();
    }

    private void reset() {
        objectives = new double[testScenarios.length];
        solutionTimesInSeconds = new double[testScenarios.length];
        expectedObjective = 0.0;
        averageSolutionTime = 0.0;
    }
}
