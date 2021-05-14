package stochastic.output;

import stochastic.domain.Leg;
import stochastic.registry.Parameters;
import stochastic.utility.CSVHelper;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class RescheduleSolution {
    /**
     * RescheduleSolution objects store solutions of the master problem that reschedule legs to
     * protect against uncertain delays.
     */
    private final String name;
    private final double rescheduleCost;
    private final int[] reschedules;
    private boolean isOriginalSchedule;

    public RescheduleSolution(String name, double rescheduleCost, int[] reschedules) {
        this.name = name;
        this.rescheduleCost = rescheduleCost;
        this.reschedules = reschedules;
        isOriginalSchedule = false;
    }

    public String getName() {
        return name;
    }

    public double getRescheduleCost() {
        return rescheduleCost;
    }

    public int[] getReschedules() {
        return reschedules;
    }

    public void setOriginalSchedule(boolean originalSchedule) {
        isOriginalSchedule = originalSchedule;
    }

    public boolean isOriginalSchedule() {
        return isOriginalSchedule;
    }

    /**
     * Writes stored solution to a file with the given path.
     */
    public void writeCSV(ArrayList<Leg> legs) throws IOException {
        String path = Parameters.getOutputPath() + "/reschedule_solution_" + name + ".csv";
        BufferedWriter writer = new BufferedWriter(new FileWriter(path));
        ArrayList<String> headers = new ArrayList<>(Arrays.asList("leg_id", "flt_num", "reschedule"));
        CSVHelper.writeLine(writer, headers);

        for (int i = 0; i < reschedules.length; ++i) {
            Leg leg = legs.get(i);
            ArrayList<String> row = new ArrayList<>(Arrays.asList(
                    Integer.toString(leg.getId()),
                    Integer.toString(leg.getFltNum()),
                    Integer.toString(reschedules[i])));
            CSVHelper.writeLine(writer, row);
        }
        writer.close();
    }
}
