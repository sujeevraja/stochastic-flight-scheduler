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
import java.util.TreeMap;

public class KpiManager {
    /**
     * KpiManager objects can be used to process/compare/write final solutions.
     */
    private final static Logger logger = LogManager.getLogger(KpiManager.class);
    private DataRegistry dataRegistry;
    private TreeMap<String, Object> kpis;

    public KpiManager(DataRegistry dataRegistry) {
        this.dataRegistry = dataRegistry;
        kpis = new TreeMap<>();
    }

    public void addKpi(String key, Object value) {
        kpis.put(key, value);
    }

    public void writeOutput() throws IOException {
        writeKpis();
        logger.info("solution processing completed.");
    }

    private void writeKpis() throws IOException {
        TreeMap<String, Object> allKpis = new TreeMap<>();
        allKpis.put("input", getInputKpis());
        allKpis.put("output", kpis);

        String kpiFileName = "solution/kpis.yaml";
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
