package com.stochastic;

import com.stochastic.controller.Controller;
import com.stochastic.registry.Parameters;
import com.stochastic.solver.MasterSolver;
import com.stochastic.utility.OptException;
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

            Parameters.setNumScenarios(10);
            Parameters.setMaxLegDelayInMin(30);
            Parameters.setScale(3.5);
            Parameters.setShape(0.25);
            Parameters.setDurations(new ArrayList<>(Arrays.asList(5, 10, 15, 20, 25, 30)));
            Parameters.setFullEnumeration(true);
            Parameters.setExpectedExcess(false);
            Parameters.setRho(0.9);
            Parameters.setExcessTarget(40);

            // String path = "data\\20171115022840-v2";
            String path = "data\\instance1";

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
        } catch (OptException oe) {
            logger.error(oe);
        }
    }
}

