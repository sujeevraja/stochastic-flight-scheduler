package stochastic.main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.delay.Scenario;
import stochastic.output.QualityChecker;
import stochastic.output.RescheduleSolution;
import stochastic.output.TestKPISet;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.CSVHelper;
import stochastic.utility.Enums;
import stochastic.utility.OptException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Used to generate results for the paper.
 */
class BatchRunner {
    private final static Logger logger = LogManager.getLogger(BatchRunner.class);
    private String instanceName;

    BatchRunner(String instanceName) {
        this.instanceName = instanceName;
    }

    void trainingRun() throws OptException {
        try {
            logger.info("starting training run...");
            final String trainingPath = "solution/results_training.csv";
            final boolean addTrainingHeaders = !fileExists(trainingPath);
            if (addTrainingHeaders) {
                BufferedWriter trainingWriter = new BufferedWriter(
                    new FileWriter(trainingPath));
                CSVHelper.writeLine(trainingWriter, TrainingResult.getCsvHeaders());
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

            trainingResult.setStrategy(Parameters.getFlightPickStrategy().toString());
            trainingResult.setDistribution(Parameters.getDistributionType().toString());
            trainingResult.setDistributionMean(Parameters.getDistributionMean());
            trainingResult.setDistributionSd(Parameters.getDistributionSd());

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
                trainingResult.setNaiveModelStats(controller.getNaiveModelStats());
                trainingResult.setNaiveRescheduleCost(controller.getNaiveModelRescheduleCost());
                trainingResult.setNaiveSolutionTime(controller.getNaiveModelSolutionTime());
            }
            if (model == Enums.Model.DEP || model == Enums.Model.ALL) {
                trainingResult.setDepModelStats(controller.getDepModelStats());
                trainingResult.setDepRescheduleCost(controller.getDepRescheduleCost());
                trainingResult.setDepSolutionTime(controller.getDepSolutionTime());
            }
            if (model == Enums.Model.BENDERS || model == Enums.Model.ALL) {
                trainingResult.setBendersRescheduleCost(controller.getBendersRescheduleCost());
                trainingResult.setBendersSolutionTime(controller.getBendersSolutionTime());
                trainingResult.setBendersLowerBound(controller.getBendersLowerBound());
                trainingResult.setBendersUpperBound(controller.getBendersUpperBound());
                trainingResult.setBendersGlobalUpperBound(controller.getBendersGlobalUpperBound());
                trainingResult.setBendersGap(controller.getBendersGap());
                trainingResult.setBendersOptimalityGap(controller.getBendersOptimalityGap());
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
                    "instance", "strategy", "distribution", "mean", "standard deviation",
                    "budget fraction", "expected excess", "excess target", "excess aversion",
                    "approach", "rescheduleCost", "twoStageObjective", "decrease (%)",
                    "expExcessObjective", "decrease (%)"));

                for (Enums.TestKPI kpi : Enums.TestKPI.values()) {
                    testHeaders.add(kpi.name());
                    testHeaders.add("decrease (%)");
                }
                CSVHelper.writeLine(testWriter, testHeaders);
            }

            // read model data
            Controller controller = new Controller();
            controller.readData();

            // read all reschedule solutions
            ArrayList<RescheduleSolution> rescheduleSolutions =
                controller.collectRescheduleSolutionsFromFiles();

            // prepare test scenarios
            DataRegistry dataRegistry = controller.getDataRegistry();
            Scenario[] testScenarios;
            if (Parameters.isParsePrimaryDelaysFromFiles()) {
                controller.buildScenarios();
                testScenarios = dataRegistry.getDelayScenarios();
            }
            else {
                controller.setDelayGenerator();
                testScenarios = dataRegistry.getDelayGenerator().generateScenarios(
                    Parameters.getNumTestScenarios());
            }

            // prepare test results
            Parameters.setColumnGenStrategy(Enums.ColumnGenStrategy.FULL_ENUMERATION);
            QualityChecker qc = new QualityChecker(controller.getDataRegistry(), testScenarios);
            TestKPISet[] testKPISets = qc.collectAverageTestStatsForBatchRun(rescheduleSolutions);
            TestKPISet baseKPISet = testKPISets[0];

            // write test results
            Double baseObj = null;
            Double baseExpExcessObj = null;
            for (int j = 0; j < testKPISets.length; ++j) {
                TestKPISet testKPISet = testKPISets[j];
                final double rescheduleCost = rescheduleSolutions.get(j).getRescheduleCost();
                final double delayCost = testKPISet.getKpi(Enums.TestKPI.delayCost);
                final double eeDelayCost = testKPISet.getKpi(Enums.TestKPI.expExcessDelayCost);
                final double twoStageObj = rescheduleCost + delayCost;
                final double eeObj = rescheduleCost + eeDelayCost;
                if (baseObj == null) {
                    baseObj = twoStageObj;
                    baseExpExcessObj = eeObj;
                }

                ArrayList<String> row = new ArrayList<>(Arrays.asList(
                        instanceName,
                        Parameters.getFlightPickStrategy().toString(),
                        Parameters.getDistributionType().toString(),
                        Double.toString(Parameters.getDistributionMean()),
                        Double.toString(Parameters.getDistributionSd()),
                        Double.toString(Parameters.getRescheduleBudgetFraction()),
                        Boolean.toString(Parameters.isExpectedExcess()),
                        Integer.toString(Parameters.getExcessTarget()),
                        Double.toString(Parameters.getRiskAversion())));

                row.addAll(Arrays.asList(
                    rescheduleSolutions.get(j).getName(),
                    Double.toString(rescheduleCost),
                    Double.toString(twoStageObj)));

                double twoStageObjDecrease = 0.0;
                if (j > 0) twoStageObjDecrease = ((baseObj - twoStageObj) / baseObj) * 100.0;
                row.add(Double.toString(twoStageObjDecrease));

                row.add(Double.toString(eeObj));
                double eeObjDecrease = 0.0;
                if (j > 0) eeObjDecrease = ((baseExpExcessObj - eeObj) / baseExpExcessObj) * 100.0;
                row.add(Double.toString(eeObjDecrease));

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

    void bendersRun() throws OptException {
        try {
            final String resultPath = "solution/results_benders.csv";
            final boolean addHeaders = !fileExists(resultPath);
            if (addHeaders) {
                BufferedWriter trainingWriter = new BufferedWriter(
                    new FileWriter(resultPath));
                ArrayList<String> headers = new ArrayList<>(Arrays.asList(
                        "instance",
                        "column strategy",
                        "caching",
                        "threads",
                        "multi cut",
                        "Benders reschedule cost",
                        "Benders solution time (seconds)",
                        "Benders lower bound",
                        "Benders upper bound",
                        "Benders global upper bound",
                        "Benders gap (%)",
                        "Benders optimality gap (%)",
                        "Benders number of cuts",
                        "Benders number of iterations"));
                CSVHelper.writeLine(trainingWriter, headers);
                trainingWriter.close();
            }

            List<String> row = new ArrayList<>();
            row.add(instanceName);
            row.add(Parameters.getColumnGenStrategy().toString());
            row.add(Boolean.toString(Parameters.isUseColumnCaching()));
            row.add(Integer.toString(Parameters.getNumThreadsForSecondStage()));
            row.add(Boolean.toString(Parameters.isBendersMultiCut()));

            // solve models and write solutions
            Controller controller = new Controller();
            controller.readData();
            controller.setDelayGenerator();
            controller.buildScenarios();
            controller.solve();

            // collect solution KPIs
            row.add(Double.toString(controller.getBendersRescheduleCost()));
            row.add(Double.toString(controller.getBendersSolutionTime()));
            row.add(Double.toString(controller.getBendersLowerBound()));
            row.add(Double.toString(controller.getBendersUpperBound()));
            row.add(Double.toString(controller.getBendersGlobalUpperBound()));
            row.add(Double.toString(controller.getBendersGap()));
            row.add(Double.toString(controller.getBendersOptimalityGap()));
            row.add(Double.toString(controller.getBendersNumCuts()));
            row.add(Integer.toString(controller.getBendersNumIterations()));

            BufferedWriter bw = new BufferedWriter(new FileWriter(resultPath, true));
            CSVHelper.writeLine(bw, row);
            bw.close();
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to csv during multi-threading run");
        }
    }

    static boolean fileExists(String pathString) {
        Path path = Paths.get(pathString);
        return Files.exists(path);
    }
}
