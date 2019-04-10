package stochastic.output;

import java.util.Arrays;

class ComparableStats {
    /**
     * ComparableStats objects hold combined statistics of a given reschedule solution and a delay solution.
     * Improvements over other ComparableStats objects can also be calculated and provided.
     */

    double[] values;
    double[] percentageDecreases;

    ComparableStats(RescheduleSolution rescheduleSolution, DelaySolution delaySolution) {
        values = new double[]{
                delaySolution.getDelayCost(),
                rescheduleSolution.getRescheduleCost() + delaySolution.getDelayCost(),
                (double) delaySolution.getTotalDelaySum(),
                (double) delaySolution.getPropagatedDelaySum(),
                (double) delaySolution.getExcessDelaySum(),
        };
        percentageDecreases = new double[values.length];
        Arrays.fill(percentageDecreases, 0.0);
    }

    void setPercentageDecreases(ComparableStats other) {
        for (int i = 0;  i < values.length;  ++i) {
            percentageDecreases[i] = (other.values[i] - values[i]) * 100.0 / other.values[i];
        }
    }

    public double[] getValues() {
        return values;
    }

    public double[] getPercentageDecreases() {
        return percentageDecreases;
    }
}
