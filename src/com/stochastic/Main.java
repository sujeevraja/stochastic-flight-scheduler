package com.stochastic;

import com.stochastic.controller.Controller;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.OptException;
import ilog.concert.IloException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;

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
            controller.solve(); // Benders Decomposition
            controller.newProcessSolution();

            /*
            // ---- SECTION START ----
            // Sujeev: uncomment this section later, for now, we don't need this.
            int numTestScenarios = 50;
            controller.generateDelays(numTestScenarios);
            controller.processSolution(true, MasterSolver.getxValues(), numTestScenarios);
            
            Controller.expExcess = true;            
            controller.solve(); //BD
            long tsRARuntime  = (System.currentTimeMillis() - t1)/1000;
            
            Controller.expExcess = false;            
            controller.processSolution(true, MasterSolver.getxValues(), numTestScenarios);
            // ---- SECTION END ----
            */

            // ---- SECTION START ----
            // Sujeev: no idea what this tests, check later.
            // DepSolver ds = new DepSolver();
            // ds.constructModel(controller.getDataRegistry());
            // ds.solve();      
            // ---- SECTION END ----

            logger.info("completed optimization.");
        } catch (IOException | IloException | OptException ex) {
            logger.error(ex);
        }
    }

    private static void setParameters() {
        // String path = "data\\20171115022840-v2";
        String path = "data\\instance1";

        Parameters.setInstancePath(path);
        Parameters.setNumScenariosToGenerate(10);
        Parameters.setScale(3.5);
        Parameters.setShape(0.25);
        Parameters.setDurations(new int[]{5, 10, 15, 20, 25, 30});
        // Parameters.setDurations(new int[]{5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60});
        Parameters.setBendersTolerance(1e-3);

        // Second-stage parameters
        Parameters.setFullEnumeration(false);
        Parameters.setReducedCostStrategy(Parameters.ReducedCostStrategy.FIRST_PATHS);
        Parameters.setNumReducedCostPaths(10);

        // Debugging parameter
        Parameters.setDebugVerbose(false); // Set to true to see CPLEX logs, lp files and solution xml files.

        // Multi-threading parameters
        Parameters.setRunSecondStageInParallel(false);
        Parameters.setNumThreadsForSecondStage(2);

        // Expected excess parameters
        Parameters.setExpectedExcess(false);
        Parameters.setRho(0.9);
        Parameters.setExcessTarget(40);
    }
}

