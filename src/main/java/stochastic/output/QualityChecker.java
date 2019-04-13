package stochastic.output;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import stochastic.delay.NewDelayGenerator;
import stochastic.delay.Scenario;
import stochastic.domain.Leg;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.solver.PathCache;
import stochastic.solver.SolverUtility;
import stochastic.solver.SubSolverRunnable;
import stochastic.utility.CSVHelper;
import stochastic.utility.Enums;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

class QualityChecker {
    /**
     * QualityChecker generates random delays to compare different reschedule solutions including the original
     * schedule (i.e no reschedule). This is done by running the second-stage model as a MIP.
     */
    private DataRegistry dataRegistry;
    private int[] zeroReschedules;
    private Scenario[] testScenarios;

    QualityChecker(DataRegistry dataRegistry) {
        this.dataRegistry = dataRegistry;
        zeroReschedules = new int[dataRegistry.getLegs().size()];
        Arrays.fill(zeroReschedules, 0);
    }

    void generateTestDelays() {
        // LogNormalDistribution distribution = new LogNormalDistribution(Parameters.getScale(), Parameters.getShape());
        // DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), distribution);

        // TODO correct distributions later.
        RealDistribution distribution;
        switch (Parameters.getDistributionType()) {
            case EXPONENTIAL:
                distribution = new ExponentialDistribution(Parameters.getDistributionMean());
            case TRUNCATED_NORMAL:
                distribution = new ExponentialDistribution(Parameters.getDistributionMean());
            case LOGNORMAL:
                distribution = new ExponentialDistribution(Parameters.getDistributionMean());
            default:
                distribution = new ExponentialDistribution(Parameters.getDistributionMean());
        }
        NewDelayGenerator dgen = new NewDelayGenerator(distribution, dataRegistry.getLegs());
        testScenarios = dgen.generateScenarios(Parameters.getNumTestScenarios());
    }

    void compareSolutions(ArrayList<RescheduleSolution> rescheduleSolutions, String timeStamp) throws IOException {
        DelaySolution[][] delaySolutions = collectAllDelaySolutions(rescheduleSolutions, timeStamp);

        String compareFileName = "solution/" + timeStamp + "__comparison.csv";
        BufferedWriter csvWriter = new BufferedWriter(new FileWriter(compareFileName));

        ArrayList<String> comparableStatNames = new ArrayList<>(Arrays.asList(
                "delay cost",
                "total cost",
                "total flight delay",
                "maximum flight delay",
                "average flight delay",
                "total propagated delay",
                "maximum propagated delay",
                "average propagated delay",
                "total excess delay",
                "maximum excess delay",
                "average excess delay"
        ));

        ArrayList<String> headerRow = new ArrayList<>(Arrays.asList(
                "name",
                "distribution",
                "mean",
                "variance",
                "strategy",
                "probability",
                "reschedule type",
                "reschedule cost"));

        for (String name : comparableStatNames) {
            headerRow.add(name);
            headerRow.add("decrease (%)");
        }
        headerRow.add("delay solution time (sec)");

        CSVHelper.writeLine(csvWriter, headerRow);

        ArrayList<ArrayList<Double>> averageRows = new ArrayList<>();
        for (int i = 0; i < rescheduleSolutions.size(); ++i)
            averageRows.add(new ArrayList<>(Collections.nCopies(headerRow.size() - 7, 0.0)));

        for (int i = 0; i < testScenarios.length; ++i) {
            double probability = testScenarios[i].getProbability();
            ComparableStats baseStats = null;

            for (int j = 0; j < rescheduleSolutions.size(); ++j) {
                RescheduleSolution rescheduleSolution = rescheduleSolutions.get(j);
                DelaySolution delaySolution = delaySolutions[i][j];

                ComparableStats stats = new ComparableStats(rescheduleSolution, delaySolution);
                if (baseStats == null)
                    baseStats = stats;
                else
                    stats.setPercentageDecreases(baseStats);

                ArrayList<String> row = new ArrayList<>();
                row.add("scenario " + i);
                row.add(Parameters.getDistributionType().toString());
                row.add(Double.toString(Parameters.getDistributionMean()));

                if (Parameters.getDistributionType() == Enums.DistributionType.EXPONENTIAL) {
                    double variance = Parameters.getDistributionMean() * Parameters.getDistributionMean();
                    row.add(Double.toString(variance));
                } else {
                    row.add(Double.toString(Parameters.getDistributionSd()));
                }

                row.add(Parameters.getFlightPickStrategy().toString());

                row.add(Double.toString(probability));
                row.add(rescheduleSolution.getName());
                row.add(Double.toString(rescheduleSolution.getRescheduleCost()));

                ArrayList<Double> averageRow = averageRows.get(j);
                int avgRowIndex = 0;
                averageRow.set(avgRowIndex,
                        averageRow.get(avgRowIndex) + probability * rescheduleSolution.getRescheduleCost());
                ++avgRowIndex;

                double[] values = stats.getValues();
                double[] decreases = stats.getPercentageDecreases();

                for (int k = 0; k < values.length; ++k) {
                    row.add(Double.toString(values[k]));
                    row.add(Double.toString(decreases[k]));

                    averageRow.set(avgRowIndex, averageRow.get(avgRowIndex) + (probability * values[k]));
                    ++avgRowIndex;
                    averageRow.set(avgRowIndex, averageRow.get(avgRowIndex) + (probability * decreases[k]));
                    ++avgRowIndex;
                }

                row.add(Double.toString(delaySolution.getSolutionTimeInSeconds()));
                averageRow.set(avgRowIndex,
                        averageRow.get(avgRowIndex) + (probability * delaySolution.getSolutionTimeInSeconds()));

                CSVHelper.writeLine(csvWriter, row);
            }
        }

        for (int i = 0; i < averageRows.size(); ++i) {
            ArrayList<Double> avgRow = averageRows.get(i);
            ArrayList<String> row = new ArrayList<>();
            row.add("average");
            for (int j = 0; j < 5; ++j)
                row.add("-");

            row.add( rescheduleSolutions.get(i).getName());
            row.addAll(avgRow.stream().map(val -> Double.toString(val)).collect(Collectors.toList()));
            CSVHelper.writeLine(csvWriter, row);
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
        PathCache pathCache = new PathCache();
        pathCache.setCachedPaths(SolverUtility.getOriginalPaths(dataRegistry.getIdTailMap(),
                dataRegistry.getTailOrigPathMap(), adjustedDelays));

        SubSolverRunnable ssr = new SubSolverRunnable(dataRegistry, 0, scenarioNum, scen.getProbability(),
                zeroReschedules, adjustedDelays, pathCache);
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
}
