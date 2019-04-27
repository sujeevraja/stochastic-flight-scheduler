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
import java.util.ArrayList;
import java.util.Arrays;

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
                    new FileWriter("solution/results_quality_training.csv"));

            CSVHelper.writeLine(trainingWriter, trainingHeaders);

            BufferedWriter testWriter = new BufferedWriter(
                    new FileWriter("solution/results_quality_test.csv"));

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
                            row = new ArrayList<>(Arrays.asList(
                                    instanceNames.get(i),
                                    distributionType.name(),
                                    flightPickStrategy.name(),
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
    void runTimeComparisonSet() throws OptException {
        try {
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

            BufferedWriter writer = new BufferedWriter(
                    new FileWriter("solution/results_time_comparison.csv"));

            CSVHelper.writeLine(writer, headers);

            for (int i = 0; i < instancePaths.size(); ++i) {
                Parameters.setInstancePath(instancePaths.get(i));

                for (Enums.DistributionType distributionType :
                        Enums.DistributionType.values()) {
                    Parameters.setDistributionType(distributionType);

                    for (Enums.FlightPickStrategy flightPickStrategy : Enums.FlightPickStrategy.values()) {
                        Parameters.setFlightPickStrategy(flightPickStrategy);

                        for (Enums.ColumnGenStrategy columnGenStrategy : Enums.ColumnGenStrategy.values()) {
                            ArrayList<String> row = new ArrayList<>(Arrays.asList(
                                    instanceNames.get(i),
                                    distributionType.name(),
                                    flightPickStrategy.name(),
                                    columnGenStrategy.name()));

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
                        }
                    }
                }
            }

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
}
