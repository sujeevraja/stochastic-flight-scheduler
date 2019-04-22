package stochastic.main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import stochastic.output.QualityChecker;
import stochastic.output.RescheduleSolution;
import stochastic.output.TestKPISet;
import stochastic.registry.Parameters;
import stochastic.utility.CSVHelper;
import stochastic.utility.Enums;
import stochastic.utility.OptException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

/**
 * Used to generate results for the paper.
 */
class BatchRunner {
    private final static Logger logger = LogManager.getLogger(BatchRunner.class);
    private ArrayList<String> instanceNames;
    private ArrayList<String> instancePaths;

    BatchRunner() {
        instanceNames = new ArrayList<>();
        instancePaths = new ArrayList<>();

        /*
        for (int i = 1; i <= 5; ++i) {
            String name = "s" + i;
            instanceNames.add(name);
            instancePaths.add("data/paper/" + name);
        }
         */

        for (int i = 1; i <= 2; ++i) {
            String name = "instance" + i;
            instanceNames.add(name);
            instancePaths.add("data/" + name);
        }
    }

    void runQualitySet() throws OptException {
        try {
            setDefaultParameters();
            writeDefaultParameters();

            ArrayList<String> trainingHeaders = new ArrayList<>(Arrays.asList(
                    "instance",
                    "distribution",
                    "strategy",
                    "Naive model reschedule cost",
                    "Naive model solution time (seconds)",
                    "DEP reschedule cost",
                    "DEP solution time (seconds)",
                    "Benders reschedule cost",
                    "Benders solution time (seconds)",
                    "Benders lower bound",
                    "Benders upper bound",
                    "Benders gap",
                    "Benders number of cuts",
                    "Benders number of iterations"));

            BufferedWriter trainingWriter = new BufferedWriter(
                    new FileWriter("solution/results_training.csv"));

            CSVHelper.writeLine(trainingWriter, trainingHeaders);

            BufferedWriter testWriter = new BufferedWriter(
                    new FileWriter("solution/results_test.csv"));

            ArrayList<String> testHeaders = new ArrayList<>(Arrays.asList(
                    "distribution",
                    "strategy",
                    "approach"));

            for (Enums.TestKPI kpi : Enums.TestKPI.values()) {
                testHeaders.add(kpi.name());
                testHeaders.add("decrease (%)");
            }

            CSVHelper.writeLine(testWriter, testHeaders);

            for (int i = 0; i < instancePaths.size(); ++i) {
                Parameters.setInstancePath(instancePaths.get(i));

                for (Enums.DistributionType distributionType :
                        Enums.DistributionType.values()) {
                    Parameters.setDistributionType(distributionType);

                    for (Enums.FlightPickStrategy flightPickStrategy :
                            Enums.FlightPickStrategy.values()) {
                        Parameters.setFlightPickStrategy(flightPickStrategy);

                        ArrayList<String> row = new ArrayList<>();
                        row.add(instanceNames.get(i));
                        row.add(distributionType.name());
                        row.add(flightPickStrategy.name());

                        // solve models
                        Controller controller = new Controller();
                        controller.readData();
                        controller.buildScenarios();

                        controller.solveWithNaiveApproach();
                        row.add(Double.toString(controller.getNaiveModelRescheduleCost()));
                        row.add(Double.toString(controller.getNaiveModelSolutionTime()));

                        controller.solveWithDEP();
                        row.add(Double.toString(controller.getDepRescheduleCost()));
                        row.add(Double.toString(controller.getDepSolutionTime()));

                        controller.solveWithBenders();

                        // write training results
                        row.add(Double.toString(controller.getBendersRescheduleCost()));
                        row.add(Double.toString(controller.getBendersSolutionTime()));
                        row.add(Double.toString(controller.getBendersLowerBound()));
                        row.add(Double.toString(controller.getBendersUpperBound()));
                        row.add(Double.toString(controller.getBendersGap()));
                        row.add(Integer.toString(controller.getBendersNumCuts()));
                        row.add(Integer.toString(controller.getBendersNumIterations()));

                        CSVHelper.writeLine(trainingWriter, row);

                        // collect test solutions
                        QualityChecker qc = new QualityChecker(controller.getDataRegistry());
                        qc.generateTestDelays();
                        ArrayList<RescheduleSolution> rescheduleSolutions = controller.getAllRescheduleSolutions();
                        TestKPISet[] testKPISets = qc.collectAverageTestStatsForBatchRun(rescheduleSolutions);
                        TestKPISet baseKPISet = testKPISets[0];

                        // write test results
                        for (int j = 0; j < testKPISets.length; ++j) {
                            row = new ArrayList<>();
                            row.add(distributionType.name());
                            row.add(flightPickStrategy.name());
                            row.add(rescheduleSolutions.get(j).getName());

                            TestKPISet testKPISet = testKPISets[j];
                            TestKPISet percentDecreaseSet = j > 0
                                    ? TestKPISet.getPercentageDecrease(baseKPISet, testKPISet)
                                    : null;

                            for (Enums.TestKPI kpi : Enums.TestKPI.values()) {
                                row.add(testKPISet.getKpi(kpi).toString());
                                double decrease = percentDecreaseSet != null
                                        ? percentDecreaseSet.getKpi(kpi)
                                        : 0;
                                row.add(Double.toString(decrease));
                            }
                            CSVHelper.writeLine(testWriter, row);
                        }
                    }
                }
            }

            trainingWriter.close();
            testWriter.close();
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to csv during quality run");
        }
    }

    /**
     * Performs runs to compare solution times of labeling with full enumeration.
     */
    void runTimeComparisonSet() {
        // TODO yet to implement
    }

    /**
     * Performs runs to show the effect of changing budget.
     */
    void runBudgetComparison() {
    }

    /**
     * Sets parameters common to all runs.
     */
    private void setDefaultParameters() {
        Parameters.setRescheduleBudgetFraction(0.5);
        Parameters.setFlightRescheduleBound(30);
        Parameters.setNumScenariosToGenerate(30);

        Parameters.setDistributionMean(15);
        Parameters.setDistributionSd(15); // ignored for exponential distribution.

        Parameters.setSolveDEP(true); // should solve or not?

        Parameters.setBendersMultiCut(true);
        Parameters.setBendersTolerance(1e-3);
        Parameters.setNumBendersIterations(5);
        Parameters.setWarmStartBenders(false);

        // Second-stage parameters
        Parameters.setFullEnumeration(false);
        Parameters.setReducedCostStrategy(Enums.ReducedCostStrategy.FIRST_PATHS);
        Parameters.setNumReducedCostPaths(10);

        // Debugging parameter
        Parameters.setDebugVerbose(false); // Set to true to see CPLEX logs, lp files and solution xml files.

        // Multi-threading parameters
        Parameters.setRunSecondStageInParallel(true);
        Parameters.setNumThreadsForSecondStage(2);

        // Solution quality parameters
        Parameters.setCheckSolutionQuality(true);
        Parameters.setNumTestScenarios(10);

        // Expected excess parameters
        Parameters.setExpectedExcess(false);
        Parameters.setRho(0.9);
        Parameters.setExcessTarget(40);
    }

    private void writeDefaultParameters() throws OptException {
        try {
            TreeMap<String, Object> parameters = new TreeMap<>();
            parameters.put("reschedule time budget fraction", Parameters.getRescheduleBudgetFraction());
            parameters.put("reschedule time limit for flights", Parameters.getFlightRescheduleBound());
            parameters.put("distribution mean", Parameters.getDistributionMean());
            parameters.put("distribution standard deviation", Parameters.getDistributionSd());
            parameters.put("benders multi-cut", Parameters.isBendersMultiCut());
            parameters.put("benders tolerance", Parameters.getBendersTolerance());
            parameters.put("benders iterations", Parameters.getNumBendersIterations());
            parameters.put("benders warm start", Parameters.isWarmStartBenders());
            parameters.put("full enumeration", Parameters.isFullEnumeration());
            parameters.put("reduced cost strategy", Parameters.getReducedCostStrategy().name());
            parameters.put("reduced cost paths", Parameters.getNumReducedCostPaths());
            parameters.put("second stage number of scenarios", Parameters.getNumScenariosToGenerate());
            parameters.put("second stage in parallel", Parameters.isRunSecondStageInParallel());
            parameters.put("second stage number of threads", Parameters.getNumThreadsForSecondStage());
            parameters.put("test number of scenarios", Parameters.getNumTestScenarios());

            String kpiFileName = "solution/quality_parameters.yaml";
            BufferedWriter kpiWriter = new BufferedWriter(new FileWriter(kpiFileName));
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);
            yaml.dump(parameters, kpiWriter);
            kpiWriter.close();
            logger.info("wrote default parameters");
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing default parameters");
        }
    }
}
