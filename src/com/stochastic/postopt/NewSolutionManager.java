package com.stochastic.postopt;

import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class NewSolutionManager {
    /**
     * NewSolutionManager objects can be used to process/compare/write final solutions.
     */
    private final static Logger logger = LogManager.getLogger(NewSolutionManager.class);
    private String timeStamp;
    private DataRegistry dataRegistry;
    private Solution bendersSolution;
    private double bendersSolutionTime;
    private Solution naiveSolution;

    public NewSolutionManager(DataRegistry dataRegistry) {
        timeStamp = new SimpleDateFormat("yyyy_MM_dd'T'HH_mm_ss").format(new Date());
        this.dataRegistry = dataRegistry;
    }

    public void setBendersSolution(Solution bendersSolution) {
        this.bendersSolution = bendersSolution;
    }

    public void setBendersSolutionTime(double bendersSolutionTime) {
        this.bendersSolutionTime = bendersSolutionTime;
    }

    public void setNaiveSolution(Solution naiveSolution) {
        this.naiveSolution = naiveSolution;
    }

    public void writeOutput() throws IOException {
        String bendersOutputPath = "solution/" + timeStamp + "_benders_solution.csv";
        bendersSolution.writeCSV(bendersOutputPath, dataRegistry.getLegs());
        logger.info("wrote benders output to " + bendersOutputPath);

        if (naiveSolution != null) {
            String naiveOutputPath = "solution/" + timeStamp + "_naive_solution.csv";
            naiveSolution.writeCSV(naiveOutputPath, dataRegistry.getLegs());
            logger.info("wrote naive output to " + naiveOutputPath);
        }

        // write KPIs
        BufferedWriter kpiWriter = new BufferedWriter(new FileWriter("solution/" + timeStamp + "_kpis.yaml"));
        kpiWriter.write("---\n");
        writeInputData(kpiWriter);
        kpiWriter.write("Benders objective: " + bendersSolution.getObjective() + "\n");
        kpiWriter.write("Benders theta: " + bendersSolution.getThetaValue() + "\n");
        kpiWriter.write("Benders solution time (seconds): " + bendersSolutionTime + "\n");
        if (naiveSolution != null)
            kpiWriter.write("Naive objective: " + naiveSolution.getObjective() + "\n");
        else
            kpiWriter.write("Naive objective: N/A\n");
        kpiWriter.write("...\n");
        kpiWriter.close();
        logger.info("wrote KPIs");

        logger.info("solution processing completed.");
    }

    private void writeInputData(BufferedWriter writer) throws IOException {
        writer.write("instance path: " + Parameters.getInstancePath() + "\n");
        writer.write("number of tails: " + dataRegistry.getTails().size() + "\n");
        writer.write("number of legs: " + dataRegistry.getLegs().size() + "\n");

        int numScenarios = dataRegistry.getNumScenarios();
        writer.write("number of scenarios: " + numScenarios + "\n");

        StringBuilder delayStr = new StringBuilder();
        StringBuilder probStr = new StringBuilder();

        delayStr.append("scenario delays: [");
        probStr.append("scenario probabilities: [");

        int[] scenarioDelays = dataRegistry.getScenarioDelays();
        double[] scenarioProbabilities = dataRegistry.getScenarioProbabilities();
        for(int i = 0; i < numScenarios; ++i) {
            delayStr.append(scenarioDelays[i]);
            probStr.append(scenarioProbabilities[i]);
            if (i < numScenarios - 1) {
                delayStr.append(", ");
                probStr.append(", ");
            }
        }

        delayStr.append("]\n");
        probStr.append("]\n");

        writer.write(delayStr.toString());
        writer.write(probStr.toString());
    }
}
