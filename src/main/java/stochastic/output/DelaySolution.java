package stochastic.output;

import stochastic.domain.Leg;
import stochastic.utility.CSVHelper;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class DelaySolution {
    /**
     * DelaySolution objects store solutions to second-stage problems solved as MIPs. These objects capture quantities
     * like total delay and total propagated delay.
     */
    private double delayCost;
    private int[] primaryDelays;
    private int[] totalDelays;
    private int[] propagatedDelays;
    private int[] excessDelays;

    private int sumTotalDelay;
    private int maxTotalDelay;
    private double avgTotalDelay;

    private int sumPropagatedDelay;
    private int maxPropagatedDelay;
    private double avgPropagatedDelay;

    private int sumExcessDelay;
    private int maxExcessDelay;
    private double avgExcessDelay;

    private double solutionTimeInSeconds;

    public DelaySolution(double delayCost, int[] primaryDelays, int[] totalDelays, int[] propagatedDelays,
                         double[] recourseDelays) {
        this.delayCost = delayCost;
        this.primaryDelays = primaryDelays;

        this.totalDelays = totalDelays;
        sumTotalDelay = 0;
        maxTotalDelay = 0;
        for(int delay : totalDelays) {
            sumTotalDelay += delay;
            if (maxTotalDelay < delay)
                maxTotalDelay = delay;
        }
        avgTotalDelay = ((double) sumTotalDelay) / totalDelays.length;


        this.propagatedDelays = propagatedDelays;
        for(int delay : propagatedDelays) {
            sumPropagatedDelay += delay;
            if (maxPropagatedDelay < delay)
                maxPropagatedDelay = delay;
        }
        avgPropagatedDelay = ((double) sumPropagatedDelay) / propagatedDelays.length;

        excessDelays = new int[recourseDelays.length];
        sumExcessDelay = 0;
        maxExcessDelay = 0;
        for (int i = 0; i < excessDelays.length; ++i) {
            excessDelays[i] = (int) Math.round(recourseDelays[i]);
            sumExcessDelay += excessDelays[i];
            if (maxExcessDelay < excessDelays[i])
                maxExcessDelay = excessDelays[i];
        }
        avgExcessDelay = ((double) sumExcessDelay) / excessDelays.length;
    }

    double getDelayCost() {
        return delayCost;
    }

    int getSumTotalDelay() {
        return sumTotalDelay;
    }

    int getMaxTotalDelay() {
        return maxTotalDelay;
    }

    double getAvgTotalDelay() {
        return avgTotalDelay;
    }

    int getSumPropagatedDelay() {
        return sumPropagatedDelay;
    }

    int getMaxPropagatedDelay() {
        return maxPropagatedDelay;
    }

    double getAvgPropagatedDelay() {
        return avgPropagatedDelay;
    }

    int getSumExcessDelay() {
        return sumExcessDelay;
    }

    int getMaxExcessDelay() {
        return maxExcessDelay;
    }

    double getAvgExcessDelay() {
        return avgExcessDelay;
    }

    void setSolutionTimeInSeconds(double solutionTimeInSeconds) {
        this.solutionTimeInSeconds = solutionTimeInSeconds;
    }

    double getSolutionTimeInSeconds() {
        return solutionTimeInSeconds;
    }

    /**
     * Writes stored solution to a file with the given path.
     * @param path path to file (assumed to be csv).
     */
    void writeCSV(String path, ArrayList<Leg> legs) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(path));
        ArrayList<String> headers = new ArrayList<>(Arrays.asList("leg_id", "flt_num",
            "primary_delay", "total_delay", "propagated_delay", "excess_delay"));
        CSVHelper.writeLine(writer, headers);

        for (int i = 0; i < legs.size(); ++i) {
            Leg leg = legs.get(i);
            ArrayList<String> row = new ArrayList<>(Arrays.asList(
                    Integer.toString(leg.getId()),
                    Integer.toString(leg.getFltNum()),
                    Integer.toString(primaryDelays[i]),
                    Integer.toString(totalDelays[i]),
                    Integer.toString(propagatedDelays[i]),
                    Integer.toString(excessDelays[i])));
            CSVHelper.writeLine(writer, row);
        }
        writer.close();
    }
}
