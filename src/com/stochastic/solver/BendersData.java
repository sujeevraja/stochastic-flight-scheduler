package com.stochastic.solver;

public class BendersData {
    private double upperBound;
    private double alpha;
    private double[][] beta;

    BendersData(double upperBound, double alpha, double[][] beta) {
        this.upperBound = upperBound;
        this.alpha = alpha;
        this.beta = beta;
    }

    public void setUpperBound(double upperBound) {
        this.upperBound = upperBound;
    }

    public double getUpperBound() {
        return upperBound;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setBeta(double[][] beta) {
        this.beta = beta;
    }

    public double[][] getBeta() {
        return beta;
    }
}
