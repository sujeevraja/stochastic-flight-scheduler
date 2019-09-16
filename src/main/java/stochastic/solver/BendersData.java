package stochastic.solver;

import stochastic.utility.Constants;

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

    public void setUpperBound(double upperBound) {
        this.upperBound = upperBound;
    }

    public double getUpperBound() {
        return upperBound;
    }

    void addCut(BendersCut cut) {
        cuts.add(cut);
    }

    BendersCut getCut(int cutIndex) {
        return cuts.get(cutIndex);
    }

    public void updateAlpha(int cutNum, double alpha, double probability) {
        BendersCut cut = cuts.get(cutNum);
        cut.setAlpha(cut.getAlpha() + (alpha * probability));
    }

    public void updateBeta(int cutNum, double[] beta, double probability) {
        double[] cutBeta = cuts.get(cutNum).getBeta();
        for (int i = 0; i < cutBeta.length; ++i) {
            if (Math.abs(beta[i]) >= Constants.EPS) {
                cutBeta[i] += beta[i] * probability;
            }
        }
    }
}
