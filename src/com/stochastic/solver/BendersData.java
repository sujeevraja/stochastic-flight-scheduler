package com.stochastic.solver;

import java.util.ArrayList;

class BendersData {
    private double upperBound;
    private ArrayList<BendersCut> cuts;

    BendersData(double upperBound) {
        this.upperBound = upperBound;
        cuts = new ArrayList<>();
    }

    void setUpperBound(double upperBound) {
        this.upperBound = upperBound;
    }

    double getUpperBound() {
        return upperBound;
    }

    void addCut(BendersCut cut) {
        cuts.add(cut);
    }

    ArrayList<BendersCut> getCuts() {
        return cuts;
    }

    BendersCut getCut(int cutIndex) {
        return cuts.get(cutIndex);
    }
}
