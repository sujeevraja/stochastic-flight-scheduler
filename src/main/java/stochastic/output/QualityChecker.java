package stochastic.output;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import stochastic.delay.Scenario;
import stochastic.domain.Leg;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.solver.PathCache;
import stochastic.solver.SolverUtility;
import stochastic.solver.SubSolverRunnable;
import stochastic.utility.CSVHelper;
import stochastic.utility.Enums;
import stochastic.utility.OptException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * QualityChecker generates random delays to compare different reschedule solutions including the
 * original schedule (i.e no reschedule). This is done by running the second-stage model as a MIP.
 */
public class QualityChecker {
    private DataRegistry dataRegistry;
    private IloCplex cplex;
    private int[] zeroReschedules;
    private Scenario[] testScenarios;

    public QualityChecker(DataRegistry dataRegistry) {
        this.dataRegistry = dataRegistry;
        zeroReschedules = new int[dataRegistry.getLegs().size()];
        Arrays.fill(zeroReschedules, 0);
    }

    private void initCplex() throws OptException {
        try {
            this.cplex = new IloCplex();
            if (!Parameters.isDebugVerbose())
                cplex.setOut(null);
        } catch (IloException ex) {
            throw new OptException("error initializing CPLEX for quality checks");
        }
    }

    private void endCplex() {
        cplex.end();
        cplex = null;
    }

    public void generateTestDelays() {
        testScenarios = dataRegistry.getDelayGenerator().generateScenarios(
            Parameters.getNumTestScenarios());
    }

    public TestKPISet[] collectAverageTestStatsForBatchRun(
            ArrayList<RescheduleSolution> rescheduleSolutions) throws OptException {
        // delaySolutionKpis[i] contains average delay KPIs of all test scenarios generated with
        // rescheduleSolutions[i] applied to the base schedule.
        TestKPISet[] delaySolutionKPIs = new TestKPISet[rescheduleSolutions.size()];

        initCplex();
        for (int i = 0; i < rescheduleSolutions.size(); ++i) {
            RescheduleSolution rescheduleSolution = rescheduleSolutions.get(i);
            TestKPISet[] kpis = new TestKPISet[testScenarios.length];
            for (int j = 0; j < testScenarios.length; ++j) {
                DelaySolution delaySolution = getDelaySolution(testScenarios[j], j,
                    rescheduleSolution.getName(), rescheduleSolution.getReschedules());

                kpis[j] = delaySolution.getTestKPISet();
            }

            TestKPISet averageKPIs = new TestKPISet();
            averageKPIs.storeAverageKPIs(kpis);
            delaySolutionKPIs[i] = averageKPIs;
        }
        endCplex();

        return delaySolutionKPIs;
    }

    public void compareSolutions(ArrayList<RescheduleSolution> rescheduleSolutions)
            throws IOException, OptException {
        String compareFileName = "solution/comparison.csv";
        BufferedWriter csvWriter = new BufferedWriter(new FileWriter(compareFileName));

        ArrayList<String> headerRow = new ArrayList<>(Arrays.asList(
                "name",
                "distribution",
                "mean",
                "variance",
                "strategy",
                "probability",
                "reschedule type",
                "reschedule cost"));

        for (Enums.TestKPI testKPI : Enums.TestKPI.values()) {
            headerRow.add(testKPI.name());
            if (testKPI != Enums.TestKPI.delaySolutionTimeInSec)
                headerRow.add("decrease (%)");
        }

        CSVHelper.writeLine(csvWriter, headerRow);

        ArrayList<Map<Enums.TestKPI, Double>> averageKpis = new ArrayList<>();
        ArrayList<Map<Enums.TestKPI, Double>> averageDecreases = new ArrayList<>();

        { // New block here is to de-scope kpiMap.
            Map<Enums.TestKPI, Double> kpiMap = new HashMap<>();
            for (Enums.TestKPI kpi : Enums.TestKPI.values())
                kpiMap.put(kpi, 0.0);

            for (int i = 0; i < rescheduleSolutions.size(); ++i) {
                Map<Enums.TestKPI, Double> mapCopy = kpiMap.entrySet().stream().collect(
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                averageKpis.add(mapCopy);

                mapCopy = kpiMap.entrySet().stream().collect(
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                averageDecreases.add(mapCopy);
            }
        }

        // collect solutions and write results for each scenario and setting.
        initCplex();
        for (int j = 0; j < testScenarios.length; ++j) {
            final double probability = testScenarios[j].getProbability();
            TestKPISet baseSet = null;

            for (int i = 0; i < rescheduleSolutions.size(); ++i) {
                ArrayList<String> row = new ArrayList<>();
                row.add("scenario " + j);
                row.add(Parameters.getDistributionType().name());
                row.add(Double.toString(Parameters.getDistributionMean()));

                if (Parameters.getDistributionType() == Enums.DistributionType.EXPONENTIAL) {
                    final double variance = Parameters.getDistributionMean() *
                        Parameters.getDistributionMean();
                    row.add(Double.toString(variance));
                } else
                    row.add(Double.toString(Parameters.getDistributionSd()));

                row.add(Parameters.getFlightPickStrategy().name());
                row.add(Double.toString(probability));

                RescheduleSolution rescheduleSolution = rescheduleSolutions.get(i);
                row.add(rescheduleSolution.getName());
                row.add(Double.toString(rescheduleSolution.getRescheduleCost()));

                DelaySolution delaySolution = getDelaySolution(testScenarios[j], j,
                    rescheduleSolution.getName(), rescheduleSolution.getReschedules());

                TestKPISet testKPISet = delaySolution.getTestKPISet();
                TestKPISet percentDecreaseSet = baseSet != null
                        ? TestKPISet.getPercentageDecrease(baseSet, testKPISet)
                        : null;

                Map<Enums.TestKPI, Double> averageKPISet = averageKpis.get(i);
                Map<Enums.TestKPI, Double> averageDecreasesSet = averageDecreases.get(i);

                for (Enums.TestKPI kpi : Enums.TestKPI.values()) {
                    row.add(testKPISet.getKpi(kpi).toString());
                    averageKPISet.put(kpi, averageKPISet.get(kpi) + testKPISet.getKpi(kpi));

                    if (kpi != Enums.TestKPI.delaySolutionTimeInSec) {
                        double dec = percentDecreaseSet != null
                                ? percentDecreaseSet.getKpi(kpi)
                                : 0;
                        row.add(Double.toString(dec));
                        averageDecreasesSet.put(kpi, averageDecreasesSet.get(kpi) + dec);
                    }
                }
                if (baseSet == null)
                    baseSet = testKPISet;

                CSVHelper.writeLine(csvWriter, row);
            }
        }
        endCplex();

        // write average results across all scenarios.
        final double numTestScenarios = testScenarios.length;
        for (int i = 0; i < rescheduleSolutions.size(); ++i) {
            Map<Enums.TestKPI, Double> averageSet = averageKpis.get(i);
            for (Map.Entry<Enums.TestKPI, Double> entry : averageSet.entrySet())
                averageSet.put(entry.getKey(), entry.getValue() / numTestScenarios);

            Map<Enums.TestKPI, Double> decreaseSet = averageDecreases.get(i);
            for (Map.Entry<Enums.TestKPI, Double> entry : decreaseSet.entrySet())
                decreaseSet.put(entry.getKey(), entry.getValue() / numTestScenarios);
        }

        for (int i = 0; i < rescheduleSolutions.size(); ++i) {
            ArrayList<String> row = new ArrayList<>();
            row.add("average");
            row.add(Parameters.getDistributionType().name());
            row.add(Double.toString(Parameters.getDistributionMean()));

            if (Parameters.getDistributionType() == Enums.DistributionType.EXPONENTIAL) {
                final double variance = Parameters.getDistributionMean() *
                    Parameters.getDistributionMean();
                row.add(Double.toString(variance));
            } else
                row.add(Double.toString(Parameters.getDistributionSd()));

            row.add(Parameters.getFlightPickStrategy().name());
            row.add("-");

            RescheduleSolution rescheduleSolution = rescheduleSolutions.get(i);
            row.add(rescheduleSolution.getName());
            row.add(Double.toString(rescheduleSolution.getRescheduleCost()));

            Map<Enums.TestKPI, Double> averageSet = averageKpis.get(i);
            Map<Enums.TestKPI, Double> averageDecreaseSet = averageDecreases.get(i);

            for (Enums.TestKPI kpi : Enums.TestKPI.values()) {
                row.add(averageSet.get(kpi).toString());
                if (kpi != Enums.TestKPI.delaySolutionTimeInSec)
                    row.add(averageDecreaseSet.get(kpi).toString());
            }
            CSVHelper.writeLine(csvWriter, row);
        }
        csvWriter.close();
    }

    private DelaySolution getDelaySolution(Scenario scen, int scenarioNum, String slnName,
                                           int[] reschedules) {
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

        SubSolverRunnable ssr = new SubSolverRunnable(dataRegistry, 0, scenarioNum,
            scen.getProbability(), zeroReschedules, adjustedDelays, pathCache);
        ssr.setCplex(cplex);
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
