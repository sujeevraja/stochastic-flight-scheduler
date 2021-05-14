package stochastic.main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.dao.RescheduleSolutionDAO;
import stochastic.delay.Scenario;
import stochastic.domain.Leg;
import stochastic.output.QualityChecker;
import stochastic.output.RescheduleSolution;
import stochastic.output.TestKPISet;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.Enums;
import stochastic.utility.OptException;
import stochastic.utility.Util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * Used to generate results for the paper.
 */
class BatchRunner {
    private final static Logger logger = LogManager.getLogger(BatchRunner.class);

    void trainingRun() throws OptException {
        try {
            logger.info("starting training run...");
            final String trainingRowPath = Parameters.getOutputPath() + "/training_result.partial";
            TrainingResult trainingResult;
            if (fileExists(trainingRowPath)) {
                FileInputStream fileInputStream = new FileInputStream(trainingRowPath);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                trainingResult = (TrainingResult) objectInputStream.readObject();
                objectInputStream.close();
            } else
                trainingResult = new TrainingResult();

            // solve models and write solutions
            Controller controller = new Controller();
            controller.setDelayGenerator();
            controller.buildScenarios();
            controller.solve();
            controller.writeRescheduleSolutions();

            // collect solution KPIs
            Enums.Model model = Parameters.getModel();
            if (model == Enums.Model.NAIVE) {
                trainingResult.setNaiveModelStats(controller.getNaiveModelStats());
                trainingResult.setNaiveRescheduleCost(controller.getNaiveModelRescheduleCost());
                trainingResult.setNaiveSolutionTime(controller.getNaiveModelSolutionTime());
            }
            if (model == Enums.Model.DEP) {
                trainingResult.setDepModelStats(controller.getDepModelStats());
                trainingResult.setDepRescheduleCost(controller.getDepRescheduleCost());
                trainingResult.setDepSolutionTime(controller.getDepSolutionTime());
            }
            if (model == Enums.Model.BENDERS) {
                trainingResult.markBendersDone();
            }

            // write KPIs
            if (trainingResult.allPopulated()) {
                HashMap<String, Object> resultMap = trainingResult.asMap();
                resultMap.putAll(controller.getBendersResults());
                Util.writeToYaml(trainingResult.asMap(),
                    Parameters.getOutputPath() + "/" + Parameters.getOutputName());
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
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to csv during training run");
        }
    }

    void testRun() throws OptException {
        // read model data and reschedule solution
        Controller controller = new Controller();
        DataRegistry dataRegistry = controller.getDataRegistry();
        RescheduleSolution rescheduleSolution =
            collectRescheduleSolution(dataRegistry.getLegs());

        // prepare test scenarios
        Scenario[] testScenarios;
        if (Parameters.isParsePrimaryDelaysFromFiles()) {
            controller.buildScenarios();
            testScenarios = dataRegistry.getDelayScenarios();
        } else {
            controller.setDelayGenerator();
            testScenarios = dataRegistry.getDelayGenerator().generateScenarios(
                Parameters.getNumTestScenarios());
        }

        // prepare test results
        Parameters.setColumnGenStrategy(Enums.ColumnGenStrategy.FULL_ENUMERATION);
        QualityChecker qc = new QualityChecker(controller.getDataRegistry(), testScenarios);
        TestKPISet testKPISet = qc.collectAverageTestStatsForBatchRun(rescheduleSolution);

        HashMap<String, Object> resultMap = Parameters.asMap();
        resultMap.put("approach", rescheduleSolution.getName());

        final double rescheduleCost = rescheduleSolution.getRescheduleCost();
        resultMap.put("rescheduleCost", rescheduleCost);

        final double delayCost = testKPISet.getKpi(Enums.TestKPI.delayCost);
        final double twoStageObj = rescheduleCost + delayCost;
        resultMap.put("twoStageObj", twoStageObj);

        for (Enums.TestKPI kpi : Enums.TestKPI.values())
            resultMap.put(kpi.name(), testKPISet.getKpi(kpi).toString());

        // write test results
        Util.writeToYaml(resultMap,
            Parameters.getOutputPath() + "/" + Parameters.getOutputName());
    }

    void bendersRun() throws OptException {
        // solve models
        Controller controller = new Controller();
        controller.setDelayGenerator();
        controller.buildScenarios();
        controller.solve();

        // collect solution KPIs
        HashMap<String, Object> resultMap = Parameters.asMap();
        resultMap.putAll(controller.getBendersResults());
        Util.writeToYaml(resultMap, Parameters.getOutputPath() + "/" +
            Parameters.getOutputName());
    }

    static boolean fileExists(String pathString) {
        Path path = Paths.get(pathString);
        return Files.exists(path);
    }

    private static RescheduleSolution collectRescheduleSolution(
        ArrayList<Leg> legs) throws OptException {
        if (Parameters.getModel() == Enums.Model.ORIGINAL) {
            return new RescheduleSolution("original", 0, null);
        }
        return (new RescheduleSolutionDAO(
            Parameters.getModel().name().toLowerCase(Locale.ROOT), legs)).getRescheduleSolution();
    }
}
