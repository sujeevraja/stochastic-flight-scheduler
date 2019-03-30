package com.stochastic.postopt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class NewSolutionManager {
    /**
     * NewSolutionManager objects can be used to process/compare/write final solutions.
     */
    private final static Logger logger = LogManager.getLogger(NewSolutionManager.class);
    private Solution bendersSolution;
    private BufferedWriter kpiWriter;

    public NewSolutionManager(Solution bendersSolution) throws IOException {
        this.bendersSolution = bendersSolution;
        kpiWriter = new BufferedWriter(new FileWriter("solution/kpis.yaml"));
    }

    public void writeOutput() throws IOException {
        String bendersOutputPath = "solution/benders_solution.csv";
        bendersSolution.writeCSV(bendersOutputPath);
        logger.info("wrote benders output to " + bendersOutputPath);

        kpiWriter.write("---\n");
        kpiWriter.write("Benders objective: " + bendersSolution.getObjective() + "\n");
        kpiWriter.write("Benders theta: " + bendersSolution.getThetaValue() + "\n");
        kpiWriter.write("...\n");
        kpiWriter.close();
        logger.info("wrote KPIs");

        logger.info("solution processing completed.");
    }
}
