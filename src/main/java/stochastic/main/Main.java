package stochastic.main;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.registry.Parameters;
import stochastic.utility.Enums;
import stochastic.utility.OptException;
import stochastic.utility.Util;

import java.util.TreeMap;

/**
 * Class that owns main().
 */
public class Main {
    private final static Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            CommandLine cmd = addOptions(args);
            if (cmd == null)
                return;

            final String name = cmd.getOptionValue("name", "s6");
            final String path = cmd.getOptionValue("path", "data");
            Parameters.setInstanceName(name);
            Parameters.setInstancePath(path + "/" + name + ".xml");
            Parameters.setOutputPath(cmd.getOptionValue("outputPath", "solution"));
            Parameters.setOutputName(cmd.getOptionValue("outputName", "result.yaml"));

            setDefaultParameters();
            // writeDefaultParameters();
            updateParameters(cmd);

            if (cmd.hasOption("stats"))
                writeStatsAndExit();
            else if (cmd.hasOption("generateDelays"))
                writeDelaysAndExit();
            else if (cmd.hasOption("batch"))
                batchRun(name, cmd.getOptionValue("type"));
            else
                singleRun();
        } catch (OptException ex) {
            logger.error(ex);
            ex.printStackTrace();
        }
    }

    private static CommandLine addOptions(String[] args) throws OptException {
        Options options = new Options();
        options.addOption("batch", false,
            "batch run (single run otherwise)");
        options.addOption("c", true,
            "column gen strategy (enum/all/best/first)");
        options.addOption("cache", true,
            "use column caching (y/n)");
        options.addOption("cut", true, "benders cut type (single/multi)");
        options.addOption("d", true,
            "distribution (exp/tnorm/lnorm)");
        options.addOption("expectedExcess", true,
            "enable expected excess (y/n)");
        options.addOption("excessTarget", true,
            "expected excess target");
        options.addOption("excessAversion", true,
            "expected excess risk aversion");
        options.addOption("f", true,
            "flight pick (all/hub/rush)");
        options.addOption("generateDelays", false,
            "generate primary delays, write to file and exit");
        options.addOption("mean", true, "distribution mean");
        options.addOption("model", true, "model (naive/dep/benders/all)");
        options.addOption("name", true, "instance name");
        options.addOption("numScenarios", true, "number of scenarios");
        options.addOption("outputPath", true, "path to output folder");
        options.addOption("outputName", true, "name of output file");
        options.addOption("parallel", true,
            "number of parallel runs for second stage");
        options.addOption("parseDelays", false,
            "parse primary delays from files");
        options.addOption("path", true, "path to folder with instance");
        options.addOption("r", true, "reschedule budget fraction");
        options.addOption("sd", true, "standard deviation");
        options.addOption("stats", false, "generate stats about instance");
        options.addOption("type", true,
            "type (benders/training/test)");
        options.addOption("h", false, "help (show options and exit)");

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption('h')) {
                HelpFormatter helpFormatter = new HelpFormatter();
                helpFormatter.printHelp("stochastic.jar/stochastic_uber.jar", options);
                return null;
            }
            return cmd;
        } catch (ParseException ex) {
            logger.error(ex);
            throw new OptException("error parsing CLI args");
        }
    }

    private static void writeStatsAndExit() throws OptException {
        logger.info("started primary delay generation...");
        Controller controller = new Controller();
        controller.computeStats();
        logger.info("completed primary delay generation.");
    }

    private static void writeDelaysAndExit() throws OptException {
        logger.info("started primary delay generation...");
        Controller controller = new Controller();
        controller.computeStats();
        controller.setDelayGenerator();
        Parameters.setParsePrimaryDelaysFromFiles(false);
        controller.buildScenarios();
        controller.writeScenariosToFile();
        logger.info("completed primary delay generation.");
    }

    private static void batchRun(String name, String runType) throws OptException {
        BatchRunner batchRunner = new BatchRunner(name);
        switch (runType) {
            case "benders":
                batchRunner.bendersRun();
                break;
            case "test":
                batchRunner.testRun();
                break;
            case "training":
                batchRunner.trainingRun();
                break;
            default:
                throw new OptException("unknown run type: " + runType);
        }
    }

    private static void singleRun() throws OptException {
        logger.info("Started optimization...");
        Controller controller = new Controller();
        controller.computeStats();
        controller.setDelayGenerator();
        controller.buildScenarios();
        controller.solve();
        // controller.processSolution();
        logger.info("completed optimization.");
    }

    private static void setDefaultParameters() {
        Parameters.setModel(Enums.Model.BENDERS);
        Parameters.setRescheduleBudgetFraction(0.5);
        Parameters.setFlightRescheduleBound(30);
        Parameters.setNumSecondStageScenarios(30);

        Parameters.setDistributionType(Enums.DistributionType.LOG_NORMAL);
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
        Parameters.setUseColumnCaching(true);

        // Debugging parameter
        Parameters.setDebugVerbose(false); // Set to true to see CPLEX logs, lp files and solution xml files.
        Parameters.setSetCplexNames(false);
        Parameters.setShowCplexOutput(false);

        // Multi-threading parameters
        Parameters.setRunSecondStageInParallel(true);
        Parameters.setNumThreadsForSecondStage(6);

        // Solution quality parameters
        Parameters.setCheckSolutionQuality(true);
        Parameters.setNumTestScenarios(100);

        // Expected excess parameters
        Parameters.setExpectedExcess(false);
        Parameters.setRiskAversion(0.9);
        Parameters.setExcessTarget(40);
    }

    private static void updateParameters(CommandLine cmd) throws OptException {
        if (cmd.hasOption('c')) {
            final String columnGen = cmd.getOptionValue('c');
            switch (columnGen) {
                case "enum":
                    Parameters.setColumnGenStrategy(Enums.ColumnGenStrategy.FULL_ENUMERATION);
                    break;
                case "all":
                    Parameters.setColumnGenStrategy(Enums.ColumnGenStrategy.ALL_PATHS);
                    break;
                case "best":
                    Parameters.setColumnGenStrategy(Enums.ColumnGenStrategy.BEST_PATHS);
                    break;
                case "first":
                    Parameters.setColumnGenStrategy(Enums.ColumnGenStrategy.FIRST_PATHS);
                    break;
                default:
                    logger.error("unknown column generation strategy: " + columnGen);
                    break;
            }
        }
        if (cmd.hasOption("cut")) {
            final String cutType = cmd.getOptionValue("cut");
            if (cutType.equals("single"))
                Parameters.setBendersMultiCut(false);
            else if (cutType.equals("multi"))
                Parameters.setBendersMultiCut(true);
            else throw new OptException("unknown cut type " + cutType);
        }
        if (cmd.hasOption('d')) {
            final String distribution = cmd.getOptionValue('d');
            switch (distribution) {
                case "exp":
                    Parameters.setDistributionType(Enums.DistributionType.EXPONENTIAL);
                    break;
                case "tnorm":
                    Parameters.setDistributionType(Enums.DistributionType.TRUNCATED_NORMAL);
                    break;
                case "lnorm":
                    Parameters.setDistributionType(Enums.DistributionType.LOG_NORMAL);
                    break;
                default:
                    logger.error("unknown distribution: " + distribution);
                    break;
            }
        }
        if (cmd.hasOption('f')) {
            final String distribution = cmd.getOptionValue('f');
            switch (distribution) {
                case "all":
                    Parameters.setFlightPickStrategy(Enums.FlightPickStrategy.ALL);
                    break;
                case "hub":
                    Parameters.setFlightPickStrategy(Enums.FlightPickStrategy.HUB);
                    break;
                case "rush":
                    Parameters.setFlightPickStrategy(Enums.FlightPickStrategy.RUSH_TIME);
                    break;
                default:
                    logger.error("unknown flight pick strategy: " + distribution);
                    break;
            }
        }
        if (cmd.hasOption("mean")) {
            final double mean = Double.parseDouble(cmd.getOptionValue("mean"));
            Parameters.setDistributionMean(mean);
        }
        if (cmd.hasOption("model")) {
            final String model = cmd.getOptionValue("model").toLowerCase();
            switch (model) {
                case "benders":
                    Parameters.setModel(Enums.Model.BENDERS);
                    break;
                case "dep":
                    Parameters.setModel(Enums.Model.DEP);
                    break;
                case "naive":
                    Parameters.setModel(Enums.Model.NAIVE);
                    break;
                case "all":
                    Parameters.setModel(Enums.Model.ALL);
                    break;
                default:
                    throw new OptException("unknown model type, use benders/dep/naive/all");
            }
        }
        else
            logger.info("model not provided, defaulting to Benders");
        if (cmd.hasOption("numScenarios")) {
            final int numScenarios = Integer.parseInt(cmd.getOptionValue("numScenarios"));
            Parameters.setNumSecondStageScenarios(numScenarios);
        }
        if (cmd.hasOption("parallel")) {
            final int numThreads = Integer.parseInt(cmd.getOptionValue("parallel"));
            Parameters.setNumThreadsForSecondStage(numThreads);
            Parameters.setRunSecondStageInParallel(numThreads > 1);
        }
        if (cmd.hasOption("parseDelays"))
            Parameters.setParsePrimaryDelaysFromFiles(true);
        if (cmd.hasOption('r')) {
            final double budgetFraction = Double.parseDouble(cmd.getOptionValue('r'));
            Parameters.setRescheduleBudgetFraction(budgetFraction);
        }
        if (cmd.hasOption("cache")) {
            final boolean useCaching = cmd.getOptionValue("cache").equals("y");
            Parameters.setUseColumnCaching(useCaching);
            logger.info("use column caches: " + useCaching);
        }
        if (cmd.hasOption("sd")) {
            final double sd = Double.parseDouble(cmd.getOptionValue("sd"));
            Parameters.setDistributionSd(sd);
        }
        if (cmd.hasOption("expectedExcess")) {
            final boolean useExpectedExcess = cmd.getOptionValue("expectedExcess").equals("y");
            Parameters.setExpectedExcess(useExpectedExcess);
            logger.info("use expected excess: " + useExpectedExcess);
        }
        if (cmd.hasOption("excessTarget")) {
            final int excessTarget = Integer.parseInt(cmd.getOptionValue("excessTarget"));
            Parameters.setExcessTarget(excessTarget);
        }
        if (cmd.hasOption("excessAversion")) {
            final double aversion = Double.parseDouble(cmd.getOptionValue("excessAversion"));
            Parameters.setRiskAversion(aversion);
        }
    }
}

