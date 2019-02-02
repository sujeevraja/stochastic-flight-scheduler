package com.stochastic;

import com.stochastic.controller.Controller;
import com.stochastic.dao.InputsDAO;
import com.stochastic.solver.DepSolver;
import com.stochastic.solver.MasterSolver;
import com.stochastic.utility.OptException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;

public class Main {
    /**
     * Only responsibility is to own main().
     */
    private final static Logger logger = LogManager.getLogger(Main.class);
    
    public static int numScenarios     = 10;
    public static int numTestScenarios = 50;
    public static String path          = "";
    
    public static long  tsRuntime       = 0;
    public static long  tsRARuntime     = 0;
    
    public static void main(String[] args) {
        try {
            logger.info("Started optimization...");
            
            // two-stage
            long t1 = System.currentTimeMillis();            
            
            numScenarios = Integer.parseInt(args[0]);
            numTestScenarios = Integer.parseInt(args[1]);
            path = args[2];
            
            Controller.expExcess = false;            
            Controller controller = new Controller();
            controller.readData(path); 
            
//            InputsDAO.ReadData();
            
            controller.solve(); //BD
            
            tsRuntime  = (System.currentTimeMillis() - t1)/1000;            
            t1 = System.currentTimeMillis();
            
                        
            System.exit(0); // test BD
            
            controller.generateDelays();
            controller.processSolution(true, MasterSolver.getxValues());
            
            Controller.expExcess = true;            
            controller.solve(); //BD
            tsRARuntime  = (System.currentTimeMillis() - t1)/1000;
            
            Controller.expExcess = false;            
            controller.processSolution(true, MasterSolver.getxValues());
            
            controller.writeResults();

            // DepSolver ds = new DepSolver();
            // ds.constructModel(controller.getDataRegistry());
            // ds.solve();      
            

            logger.info("Completed optimization.");
        } catch (OptException oe) {
            logger.error(oe);
        }
    }
}

