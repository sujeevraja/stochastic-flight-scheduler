package stochastic.solver;

import stochastic.utility.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

class BendersCut {
    /**
     * Benders cut holds coefficients and RHS of feasibility cuts that will be added to the Benders master problem.
     * Given the first-stage variables x, cut coefficents \beta and cut RHS \alpha, the cut will be
     *
     * \beta x + \theta \geq \alpha
     */
    private final static Logger logger = LogManager.getLogger(BendersCut.class);
    private double alpha;
    private double[][] beta;

    BendersCut(double alpha, int dim1, int dim2) {
        this.alpha = alpha;
        this.beta = new double[dim1][];
        for (int i = 0; i < dim1; ++i) {
            beta[i] = new double[dim2];
            Arrays.fill(beta[i], 0.0);
        }
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

    /**
     * Check if the given master solution is cut off by the current cut.
     * @param x master problem reschedule solution values
     * @param theta benders theta
     * @return true if cut separates the solution, fales otherwise
     */
    boolean separates(double[][] x, double theta) {
        double lhs = theta;
        for (int i = 0; i < beta.length; ++i) {
            for (int j = 0; j < beta[i].length; ++j) {
                lhs += beta[i][j] * x[i][j];
            }
        }

        logger.debug("lhs: " + lhs + " rhs: " + alpha + " violation: " + (lhs - alpha));
        return lhs <= alpha - Constants.MINIMUM_CUT_VIOLATION;
   }

   void clear() {
        alpha = 0.0;
        for (double[] row : beta)
            Arrays.fill(row, 0.0);
   }
}
