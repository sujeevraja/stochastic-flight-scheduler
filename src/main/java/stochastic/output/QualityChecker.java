package stochastic.output;

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

public class QualityChecker {
    /**
     * QualityChecker generates random delays to compare different reschedule solutions including the original
     * schedule (i.e no reschedule). This is done by running the second-stage model as a MIP.
     */
    private DataRegistry dataRegistry;
    private int[] zeroReschedules;
    private Scenario[] testScenarios;
    private String timeStamp;

    public QualityChecker(DataRegistry dataRegistry) {
        this.dataRegistry = dataRegistry;
        zeroReschedules = new int[dataRegistry.getLegs().size()];
        Arrays.fill(zeroReschedules, 0);
        timeStamp = null;
    }

    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    public void generateTestDelays() {
        testScenarios = dataRegistry.getDelayGenerator().generateScenarios(Parameters.getNumTestScenarios());
    }

    public TestKPISet[] collectAverageTestStatsForBatchRun(ArrayList<RescheduleSolution> rescheduleSolutions) {
        // delaySolutionKpis[i] contains average delay KPIs of all test scenarios generated with rescheduleSolutions[i]
        // applied to the base schedule.
        TestKPISet[] delaySolutionKPIs = new TestKPISet[rescheduleSolutions.size()];

        for (int i = 0; i < rescheduleSolutions.size(); ++i) {
            RescheduleSolution rescheduleSolution = rescheduleSolutions.get(i);
            TestKPISet[] kpis = new TestKPISet[testScenarios.length];
            for (int j = 0; j < testScenarios.length; ++j) {
                DelaySolution delaySolution = getDelaySolution(testScenarios[j], j, rescheduleSolution.getName(),
                        rescheduleSolution.getReschedules());

                TestKPISet testKPISet = new TestKPISet();
                testKPISet.setKpi(Enums.TestKPI.delayCost, delaySolution.getDelayCost());
                testKPISet.setKpi(Enums.TestKPI.totalFlightDelay, (double) delaySolution.getSumTotalDelay());
                testKPISet.setKpi(Enums.TestKPI.maximumFlightDelay, (double) delaySolution.getMaxTotalDelay());
                testKPISet.setKpi(Enums.TestKPI.averageFlightDelay, delaySolution.getAvgTotalDelay());
                testKPISet.setKpi(Enums.TestKPI.totalPropagatedDelay, (double) delaySolution.getSumPropagatedDelay());
                testKPISet.setKpi(Enums.TestKPI.maximumPropagatedDelay, (double) delaySolution.getMaxPropagatedDelay());
                testKPISet.setKpi(Enums.TestKPI.averagePropagatedDelay, delaySolution.getAvgPropagatedDelay());
                testKPISet.setKpi(Enums.TestKPI.totalExcessDelay, (double) delaySolution.getSumExcessDelay());
                testKPISet.setKpi(Enums.TestKPI.maximumExcessDelay, (double) delaySolution.getMaxExcessDelay());
                testKPISet.setKpi(Enums.TestKPI.averageExcessDelay, delaySolution.getAvgExcessDelay());
                testKPISet.setKpi(Enums.TestKPI.delaySolutionTimeInSec, delaySolution.getSolutionTimeInSeconds());
                kpis[j] = testKPISet;
            }

            TestKPISet averageKPIs = new TestKPISet();
            averageKPIs.storeAverageKPIs(kpis);
            delaySolutionKPIs[i] = averageKPIs;
        }

        return delaySolutionKPIs;
    }

    public void compareSolutions(ArrayList<RescheduleSolution> rescheduleSolutions) throws IOException {
        DelaySolution[][] delaySolutions = collectAllDelaySolutions(rescheduleSolutions);

        String compareFileName = timeStamp != null ?
                "solution/" + timeStamp + "__comparison.csv"
                : "solution/comparison.csv";
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

    private DelaySolution[][] collectAllDelaySolutions(ArrayList<RescheduleSolution> rescheduleSolutions)
            throws IOException {
        DelaySolution[][] delaySolutions = new DelaySolution[testScenarios.length][rescheduleSolutions.size()];
        for (int i = 0; i < testScenarios.length; ++i) {
            for (int j = 0; j < rescheduleSolutions.size(); ++j) {
                RescheduleSolution rescheduleSolution = rescheduleSolutions.get(j);
                DelaySolution delaySolution = getDelaySolution(testScenarios[i], i, rescheduleSolution.getName(),
                        rescheduleSolution.getReschedules());
                delaySolutions[i][j] = delaySolution;

                if (Parameters.isDebugVerbose()) {
                    if (timeStamp != null) {
                        String name = ("solution/" + timeStamp + "__delay_solution_scenario_" + i + "_"
                                + rescheduleSolution.getName() + ".csv");
                        delaySolution.writeCSV(name, dataRegistry.getLegs());
                    } else {
                        String name = ("solution/delay_solution_scenario_" + i + "_"
                                + rescheduleSolution.getName() + ".csv");
                        delaySolution.writeCSV(name, dataRegistry.getLegs());

                    }
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
        int[] adjustedDelays;
        if (reschedules != null) {
            adjustedDelays = new int[dataRegistry.getLegs().size()];
            int[] primaryDelays = scen.getPrimaryDelays();
            for (int i = 0; i < primaryDelays.length; ++i) {
                adjustedDelays[i] = Math.max(primaryDelays[i] - reschedules[i], 0);
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
