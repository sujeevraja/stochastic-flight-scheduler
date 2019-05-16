package stochastic.solver;

import java.util.ArrayList;

public class BendersData {
    /**
     * BendersData objects store cuts and updated upper bounds from second stage solutions.
     */
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

    BendersCut getCut(int cutIndex) {
        return cuts.get(cutIndex);
    }
}
