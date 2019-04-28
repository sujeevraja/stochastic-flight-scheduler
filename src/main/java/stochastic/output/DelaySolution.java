package stochastic.output;

import stochastic.domain.Leg;
import stochastic.utility.CSVHelper;
import stochastic.utility.Enums;

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
    private TestKPISet testKPISet;
    private int[] primaryDelays;
    private int[] totalDelays;
    private int[] propagatedDelays;
    private int[] excessDelays;

    public DelaySolution(double delayCost, int[] primaryDelays, int[] totalDelays, int[] propagatedDelays,
                         double[] recourseDelays) {
        testKPISet = new TestKPISet();
        testKPISet.setKpi(Enums.TestKPI.delayCost, delayCost);
        this.primaryDelays = primaryDelays;

        this.totalDelays = totalDelays;
        double sumTotalDelay = 0;
        double maxTotalDelay = 0;
        for (int delay : totalDelays) {
            sumTotalDelay += delay;
            if (maxTotalDelay < delay)
                maxTotalDelay = delay;
        }
        testKPISet.setKpi(Enums.TestKPI.totalFlightDelay, sumTotalDelay);
        testKPISet.setKpi(Enums.TestKPI.maximumFlightDelay, maxTotalDelay);
        testKPISet.setKpi(Enums.TestKPI.averageFlightDelay, sumTotalDelay / totalDelays.length);

        this.propagatedDelays = propagatedDelays;
        double sumPropagatedDelay = 0;
        double maxPropagatedDelay = 0;
        for (int delay : propagatedDelays) {
            sumPropagatedDelay += delay;
            if (maxPropagatedDelay < delay)
                maxPropagatedDelay = delay;
        }
        testKPISet.setKpi(Enums.TestKPI.totalPropagatedDelay, sumPropagatedDelay);
        testKPISet.setKpi(Enums.TestKPI.maximumExcessDelay, maxPropagatedDelay);
        testKPISet.setKpi(Enums.TestKPI.averagePropagatedDelay, sumPropagatedDelay / propagatedDelays.length);

        excessDelays = new int[recourseDelays.length];
        double sumExcessDelay = 0;
        double maxExcessDelay = 0;
        for (int i = 0; i < excessDelays.length; ++i) {
            excessDelays[i] = (int) Math.round(recourseDelays[i]);
            sumExcessDelay += excessDelays[i];
            if (maxExcessDelay < excessDelays[i])
                maxExcessDelay = excessDelays[i];
        }
        testKPISet.setKpi(Enums.TestKPI.totalExcessDelay, sumExcessDelay);
        testKPISet.setKpi(Enums.TestKPI.maximumExcessDelay, maxExcessDelay);
        testKPISet.setKpi(Enums.TestKPI.averageExcessDelay, sumExcessDelay / excessDelays.length);
    }

    void setSolutionTimeInSeconds(double solutionTimeInSeconds) {
        testKPISet.setKpi(Enums.TestKPI.delaySolutionTimeInSec, solutionTimeInSeconds);
    }

    public TestKPISet getTestKPISet() {
        return testKPISet;
    }

    /**
     * Writes stored solution to a file with the given path.
     *
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
