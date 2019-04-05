package com.stochastic.solver;

import java.util.ArrayList;

class BendersData {
    private double upperBound;
    private BendersCut aggregatedCut; // used only in single cut benders
    private ArrayList<BendersCut> cuts; // used during multicut benders

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

    void setAggregatedCut(BendersCut aggregatedCut) {
        this.aggregatedCut = aggregatedCut;
    }

    BendersCut getAggregatedCut() {
        return aggregatedCut;
    }

    void addCut(BendersCut cut) {
        cuts.add(cut);
    }

    ArrayList<BendersCut> getCuts() {
        return cuts;
    }
}
