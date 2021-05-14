package stochastic.registry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.delay.DelayGenerator;
import stochastic.delay.Scenario;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Network;
import stochastic.network.Path;
import stochastic.utility.CSVHelper;
import stochastic.utility.OptException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class DataRegistry {
    /**
     * Class that controls the entire solution process from reading data to writing output.
     */
    private final static Logger logger = LogManager.getLogger(DataRegistry.class);
    /**
     * Holds input data.
     */
    private final ArrayList<Leg> legs;
    private final ArrayList<Tail> tails;
    private final HashMap<Integer, Tail> idTailMap;
    private final HashMap<Integer, Path> tailOrigPathMap;
    private final Network network;
    private final int hub;

    private DelayGenerator delayGenerator;
    private Scenario[] delayScenarios;
    private int rescheduleTimeBudget;

    public DataRegistry(ArrayList<Leg> legs, ArrayList<Tail> tails, HashMap<Integer, Path> tailOrigPathMap, int hub) {
        this.legs = legs;
        network = new Network(legs);

        this.tails = tails;
        idTailMap = new HashMap<>();
        for (Tail tail : tails)
            idTailMap.put(tail.getId(), tail);

        this.tailOrigPathMap = tailOrigPathMap;
        this.hub = hub;
    }

    public HashMap<Integer, Path> getTailOrigPathMap() {
        return tailOrigPathMap;
    }

    public ArrayList<Leg> getLegs() {
        return legs;
    }

    public ArrayList<Tail> getTails() {
        return tails;
    }

    public HashMap<Integer, Tail> getIdTailMap() {
        return idTailMap;
    }

    public Network getNetwork() {
        return network;
    }

    public int getHub() { return hub; }

    public void setDelayGenerator(DelayGenerator delayGenerator) {
        this.delayGenerator = delayGenerator;
    }

    public DelayGenerator getDelayGenerator() {
        return delayGenerator;
    }

    public void setDelayScenarios(Scenario[] delayScenarios) {
        this.delayScenarios = delayScenarios;
    }

    public Scenario[] getDelayScenarios() {
        return delayScenarios;
    }

    public void setRescheduleTimeBudget(int rescheduleTimeBudget) {
        this.rescheduleTimeBudget = rescheduleTimeBudget;
    }

    public int getRescheduleTimeBudget() {
        return rescheduleTimeBudget;
    }

    public void parsePrimaryDelaysFromFiles() throws OptException {
        logger.info("staring primary delay parsing...");
        final int numScenarios = Parameters.getNumSecondStageScenarios();
        final double probability = 1.0 / numScenarios;
        Scenario[] scenarios = new Scenario[numScenarios];
        final String prefix = Parameters.getOutputPath() + "/primary_delays_";
        final String suffix = ".csv";

        // Build map from leg id to index for delay data collection.
        Map<Integer, Integer> legIdIndexMap = new HashMap<>();
        for (int i = 0; i < legs.size(); ++i)
            legIdIndexMap.put(legs.get(i).getId(), i);

        double avgTotalPrimaryDelay = 0.0;

        for (int i = 0; i < numScenarios; ++i) {
            // read delay data
            String filePath = prefix + i + suffix;
            Map<Integer, Integer> primaryDelayMap = new HashMap<>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(filePath));
                CSVHelper.parseLine(reader); // parse once to skip headers

                while (true) {
                    List<String> line = CSVHelper.parseLine(reader);
                    if (line == null)
                        break;

                    int legId = Integer.parseInt(line.get(0));
                    int primaryDelay = Integer.parseInt(line.get(1));
                    primaryDelayMap.put(legId, primaryDelay);
                }

                reader.close();
            } catch (IOException ex) {
                logger.error(ex);
                throw new OptException("error reading primary delays from file");
            }

            // build scenario, store it
            int[] delays = new int[legs.size()];
            Arrays.fill(delays, 0);
            for (Map.Entry<Integer, Integer> entry : primaryDelayMap.entrySet())
                delays[legIdIndexMap.get(entry.getKey())] = entry.getValue();

            scenarios[i] = new Scenario(probability, delays);
            avgTotalPrimaryDelay += scenarios[i].getTotalPrimaryDelay();
        }

        delayScenarios = scenarios;

        avgTotalPrimaryDelay /= scenarios.length;
        logger.info("average total primary delay (minutes): " + avgTotalPrimaryDelay);

        rescheduleTimeBudget = (int) Math.round(
            avgTotalPrimaryDelay * Parameters.getRescheduleBudgetFraction());
        logger.info("completed primary delay parsing.");
    }

    /**
     * Generates delay realizations and probabilities for second stage scenarios.
     */
    public void buildScenariosFromDistribution(int numScenarios) {
        delayScenarios = delayGenerator.generateScenarios(numScenarios);

        double avgTotalPrimaryDelay = 0.0;
        for (Scenario s : delayScenarios)
            avgTotalPrimaryDelay += s.getTotalPrimaryDelay();
        avgTotalPrimaryDelay /= delayScenarios.length;

        logger.info("average total primary delay (minutes): " + avgTotalPrimaryDelay);

        rescheduleTimeBudget = (int) Math.round(
            avgTotalPrimaryDelay * Parameters.getRescheduleBudgetFraction());
    }
}
