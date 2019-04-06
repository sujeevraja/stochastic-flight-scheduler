package stochastic.delay;

import java.util.HashMap;

public class Scenario {
    /**
     * Scenario holds probability and primary leg delay data for a specific second-stage realization.
     */
    private double probability;
    private HashMap<Integer, Integer> primaryDelays; // keys are leg indices, values are delays.

    Scenario(double probability, HashMap<Integer, Integer> primaryDelays) {
        this.probability = probability;
        this.primaryDelays = primaryDelays;
    }

    public double getProbability() {
        return probability;
    }

    public HashMap<Integer, Integer> getPrimaryDelays() {
        return primaryDelays;
    }
}
