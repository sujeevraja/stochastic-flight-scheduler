package stochastic.main;

import stochastic.registry.Parameters;
import stochastic.utility.Enums;
import stochastic.utility.OptException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Main {
    /**
     * Class that owns main().
     */
    private final static Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            logger.info("Started optimization...");
            setParameters();

            Controller controller = new Controller();
            controller.readData();
            controller.buildScenarios();
            controller.solveWithNaiveApproach();
            if (Parameters.isSolveDEP())
                controller.solveWithDEP();
            controller.solveWithBenders();
            controller.processSolution();

            logger.info("completed optimization.");
        } catch (OptException ex) {
            logger.error(ex);
        }
    }

    private static void setParameters() {
        String path = "data/20171115022840-v2";
        // String path = "data/instance1";

        Parameters.setInstancePath(path);
        Parameters.setRescheduleTimeBudget(1500);
        Parameters.setFlightRescheduleBound(30);
        Parameters.setNumScenariosToGenerate(10);
        Parameters.setScale(3.5);
        Parameters.setShape(0.25);

        Parameters.setDistributionType(Enums.DistributionType.EXPONENTIAL);
        Parameters.setDistributionMean(15);
        Parameters.setDistributionSd(10); // ignored for exponentials.
        Parameters.setFlightPickStrategy(Enums.FlightPickStrategy.ALL);

        Parameters.setSolveDEP(true);

        Parameters.setBendersMultiCut(true);
        Parameters.setBendersTolerance(1e-3);
        Parameters.setNumBendersIterations(30);
        Parameters.setWarmStartBenders(false);

        // Second-stage parameters
        Parameters.setFullEnumeration(false);
        Parameters.setReducedCostStrategy(Enums.ReducedCostStrategy.FIRST_PATHS);
        Parameters.setNumReducedCostPaths(10);

        // Debugging parameter
        Parameters.setDebugVerbose(false); // Set to true to see CPLEX logs, lp files and solution xml files.

        // Multi-threading parameters
        Parameters.setRunSecondStageInParallel(false);
        Parameters.setNumThreadsForSecondStage(2);

        // Solution quality parameters
        Parameters.setCheckSolutionQuality(true);
        Parameters.setNumTestScenarios(10);

        // Expected excess parameters
        Parameters.setExpectedExcess(false);
        Parameters.setRho(0.9);
        Parameters.setExcessTarget(40);
    }
}

