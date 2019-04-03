package com.stochastic.postopt;

import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.CSVHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TreeMap;

public class SolutionManager {
    /**
     * SolutionManager objects can be used to process/compare/write final solutions.
     */
    private final static Logger logger = LogManager.getLogger(SolutionManager.class);
    private String timeStamp;
    private DataRegistry dataRegistry;
    private Solution bendersSolution;
    private Solution naiveSolution;
    private TreeMap<String, Object> kpis;

    public SolutionManager(DataRegistry dataRegistry) {
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

    public void checkSolutionQuality() throws IOException {
        QualityChecker qc = new QualityChecker(dataRegistry);
        qc.generateTestDelays();

        BufferedWriter csvWriter = new BufferedWriter(new FileWriter("solution/comparison.csv"));
        ArrayList<String> headers = new ArrayList<>();
        headers.add("");
        for (int i = 0; i < Parameters.getNumTestScenarios(); ++i) {
            headers.add("scenario " + (i+1) + " objective");
            headers.add("scenario " + (i+1) + " solution time (sec)");
        }
        headers.add("expected objective");
        headers.add("average solution time");
        CSVHelper.writeLine(csvWriter, headers);

        logger.info("starting original schedule test runs...");
        qc.testOriginalSchedule();
        CSVHelper.writeLine(csvWriter, qc.getComparisonRow("original schedule"));
        logger.info("completed original schedule test runs.");

        logger.info("starting benders solution test runs...");
        qc.testSolution(bendersSolution);
        CSVHelper.writeLine(csvWriter, qc.getComparisonRow("benders solution"));
        logger.info("completed benders solution test runs...");

        logger.info("starting naive model solution test runs...");
        qc.testSolution(naiveSolution);
        CSVHelper.writeLine(csvWriter, qc.getComparisonRow("naive model"));
        logger.info("completed naive model solution test runs...");

        csvWriter.close();
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
        inputKpis.put("number of scenarios", dataRegistry.getDelayScenarios().length);
        return inputKpis;
    }
}
