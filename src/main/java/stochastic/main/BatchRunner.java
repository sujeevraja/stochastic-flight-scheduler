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

import java.io.*;
import java.nio.Buffer;
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
    private String instanceName;

    BatchRunner(String instanceName) {
        this.instanceName = instanceName;
    }

    /**
     * Generates and tests solutions for naive model, DEP and Benders.
     * <p>
     * Runs naive/DEP/Benders to generate reschedule solutions and compares them with the original
     * schedule with 100 randomly generated delay scenarios. Reschedule and test KPIs are written
     * to separate csv files.
     *
     * @throws OptException when there is any issue with solving/writing.
     */
    void runForQuality() throws OptException {
        /*
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
            controller.setDelayGenerator();
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
         */
    }

    /**
     * Generates reschedule KPIs for Benders with full enumeration and 3 column gen strategies.
     *
     * @throws OptException when there is any issue with solving/writing.
     */
    void runForTimeComparison() throws OptException {
        /*
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
            controller.setDelayGenerator();
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
         */
    }

    void trainingRun() throws OptException {
        try {
            logger.info("starting training run...");
            final String trainingPath = "solution/results_training.csv";
            final boolean addTrainingHeaders = !fileExists(trainingPath);
            if (addTrainingHeaders) {
                BufferedWriter trainingWriter = new BufferedWriter(
                    new FileWriter(trainingPath));
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
                CSVHelper.writeLine(trainingWriter, trainingHeaders);
                trainingWriter.close();
            }

            final String trainingRowPath = "solution/partial_training_result.txt";
            TrainingResult trainingResult;
            if (fileExists(trainingRowPath)) {
                FileInputStream fileInputStream = new FileInputStream(trainingRowPath);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                trainingResult = (TrainingResult) objectInputStream.readObject();
                objectInputStream.close();
            } else
                trainingResult = new TrainingResult();

            if (trainingResult.getInstance() == null)
                trainingResult.setInstance(instanceName);

            if (trainingResult.getBudgetFraction() == null)
                trainingResult.setBudgetFraction(Parameters.getRescheduleBudgetFraction());

            // solve models and write solutions
            Controller controller = new Controller();
            controller.readData();
            controller.setDelayGenerator();
            controller.buildScenarios();
            controller.solve();
            controller.writeRescheduleSolutions();

            // collect solution KPIs
            Enums.Model model = Parameters.getModel();
            if (model == Enums.Model.NAIVE || model == Enums.Model.ALL) {
                trainingResult.setNaiveRescheduleCost(controller.getNaiveModelRescheduleCost());
                trainingResult.setNaiveSolutionTime(controller.getNaiveModelSolutionTime());
            }
            if (model == Enums.Model.DEP || model == Enums.Model.ALL) {
                trainingResult.setDepRescheduleCost(controller.getDepRescheduleCost());
                trainingResult.setDepSolutionTime(controller.getDepSolutionTime());
            }
            if (model == Enums.Model.BENDERS || model == Enums.Model.ALL) {
                trainingResult.setBendersRescheduleCost(controller.getBendersRescheduleCost());
                trainingResult.setBendersSolutionTime(controller.getBendersSolutionTime());
                trainingResult.setBendersLowerBound(controller.getBendersLowerBound());
                trainingResult.setBendersUpperBound(controller.getBendersUpperBound());
                trainingResult.setBendersGap(controller.getBendersGap());
                trainingResult.setBendersNumCuts(controller.getBendersNumCuts());
                trainingResult.setBendersNumIterations(controller.getBendersNumIterations());
            }

            // write KPIs
            if (trainingResult.allPopulated()) {
                BufferedWriter bw = new BufferedWriter(new FileWriter(trainingPath, true));
                CSVHelper.writeLine(bw, trainingResult.getCsvRow());
                bw.close();
                File file = new File(trainingRowPath);
                if (!file.delete())
                    throw new OptException("unable to delete object file");
            } else {
                FileOutputStream fileOutputStream = new FileOutputStream(trainingRowPath);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
                objectOutputStream.writeObject(trainingResult);
                objectOutputStream.flush();
                objectOutputStream.close();
            }
            logger.info("completed training run.");
        } catch (ClassNotFoundException ex) {
            logger.error(ex);
            throw new OptException("unable to parse training row from file");
        }
        catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to csv during training run");
        }
    }

    void testRun() throws OptException {
        try {
            final String testPath = "solution/results_test.csv";
            final boolean addTestHeaders = !fileExists(testPath);
            BufferedWriter testWriter = new BufferedWriter(new FileWriter(testPath, true));

            if (addTestHeaders) {
                ArrayList<String> testHeaders = new ArrayList<>(Arrays.asList(
                        "instance", "budget fraction", "approach"));

                for (Enums.TestKPI kpi : Enums.TestKPI.values()) {
                    testHeaders.add(kpi.name());
                    testHeaders.add("decrease (%)");
                }
                CSVHelper.writeLine(testWriter, testHeaders);
            }

            ArrayList<String> row = new ArrayList<>();
            row.add(instanceName);
            row.add(Double.toString(Parameters.getRescheduleBudgetFraction()));

            // solve models
            Controller controller = new Controller();
            controller.readData();
            controller.setDelayGenerator();
            ArrayList<RescheduleSolution> rescheduleSolutions =
                    controller.collectRescheduleSolutionsFromFiles();

            QualityChecker qc = new QualityChecker(controller.getDataRegistry());
            qc.generateTestDelays();
            TestKPISet[] testKPISets = qc.collectAverageTestStatsForBatchRun(rescheduleSolutions);
            TestKPISet baseKPISet = testKPISets[0];

            // write test results
            for (int j = 0; j < testKPISets.length; ++j) {
                row = new ArrayList<>(Arrays.asList(
                        instanceName,
                        Double.toString(Parameters.getRescheduleBudgetFraction()),
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

            testWriter.close();
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to csv during test run");
        }
    }

    /**
     * Performs a run to show the effect of changing distribution mean.
     *
     * @throws OptException when there is any issue with solving/writing.
     */
    void runForMeanComparison() throws OptException {
        /*
        try {
            final String trainingPath = "solution/results_mean_training.csv";
            final boolean addTrainingHeaders = !fileExists(trainingPath);
            BufferedWriter trainingWriter = new BufferedWriter(
                    new FileWriter(trainingPath, true));

            if (addTrainingHeaders) {
                ArrayList<String> trainingHeaders = new ArrayList<>(Arrays.asList(
                        "instance",
                        "distribution type",
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
                CSVHelper.writeLine(trainingWriter, trainingHeaders);
            }

            final String testPath = "solution/results_mean_test.csv";
            final boolean addTestHeaders = !fileExists(testPath);
            BufferedWriter testWriter = new BufferedWriter(
                    new FileWriter(testPath, true));

            if (addTestHeaders) {
                ArrayList<String> testHeaders = new ArrayList<>(
                        Arrays.asList("instance", "distribution", "mean", "approach"));

                for (Enums.TestKPI kpi : Enums.TestKPI.values()) {
                    testHeaders.add(kpi.name());
                    testHeaders.add("decrease (%)");
                }
                CSVHelper.writeLine(testWriter, testHeaders);
            }

            ArrayList<String> row = new ArrayList<>();
            row.add(instanceName);
            row.add(Parameters.getDistributionType().name());
            row.add(Double.toString(Parameters.getDistributionMean()));

            // solve models
            Controller controller = new Controller();
            controller.readData();
            controller.setDelayGenerator();
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
                        instanceName,
                        Parameters.getDistributionType().name(),
                        Double.toString(Parameters.getDistributionMean()),
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

            trainingWriter.close();
            testWriter.close();
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to csv during budget comparison run");
        }
         */
    }

    static boolean fileExists(String pathString) {
        Path path = Paths.get(pathString);
        return Files.exists(path);
    }
}
