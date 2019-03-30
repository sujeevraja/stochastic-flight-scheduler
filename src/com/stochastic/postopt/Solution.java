package com.stochastic.postopt;

import com.stochastic.domain.Leg;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class Solution {
    /**
     * Solution objects store solutions of the master problem.
     */
    private double objective;
    private ArrayList<Leg> legs;
    private int[] reschedules;
    private Double thetaValue; // only applies to Benders solutions.

    public Solution(double objective, ArrayList<Leg> legs, int[] reschedules) {
        this.objective = objective;
        this.legs = legs;
        this.reschedules = reschedules;
        this.thetaValue = null;
    }

    /**
     * Writes stored solution to a file with the given path.
     * @param path path to file (assumed to be csv).
     */
    public void writeCSV(String path) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(path));
        writer.write("leg_id,flt_num,reschedule\n");
        for (int i = 0; i < reschedules.length; ++i) {
            Leg leg = legs.get(i);
            String row = leg.getId() + "," + leg.getFltNum() + "," + reschedules[i] + "\n";
            writer.write(row);
        }
        writer.close();
    }

    public double getObjective() {
        return objective;
    }

    public void setThetaValue(Double thetaValue) {
        this.thetaValue = thetaValue;
    }

    public Double getThetaValue() {
        return thetaValue;
    }
}
