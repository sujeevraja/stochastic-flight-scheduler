package stochastic.main;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import stochastic.registry.Parameters;
import stochastic.utility.Enums;
import stochastic.utility.OptException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TreeMap;

public class Main {
    /**
     * Class that owns main().
     */
    private final static Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            batchRun();
            // singleRun();
        } catch (OptException ex) {
            logger.error(ex);
        }
    }

    private static void batchRun() throws OptException {
        BatchRunner batchRunner = new BatchRunner();

        setDefaultParameters();
        writeDefaultParameters();

        batchRunner.runQualitySet();

        setDefaultParameters();
        batchRunner.runTimeComparisonSet();

        setDefaultParameters();
        batchRunner.runBudgetComparisonSet();

        setDefaultParameters();
        batchRunner.runMeanComparisonSet();
    }

    private static void singleRun() throws OptException {
        logger.info("Started optimization...");

        // String path = "data/20171115022840-v2";
        String path = "data/instance1";
        Parameters.setInstancePath(path);

        setDefaultParameters();
        writeDefaultParameters();

        Controller controller = new Controller();
        controller.readData();
        controller.buildScenarios();
        controller.solveWithNaiveApproach();
        controller.solveWithDEP();
        controller.solveWithBenders();
        controller.processSolution();

        logger.info("completed optimization.");
    }

    private static void setDefaultParameters() {
        Parameters.setRescheduleBudgetFraction(0.5);
        Parameters.setFlightRescheduleBound(30);
        Parameters.setNumSecondStageScenarios(30);

        Parameters.setDistributionType(Enums.DistributionType.LOGNORMAL);
        Parameters.setDistributionMean(15);
        Parameters.setDistributionSd(15); // ignored for exponential distribution.
        Parameters.setFlightPickStrategy(Enums.FlightPickStrategy.HUB);

        Parameters.setBendersMultiCut(true);
        Parameters.setBendersTolerance(1e-3);
        Parameters.setNumBendersIterations(30);
        Parameters.setWarmStartBenders(false);

        // Second-stage parameters
        Parameters.setColumnGenStrategy(Enums.ColumnGenStrategy.FIRST_PATHS);
        Parameters.setNumReducedCostPaths(10); // ignored for full enumeration

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

    private static void writeDefaultParameters() throws OptException {
        try {
            String kpiFileName = "solution/parameters.yaml";
            TreeMap<String, Object> parameters = new TreeMap<>();

            parameters.put("column gen strategy", Parameters.getColumnGenStrategy().name());
            parameters.put("column gen num reduced cost paths per tail", Parameters.getNumReducedCostPaths());
            parameters.put("distribution mean", Parameters.getDistributionMean());
            parameters.put("distribution standard deviation", Parameters.getDistributionSd());
            parameters.put("distribution type", Parameters.getDistributionType().name());
            parameters.put("benders multi-cut", Parameters.isBendersMultiCut());
            parameters.put("benders tolerance", Parameters.getBendersTolerance());
            parameters.put("benders num iterations", Parameters.getNumBendersIterations());
            parameters.put("benders warm start", Parameters.isWarmStartBenders());
            parameters.put("expected excess enabled", Parameters.isExpectedExcess());
            parameters.put("expected excess rho: ", Parameters.getRho());
            parameters.put("expected excess target: ", Parameters.getExcessTarget());
            parameters.put("flight pick strategy", Parameters.getFlightPickStrategy().name());
            parameters.put("reschedule time budget fraction", Parameters.getRescheduleBudgetFraction());
            parameters.put("reschedule time limit for flights", Parameters.getFlightRescheduleBound());
            parameters.put("second stage in parallel", Parameters.isRunSecondStageInParallel());
            parameters.put("second stage num scenarios", Parameters.getNumSecondStageScenarios());
            parameters.put("second stage num threads", Parameters.getNumThreadsForSecondStage());
            parameters.put("test number of scenarios", Parameters.getNumTestScenarios());

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

