package com.stochastic.solver;

import java.util.Arrays;

public class BendersCut {
    /**
     * Benders cut holds coefficients and RHS of feasibility cuts that will be added to the Benders master problem.
     * Given the first-stage variables x, cut coefficents \beta and cut RHS \alpha, the cut will be
     *
     * \beta x + \theta \geq \alpha
     */

    private double alpha;
    private double[][] beta;

    BendersCut(double alpha, int dim1, int dim2) {
        this.alpha = alpha;
        this.beta = new double[dim1][];
        for (double[] arr : beta) {
            arr = new double[dim2];
            Arrays.fill(arr, 0.0);
        }
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public double getAlpha() {
        return alpha;
    }

    public double[][] getBeta() {
        return beta;
    }
}
