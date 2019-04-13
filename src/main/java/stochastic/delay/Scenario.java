package stochastic.delay;

import java.util.Arrays;

public class Scenario {
    /**
     * Scenario holds probability and primary leg delay data for a specific second-stage realization.
     */
    private double probability;
    private int[] primaryDelays; // keys are leg indices, values are delays.
    private double totalPrimaryDelay;

    Scenario(double probability, int[] primaryDelays) {
        this.probability = probability;
        this.primaryDelays = primaryDelays;
        this.totalPrimaryDelay = Arrays.stream(primaryDelays).sum();
    }

    public double getProbability() {
        return probability;
    }

    public int[] getPrimaryDelays() {
        return primaryDelays;
    }

    public double getTotalPrimaryDelay() {
        return totalPrimaryDelay;
    }
}
