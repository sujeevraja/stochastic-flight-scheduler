package stochastic.main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Used to generate results for the paper.
 */
class BatchRunner {
    private final static Logger logger = LogManager.getLogger(BatchRunner.class);
    private ArrayList<String> instanceNames;
    private ArrayList<String> instancePaths;
    private String instanceName;

    BatchRunner(String instanceName) {
        instanceNames = new ArrayList<>();
        instancePaths = new ArrayList<>();
        this.instanceName = instanceName;

        for (int i = 1; i <= 2; ++i) {
            String name = "instance" + i;
            instanceNames.add(name);
            instancePaths.add("data/" + name);
        }
    }

    /**
     * Generates and tests solutions for naive model, DEP and Benders.
     *
     * Runs naive/DEP/Benders to generate reschedule solutions and compares them with the original
     * schedule with 100 randomly generated delay scenarios. Reschedule and test KPIs are written
     * to separate csv files.
     *
     * @throws OptException when there is any issue with solving/writing.
     */
    void runForQuality() throws OptException {
        try {
            final String trainingPath = "solution/results_quality_training.csv";
            final boolean addTrainingHeaders = !fileExists(trainingPath);
            BufferedWriter trainingWriter = new BufferedWriter(
                    new FileWriter(trainingPath, true));
            if (addTrainingHeaders) {
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
                CSVHelper.writeLine(trainingWriter, trainingHeaders);
            }

            final String testPath = "solution/results_quality_test.csv";
            final boolean addTestHeaders = !fileExists(testPath);
            BufferedWriter testWriter = new BufferedWriter(new FileWriter(testPath, true));
            if (addTestHeaders) {
                ArrayList<String> testHeaders = new ArrayList<>(Arrays.asList(
                        "instance",
                        "distribution",
                        "strategy",
                        "approach"));

                for (Enums.TestKPI kpi : Enums.TestKPI.values()) {
                    testHeaders.add(kpi.name());
                    if (kpi != Enums.TestKPI.delaySolutionTimeInSec)
                        testHeaders.add("decrease (%)");
                }

                CSVHelper.writeLine(testWriter, testHeaders);
            }

            ArrayList<String> row = new ArrayList<>();
            row.add(instanceName);
            row.add(Parameters.getDistributionType().name());
            row.add(Parameters.getFlightPickStrategy().name());

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
            ArrayList<RescheduleSolution> rescheduleSolutions =
                    controller.getAllRescheduleSolutions();
            TestKPISet[] testKPISets = qc.collectAverageTestStatsForBatchRun(rescheduleSolutions);
            TestKPISet baseKPISet = testKPISets[0];

            // write test results
            for (int j = 0; j < testKPISets.length; ++j) {
                row = new ArrayList<>(Arrays.asList(
                        instanceName,
                        Parameters.getDistributionType().name(),
                        Parameters.getFlightPickStrategy().name(),
                        rescheduleSolutions.get(j).getName()
                ));

                TestKPISet testKPISet = testKPISets[j];
                TestKPISet percentDecreaseSet = j > 0
                        ? TestKPISet.getPercentageDecrease(baseKPISet, testKPISet)
                        : null;

                for (Enums.TestKPI kpi : Enums.TestKPI.values()) {
                    row.add(testKPISet.getKpi(kpi).toString());
                    if (kpi != Enums.TestKPI.delaySolutionTimeInSec) {
                        double decrease = percentDecreaseSet != null
                                ? percentDecreaseSet.getKpi(kpi)
                                : 0;
                        row.add(Double.toString(decrease));
                    }
                }
                CSVHelper.writeLine(testWriter, row);
            }
            trainingWriter.close();
            testWriter.close();
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to csv during quality run");
        }
    }

    /**
     * Generates reschedule KPIs for Benders with full enumeration and 3 column gen strategies.
     *
     * @throws OptException when there is any issue with solving/writing.
     */
    void runForTimeComparison() throws OptException {
        try {
            final String resultsPath = "solution/results_time_comparison.csv";
            final boolean addHeaders = !fileExists(resultsPath);
            BufferedWriter writer = new BufferedWriter(
                    new FileWriter("solution/results_time_comparison.csv", true));

            if (addHeaders) {
                ArrayList<String> headers = new ArrayList<>(Arrays.asList(
                        "instance",
                        "distribution",
                        "strategy",
                        "column gen",
                        "Benders reschedule cost",
                        "Benders solution time (seconds)",
                        "Benders lower bound",
                        "Benders upper bound",
                        "Benders gap",
                        "Benders number of cuts",
                        "Benders number of iterations"));
                CSVHelper.writeLine(writer, headers);
            }

            ArrayList<String> row = new ArrayList<>(Arrays.asList(
                    instanceName,
                    Parameters.getDistributionType().name(),
                    Parameters.getFlightPickStrategy().name(),
                    Parameters.getColumnGenStrategy().name()));

            // solve models
            Controller controller = new Controller();
            controller.readData();
            controller.buildScenarios();
            controller.solveWithBenders();

            // write results
            row.add(Double.toString(controller.getBendersRescheduleCost()));
            row.add(Double.toString(controller.getBendersSolutionTime()));
            row.add(Double.toString(controller.getBendersLowerBound()));
            row.add(Double.toString(controller.getBendersUpperBound()));
            row.add(Double.toString(controller.getBendersGap()));
            row.add(Integer.toString(controller.getBendersNumCuts()));
            row.add(Integer.toString(controller.getBendersNumIterations()));

            CSVHelper.writeLine(writer, row);
            writer.close();
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to csv during time comparison run");
        }
    }

    /**
     * Performs runs to show the effect of changing budget.
     */
    void runBudgetComparisonSet() throws OptException {
        try {
            final double[] budgetFractions = new double[]{0.25, 0.5, 0.75, 1.0, 2.0};

            ArrayList<String> trainingHeaders = new ArrayList<>(Arrays.asList(
                    "instance",
                    "budget fraction",
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
                    new FileWriter("solution/results_budget_training.csv"));

            CSVHelper.writeLine(trainingWriter, trainingHeaders);

            BufferedWriter testWriter = new BufferedWriter(
                    new FileWriter("solution/results_budget_test.csv"));

            ArrayList<String> testHeaders = new ArrayList<>(Arrays.asList("instance", "budget fraction", "approach"));

            for (Enums.TestKPI kpi : Enums.TestKPI.values()) {
                testHeaders.add(kpi.name());
                testHeaders.add("decrease (%)");
            }

            CSVHelper.writeLine(testWriter, testHeaders);

            for (int i = 0; i < instancePaths.size(); ++i) {
                Parameters.setInstancePath(instancePaths.get(i));

                for (double budgetFraction : budgetFractions) {
                    Parameters.setRescheduleBudgetFraction(budgetFraction);

                    ArrayList<String> row = new ArrayList<>();
                    row.add(instanceNames.get(i));
                    row.add(Double.toString(budgetFraction));

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
                        row = new ArrayList<>(Arrays.asList(
                                instanceNames.get(i),
                                Double.toString(budgetFraction),
                                rescheduleSolutions.get(j).getName()
                        ));

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

            trainingWriter.close();
            testWriter.close();
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to csv during budget comparison run");
        }
    }

    /**
     * Performs runs to show the effect of changing distribution mean.
     */
    void runMeanComparisonSet() throws OptException {
        try {
            final double[] means = new double[]{15, 30, 45, 60};

            ArrayList<String> trainingHeaders = new ArrayList<>(Arrays.asList(
                    "instance",
                    "distribution mean",
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
                    new FileWriter("solution/results_mean_training.csv"));

            CSVHelper.writeLine(trainingWriter, trainingHeaders);

            BufferedWriter testWriter = new BufferedWriter(
                    new FileWriter("solution/results_mean_test.csv"));

            ArrayList<String> testHeaders = new ArrayList<>(Arrays.asList("instance", "mean", "approach"));

            for (Enums.TestKPI kpi : Enums.TestKPI.values()) {
                testHeaders.add(kpi.name());
                testHeaders.add("decrease (%)");
            }

            CSVHelper.writeLine(testWriter, testHeaders);

            for (int i = 0; i < instancePaths.size(); ++i) {
                Parameters.setInstancePath(instancePaths.get(i));

                for (double mean : means) {
                    Parameters.setDistributionMean(mean);

                    ArrayList<String> row = new ArrayList<>();
                    row.add(instanceNames.get(i));
                    row.add(Double.toString(mean));

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
                        row = new ArrayList<>(Arrays.asList(
                                instanceNames.get(i),
                                Double.toString(mean),
                                rescheduleSolutions.get(j).getName()
                        ));

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

            trainingWriter.close();
            testWriter.close();
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to csv during budget comparison run");
        }
    }

    static boolean fileExists(String pathString) {
        Path path = Paths.get(pathString);
        return Files.exists(path);
    }
}
