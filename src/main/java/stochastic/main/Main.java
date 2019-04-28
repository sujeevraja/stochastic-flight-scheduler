package stochastic.main;

import org.apache.commons.cli.*;
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
            Options options = new Options();
            options.addOption("b", false,
                    "batch run (single run otherwise)");
            options.addOption("c", true,
                    "column gen strategy (enum/all/best/first)");
            options.addOption("d", true,
                    "distribution (exp/tnorm/lnorm");
            options.addOption("f", true,
                    "flight pick (all/hub/rush)");
            options.addOption("n", true,
                    "instance name");
            options.addOption("p", true, "instance path");
            options.addOption("r", true, "reschedule budget fraction");
            options.addOption("t", true,
                    "type (quality/time/budget/mean/excess)");

            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            String name = cmd.hasOption('n') ? cmd.getOptionValue('n') : "instance1";
            String instancePath = cmd.hasOption('p') ? cmd.getOptionValue("p") : ("data/" + name);
            Parameters.setInstancePath(instancePath);

            setDefaultParameters();
            writeDefaultParameters();
            updateParameters(cmd);

            if (cmd.hasOption('b')) {
                BatchRunner batchRunner = new BatchRunner(name);
                String runType = cmd.getOptionValue('t');
                switch(runType) {
                    case "quality":
                        batchRunner.runForQuality();
                        break;
                    case "time":
                        batchRunner.runForTimeComparison();
                        break;
                    case "budget":
                        batchRunner.runForBudget();
                        break;
                    default:
                        throw new OptException("unknown run type: " + runType);
                }
            } else
                singleRun();
        } catch (ParseException | OptException ex) {
            logger.error(ex);
        }
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
            if (BatchRunner.fileExists(kpiFileName))
                return;

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

    private static void updateParameters(CommandLine cmd) {
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
                    logger.error("unknown column generation strattegy: " + columnGen);
                    break;
            }
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
                    Parameters.setDistributionType(Enums.DistributionType.LOGNORMAL);
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
        if (cmd.hasOption('r')) {
            final double budgetFraction = Double.parseDouble(cmd.getOptionValue('r'));
            Parameters.setRescheduleBudgetFraction(budgetFraction);
        }
    }
}

