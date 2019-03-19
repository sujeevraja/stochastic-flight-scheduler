package com.stochastic.solver;

class LabelPathGenerator {
    /**
     * LabelPathGenerator uses label setting and partial path pruning to generate routes for the second-stage
     * model.
     */

    private double[] tailCoverDuals; // \alpha in paper, free
    private double[] legCoverDuals; // \beta in paper, free
    private double[] delayLinkDuals; // \gamma in paper, <= 0

    LabelPathGenerator(double[] tailCoverDuals, double[] legCoverDuals, double[] delayLinkDuals) {
        this.tailCoverDuals = tailCoverDuals;
        this.legCoverDuals = legCoverDuals;
        this.delayLinkDuals = delayLinkDuals;
    }

    void generatePaths() {
        // TODO
    }

    boolean secondStageOptimal() {
        return false;
    }
}
