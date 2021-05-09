package stochastic.utility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class Util {
    private final static Logger logger = LogManager.getLogger(Util.class);

    public static void writeToYaml(Object o, String filePath) throws OptException {
        try {
            BufferedWriter kpiWriter = new BufferedWriter(new FileWriter(filePath));
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);
            yaml.dump(o, kpiWriter);
            kpiWriter.close();
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to YAML");
        }
    }
}
