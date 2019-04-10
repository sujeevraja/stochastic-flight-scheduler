package stochastic.delay;

import java.util.HashMap;
import java.util.Map;

public class Scenario {
    /**
     * Scenario holds probability and primary leg delay data for a specific second-stage realization.
     */
    private double probability;
    private HashMap<Integer, Integer> primaryDelays; // keys are leg indices, values are delays.
    private double totalPrimaryDelay;

    Scenario(double probability, HashMap<Integer, Integer> primaryDelays) {
        this.probability = probability;
        this.primaryDelays = primaryDelays;
        this.totalPrimaryDelay = 0.0;

        for (Map.Entry<Integer, Integer> entry : primaryDelays.entrySet())
            totalPrimaryDelay += entry.getValue();
    }

    public double getProbability() {
        return probability;
    }

    public HashMap<Integer, Integer> getPrimaryDelays() {
        return primaryDelays;
    }

    public double getTotalPrimaryDelay() {
        return totalPrimaryDelay;
    }
}
