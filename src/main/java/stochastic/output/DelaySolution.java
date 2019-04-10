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

    private int totalDelaySum;
    private int propagatedDelaySum;
    private int excessDelaySum;
    private double solutionTimeInSeconds;

    public DelaySolution(double delayCost, int[] primaryDelays, int[] totalDelays, int[] propagatedDelays,
                         double[] recourseDelays) {
        this.delayCost = delayCost;
        this.primaryDelays = primaryDelays;
        this.totalDelays = totalDelays;
        this.propagatedDelays = propagatedDelays;

        excessDelays = new int[recourseDelays.length];
        for (int i = 0; i < excessDelays.length; ++i)
            excessDelays[i] = (int) Math.round(recourseDelays[i]);

        totalDelaySum = Arrays.stream(totalDelays).sum();
        propagatedDelaySum = Arrays.stream(propagatedDelays).sum();
        excessDelaySum = Arrays.stream(excessDelays).sum();
    }

    double getDelayCost() {
        return delayCost;
    }

    int getTotalDelaySum() {
        return totalDelaySum;
    }

    int getPropagatedDelaySum() {
        return propagatedDelaySum;
    }

    int getExcessDelaySum() {
        return excessDelaySum;
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
