package stochastic.solver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.utility.Constants;

import java.util.Arrays;

class BendersCut {
    /**
     * Benders cut holds coefficients and RHS of feasibility cuts that will be added to the Benders master problem.
     * Given the first-stage variables x, cut coefficents \beta and cut RHS \alpha, the cut will be
     * <p>
     * \beta x + \theta \geq \alpha
     */
    private final static Logger logger = LogManager.getLogger(BendersCut.class);
    private double alpha;
    private double[] beta;

    BendersCut(double alpha, int dim) {
        this.alpha = alpha;
        this.beta = new double[dim];
        Arrays.fill(beta, 0.0);
    }

    void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    double getAlpha() {
        return alpha;
    }

    double[] getBeta() {
        return beta;
    }

    /**
     * Check if the given master solution is cut off by the current cut.
     *
     * @param x     master problem reschedule solution values
     * @param theta benders theta
     * @return true if cut separates the solution, fales otherwise
     */
    boolean separates(double[] x, double theta) {
        double lhs = theta;
        for (int i = 0; i < beta.length; ++i) {
            lhs += beta[i] * x[i];
        }

        logger.debug("lhs: " + lhs + " rhs: " + alpha + " violation: " + (lhs - alpha));
        return lhs <= alpha - Constants.MINIMUM_CUT_VIOLATION;
    }
}
