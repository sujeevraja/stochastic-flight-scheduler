package com.stochastic.solver;

class BendersData {
    private double upperBound;
    private double alpha;
    private double[][] beta;

    BendersData(double upperBound, double alpha, double[][] beta) {
        this.upperBound = upperBound;
        this.alpha = alpha;
        this.beta = beta;
    }

    void setUpperBound(double upperBound) {
        this.upperBound = upperBound;
    }

    double getUpperBound() {
        return upperBound;
    }

    void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    double getAlpha() {
        return alpha;
    }

    double[][] getBeta() {
        return beta;
    }
}
