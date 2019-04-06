package stochastic.output;

import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
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

public class OutputManager {
    /**
     * OutputManager objects can be used to process/compare/write final solutions.
     */
    private final static Logger logger = LogManager.getLogger(OutputManager.class);
    private String timeStamp;
    private DataRegistry dataRegistry;
    private ArrayList<RescheduleSolution> rescheduleSolutions;
    private TreeMap<String, Object> kpis;

    public OutputManager(DataRegistry dataRegistry) {
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

        ArrayList<RescheduleSolution> allRescheduleSolutions = new ArrayList<>();
        allRescheduleSolutions.add(new RescheduleSolution("original", 0, null));
        allRescheduleSolutions.addAll(rescheduleSolutions);
        qc.compareSolutions(allRescheduleSolutions, timeStamp);
    }

    public void writeOutput() throws IOException {
        for (RescheduleSolution sln : rescheduleSolutions) {
            sln.writeCSV(timeStamp, dataRegistry.getLegs());
            logger.info("wrote " + sln.getName() + " reschedule solution");
        }
        writeKpis();
        logger.info("solution processing completed.");
    }

    private void writeKpis() throws IOException {
        TreeMap<String, Object> allKpis = new TreeMap<>();
        allKpis.put("input", getInputKpis());
        allKpis.put("output", kpis);

        String kpiFileName = "solution/" + timeStamp + "__kpis.yaml";
        BufferedWriter kpiWriter = new BufferedWriter(new FileWriter(kpiFileName));
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
