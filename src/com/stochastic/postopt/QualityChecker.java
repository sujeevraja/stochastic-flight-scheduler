package com.stochastic.postopt;

import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
import com.stochastic.delay.Scenario;
import com.stochastic.domain.Leg;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.solver.SubSolverRunnable;
import com.stochastic.utility.CSVHelper;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
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

    void compareSolutions(ArrayList<RescheduleSolution> rescheduleSolutions, String timeStamp) throws IOException {
        DelaySolution[][] delaySolutions = collectAllDelaySolutions(rescheduleSolutions, timeStamp);

        String compareFileName = "solution/" + timeStamp + "__comparison.csv";
        BufferedWriter csvWriter = new BufferedWriter(new FileWriter(compareFileName));

        ArrayList<String> headers = new ArrayList<>(Arrays.asList("scenario number", "reschedule type",
                "reschedule cost", "delay cost", "total cost", "total flight delay", "total propagated delay",
                "total excess delay", "delay solution time (sec)"));
        CSVHelper.writeLine(csvWriter, headers);

        for (int i = 0; i < testScenarios.length; ++i) {
            for (int j = 0; j < rescheduleSolutions.size(); ++j) {
                ArrayList<String> row = new ArrayList<>();
                row.add(Integer.toString(i));

                RescheduleSolution rescheduleSolution = rescheduleSolutions.get(j);
                row.add(rescheduleSolution.getName());
                row.add(Double.toString(rescheduleSolution.getRescheduleCost()));

                DelaySolution delaySolution = delaySolutions[i][j];
                row.add(Double.toString(delaySolution.getDelayCost()));

                row.add(Double.toString(rescheduleSolution.getRescheduleCost() + delaySolution.getDelayCost()));
                row.add(Double.toString(delaySolution.getTotalDelaySum()));
                row.add(Double.toString(delaySolution.getPropagatedDelaySum()));
                row.add(Double.toString(delaySolution.getExcessDelaySum()));
                row.add(Double.toString(delaySolution.getSolutionTimeInSeconds()));

                CSVHelper.writeLine(csvWriter, row);
            }
        }

        csvWriter.close();
    }

    private DelaySolution[][] collectAllDelaySolutions(ArrayList<RescheduleSolution> rescheduleSolutions,
                                          String timeStamp) throws IOException {
        DelaySolution[][] delaySolutions = new DelaySolution[testScenarios.length][rescheduleSolutions.size()];
        for (int i = 0; i < testScenarios.length; ++i) {
            for (int j = 0; j < rescheduleSolutions.size(); ++j) {
                RescheduleSolution rescheduleSolution = rescheduleSolutions.get(j);
                DelaySolution delaySolution = getDelaySolution(testScenarios[i], i, rescheduleSolution.getName(),
                        rescheduleSolution.getReschedules());
                delaySolutions[i][j] = delaySolution;

                if (Parameters.isDebugVerbose()) {
                    String name = ("solution/" + timeStamp + "__delay_solution_scenario_" + i + "_"
                            + rescheduleSolution.getName() + ".csv");
                    delaySolution.writeCSV(name, dataRegistry.getLegs());
                }
            }
        }
        return delaySolutions;
    }

    private DelaySolution getDelaySolution(Scenario scen, int scenarioNum, String slnName, int[] reschedules) {
        reset();

        // update leg departure time according to reschedule values.
        if (reschedules != null) {
            for (Leg leg : dataRegistry.getLegs())
                leg.reschedule(reschedules[leg.getIndex()]);
        }

        // update primary delays using reschedules.
        HashMap<Integer, Integer> adjustedDelays;
        if (reschedules != null) {
            adjustedDelays = new HashMap<>();
            HashMap<Integer, Integer> primaryDelays = scen.getPrimaryDelays();
            for (Map.Entry<Integer, Integer> entry : primaryDelays.entrySet()) {
                Integer adjustedDelay = Math.max(entry.getValue() - reschedules[entry.getKey()], 0);
                adjustedDelays.put(entry.getKey(), adjustedDelay);
            }
        } else {
            adjustedDelays = scen.getPrimaryDelays();
        }

        // solve routing MIP and collect solution
        SubSolverRunnable ssr = new SubSolverRunnable(dataRegistry, 0, scenarioNum, scen.getProbability(),
                zeroReschedules, adjustedDelays);
        ssr.setFilePrefix(slnName);
        ssr.setSolveForQuality(true);

        Instant start = Instant.now();
        ssr.run();
        double slnTime = Duration.between(start, Instant.now()).toMillis() / 1000.0;

        DelaySolution delaySolution = ssr.getDelaySolution();
        delaySolution.setSolutionTimeInSeconds(slnTime);

        // undo reschedules
        for (Leg leg : dataRegistry.getLegs())
            leg.revertReschedule();

        return delaySolution;
    }

    void testSolution(String slnName, int[] reschedules) {
        reset();

        // update leg departure time according to reschedule values.
        if (reschedules != null) {
            for (Leg leg : dataRegistry.getLegs())
                leg.reschedule(reschedules[leg.getIndex()]);
        }

        for (int i = 0; i < testScenarios.length; ++i) {
            Scenario scen = testScenarios[i];

            // update primary delays using reschedules.
            HashMap<Integer, Integer> adjustedDelays;
            if (reschedules != null) {
                adjustedDelays = new HashMap<>();
                HashMap<Integer, Integer> primaryDelays = scen.getPrimaryDelays();
                for (Map.Entry<Integer, Integer> entry : primaryDelays.entrySet()) {
                    Integer adjustedDelay = Math.max(entry.getValue() - reschedules[entry.getKey()], 0);
                    adjustedDelays.put(entry.getKey(), adjustedDelay);
                }
            } else {
                adjustedDelays = scen.getPrimaryDelays();
            }

            // solve routing MIP and collect solution
            SubSolverRunnable ssr = new SubSolverRunnable(dataRegistry, 0, i, scen.getProbability(),
                    zeroReschedules, adjustedDelays);
            ssr.setFilePrefix(slnName);
            ssr.setSolveForQuality(true);

            Instant start = Instant.now();
            ssr.run();
            double slntime = Duration.between(start, Instant.now()).toMillis() / 1000.0;
            solutionTimesInSeconds[i] = slntime;

            DelaySolution delaySolution = ssr.getDelaySolution();
            double obj = delaySolution.getDelayCost();
            objectives[i] = obj;
            expectedObjective += (scen.getProbability() * obj);
            logger.info("tested " + slnName + " schedule with scenario " + (i+1) + " of " + testScenarios.length);
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
