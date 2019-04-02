package com.stochastic.postopt;

import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TreeMap;

public class NewSolutionManager {
    /**
     * NewSolutionManager objects can be used to process/compare/write final solutions.
     */
    private final static Logger logger = LogManager.getLogger(NewSolutionManager.class);
    private String timeStamp;
    private DataRegistry dataRegistry;
    private Solution bendersSolution;
    private Solution naiveSolution;
    private TreeMap<String, Object> kpis;

    public NewSolutionManager(DataRegistry dataRegistry) {
        timeStamp = new SimpleDateFormat("yyyy_MM_dd'T'HH_mm_ss").format(new Date());
        this.dataRegistry = dataRegistry;
        kpis = new TreeMap<>();
    }

    public void setBendersSolution(Solution bendersSolution) {
        this.bendersSolution = bendersSolution;
    }

    public void setNaiveSolution(Solution naiveSolution) {
        this.naiveSolution = naiveSolution;
    }

    public void addKpi(String key, Object value) {
        kpis.put(key, value);
    }

    public void writeOutput() throws IOException {
        String bendersOutputPath = "solution/" + timeStamp + "_benders_solution.csv";
        bendersSolution.writeCSV(bendersOutputPath, dataRegistry.getLegs());
        addKpi("benders objective", bendersSolution.getObjective());
        logger.info("wrote benders output to " + bendersOutputPath);

        if (naiveSolution != null) {
            String naiveOutputPath = "solution/" + timeStamp + "_naive_solution.csv";
            naiveSolution.writeCSV(naiveOutputPath, dataRegistry.getLegs());
            kpis.put("naive objective", naiveSolution.getObjective());
            logger.info("wrote naive output to " + naiveOutputPath);
        }

        writeKpis();
        logger.info("solution processing completed.");
    }

    private void writeKpis() throws IOException {
        TreeMap<String, Object> allKpis = new TreeMap<>();
        allKpis.put("input", getInputKpis());
        allKpis.put("output", kpis);

        BufferedWriter kpiWriter = new BufferedWriter(new FileWriter("solution/" + timeStamp + "_kpis.yaml"));
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        yaml.dump(allKpis, kpiWriter);
        kpiWriter.close();
        logger.info("wrote KPIs");
    }

    private TreeMap<String, Object> getInputKpis() {
        TreeMap<String, Object> inputKpis = new TreeMap<>();
        inputKpis.put("instance path", Parameters.getInstancePath());
        inputKpis.put("number of legs", dataRegistry.getLegs().size());
        inputKpis.put("number of tails", dataRegistry.getTails().size());
        inputKpis.put("number of scenarios", dataRegistry.getNumScenarios());
        inputKpis.put("scenario delays", dataRegistry.getScenarioDelays());
        inputKpis.put("scenario probabilities", dataRegistry.getScenarioProbabilities());
        return inputKpis;
    }

    private void writeInputData(BufferedWriter writer) throws IOException {
        writer.write("input:\n");
        writer.write("  instance path: " + Parameters.getInstancePath() + "\n");
        writer.write("  number of legs: " + dataRegistry.getLegs().size() + "\n");
        writer.write("  number of tails: " + dataRegistry.getTails().size() + "\n");

        int numScenarios = dataRegistry.getNumScenarios();
        writer.write("  number of scenarios: " + numScenarios + "\n");

        StringBuilder delayStr = new StringBuilder();
        StringBuilder probStr = new StringBuilder();

        delayStr.append("  scenario delays: [");
        probStr.append("  scenario probabilities: [");

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
