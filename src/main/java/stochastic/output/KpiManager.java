package stochastic.output;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.OptException;
import stochastic.utility.Util;

import java.util.TreeMap;

/**
 * Used to write input and output KPIs of a single run with multiple models.
 */
public class KpiManager {
    private final static Logger logger = LogManager.getLogger(KpiManager.class);
    private final DataRegistry dataRegistry;
    private final TreeMap<String, Object> kpis;

    public KpiManager(DataRegistry dataRegistry) {
        this.dataRegistry = dataRegistry;
        kpis = new TreeMap<>();
    }

    public void addKpi(String key, Object value) {
        kpis.put(key, value);
    }

    public void writeOutput() throws OptException {
        TreeMap<String, Object> allKpis = new TreeMap<>();
        allKpis.put("input", getInputKpis());
        allKpis.put("output", kpis);
        Util.writeToYaml(allKpis, Parameters.getOutputPath() + "/kpis.yaml");
        logger.info("solution processing completed.");
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
