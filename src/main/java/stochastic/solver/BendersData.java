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


    public void updateAlpha(int cutNum, double scenAlpha, double probability) {
        BendersCut cut = cuts.get(cutNum);
        cut.setAlpha(cut.getAlpha() + (scenAlpha * probability));
    }

    public void updateBeta(int cutNum, double[] dualsDelay, double probability,
                           Double dualRisk) {
        double[] beta = cuts.get(cutNum).getBeta();
        for (int j = 0; j < dualsDelay.length; j++) {
            if (Math.abs(dualsDelay[j]) >= Constants.EPS)
                beta[j] += (-dualsDelay[j] * probability);

            if (dualRisk != null && Math.abs(dualRisk) >= Constants.EPS)
                beta[j] += (dualRisk * probability);
        }
    }
}
