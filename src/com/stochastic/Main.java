package com.stochastic;

import com.stochastic.controller.Controller;
import com.stochastic.registry.Parameters;
import com.stochastic.solver.MasterSolver;
import com.stochastic.utility.OptException;
import ilog.concert.IloException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    /**
     * Only responsibility is to own main().
     */
    private final static Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            logger.info("Started optimization...");

            // Two-stage
            long t1 = System.currentTimeMillis();

            // ----- PARAMETER SETTINGS SECTION START -----
            Parameters.setNumScenarios(10);
            Parameters.setScale(3.5);
            Parameters.setShape(0.25);
            Parameters.setDurations(new int[]{5, 10, 15, 20, 25, 30});
            Parameters.setNumReducedCostPaths(50);
            Parameters.setFullEnumeration(true);

            Parameters.setRunSecondStageInParallel(false);
            Parameters.setNumThreadsForSecondStage(2);

            // Change setDebugVerbose to true to see CPLEX logs, lp files and solution xml files.
            Parameters.setDebugVerbose(false);

            // Expected excess parameters
            Parameters.setExpectedExcess(false);
            Parameters.setRho(0.9);
            Parameters.setExcessTarget(40);

            String path = "data\\20171115022840-v2";
            // String path = "data\\instance1";
            // ----- PARAMETER SETTINGS SECTION END -----

            Controller controller = new Controller();
            controller.readData(path);
            controller.solve(); //BD
            
            long tsRuntime  = (System.currentTimeMillis() - t1)/1000;
            logger.info("tsRuntime: " + tsRuntime + " seconds");

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

            logger.info("Completed optimization.");
        } catch (IloException | OptException ex) {
            logger.error(ex);
        }
    }
}

