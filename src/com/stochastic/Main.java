package com.stochastic;

import com.stochastic.controller.Controller;
import com.stochastic.utility.OptException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


public class Main {
    /**
     * Only responsibility is to own main().
     */
    private final static Logger logger = LogManager.getLogger(Main.class);
    public static int nSce = 10;

    public static void main(String[] args) {
        try {
            logger.info("Started optimization...");
            Controller controller = new Controller();
            controller.readData();
            controller.createTestDisruption();
            controller.algorithmSteps();
//            controller.solveSecondStage();
            logger.info("Completed optimization.");
        } catch (OptException oe) {
            logger.error(oe);
        }
    }
}

