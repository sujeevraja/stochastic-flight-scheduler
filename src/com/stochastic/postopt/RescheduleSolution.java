package com.stochastic.postopt;

import com.stochastic.domain.Leg;
import com.stochastic.utility.CSVHelper;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class RescheduleSolution {
    /**
     * RescheduleSolution objects store solutions of the master problem that reschedule legs to protect against
     * uncertain delays.
     */
    private String name;
    private double rescheduleCost;
    private int[] reschedules;

    public RescheduleSolution(String name, double rescheduleCost, int[] reschedules) {
        this.name = name;
        this.rescheduleCost = rescheduleCost;
        this.reschedules = reschedules;
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

    /**
     * Writes stored solution to a file with the given path.
     * @param path path to file (assumed to be csv).
     */
    public void writeCSV(String path, ArrayList<Leg> legs) throws IOException {
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
