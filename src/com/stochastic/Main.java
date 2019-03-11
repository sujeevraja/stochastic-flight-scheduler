package com.stochastic;

import com.stochastic.controller.Controller;
import com.stochastic.solver.MasterSolver;
import com.stochastic.utility.OptException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Main {
    /**
     * Only responsibility is to own main().
     */
    private final static Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            logger.info("Started optimization...");
            
            // two-stage
            long t1 = System.currentTimeMillis();            

            int numScenarios = 10;
            String path = "data\\20171115022840-v2";

            Controller.expExcess = false;            
            Controller controller = new Controller();
            controller.initHardCodedParameters(numScenarios);
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

