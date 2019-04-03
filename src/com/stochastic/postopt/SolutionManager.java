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
    private ArrayList<RescheduleSolution> rescheduleSolutions;
    private TreeMap<String, Object> kpis;

    public SolutionManager(DataRegistry dataRegistry) {
        timeStamp = new SimpleDateFormat("yyyy_MM_dd'T'HH_mm_ss").format(new Date());
        this.dataRegistry = dataRegistry;
        rescheduleSolutions = new ArrayList<>();
        kpis = new TreeMap<>();
    }

    public void addRescheduleSolution(RescheduleSolution sln) {
        rescheduleSolutions.add(sln);
        addKpi(sln.getName() + " reschedule cost", sln.getRescheduleCost());
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

        for (RescheduleSolution sln : rescheduleSolutions) {
            logger.info("starting " + sln.getName() + " test runs...");
            qc.testSolution(sln);
            CSVHelper.writeLine(csvWriter, qc.getComparisonRow(sln.getName() + " solution"));
            logger.info("completed " + sln.getName() + " solution test runs.");
        }

        csvWriter.close();
    }

    public void writeOutput() throws IOException {
        for (RescheduleSolution sln : rescheduleSolutions) {
            sln.writeCSV(dataRegistry.getLegs());
            logger.info("wrote " + sln.getName() + " reschedule solution");
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
