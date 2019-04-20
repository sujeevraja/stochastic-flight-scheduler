package stochastic.solver;

import stochastic.domain.Leg;
import stochastic.network.Path;
import stochastic.utility.Constants;

import java.util.ArrayList;

class Dual {
    /**
     * Holds dual information of a Benders subproblem.
     */
    private double[] dualsLeg;
    private double[] dualsTail;
    private double[] dualsDelay;

    private Dual() {
        dualsLeg = null;
        dualsTail = null;
        dualsDelay = null;
    }

    Dual(double[] dualsLeg, double[] dualsTail, double[] dualsDelay) {
        this.dualsLeg = dualsLeg;
        this.dualsTail = dualsTail;
        this.dualsDelay = dualsDelay;
    }

    Dual getFeasibleDual(Dual infeasibleDual, double lambda) {
        Dual feasibleDual = new Dual();
        feasibleDual.dualsLeg = getConvexCombination(dualsLeg ,infeasibleDual.dualsLeg, lambda);
        feasibleDual.dualsTail = getConvexCombination(dualsTail, infeasibleDual.dualsTail, lambda);
        feasibleDual.dualsDelay = getConvexCombination(dualsDelay, infeasibleDual.dualsDelay, lambda);
        return feasibleDual;
    }

    BendersCut getBendersCut(double[][] dualsBound, double probability) {
        double scenAlpha = 0;

        for (int j = 0; j < dualsLeg.length; j++)
            if (Math.abs(dualsLeg[j]) >= Constants.EPS)
                scenAlpha += dualsLeg[j];

        for (int j = 0; j < dualsTail.length; j++)
            if (Math.abs(dualsTail[j]) >= Constants.EPS)
                scenAlpha += dualsTail[j];

        for (int j = 0; j < dualsDelay.length; j++)
            if (Math.abs(dualsDelay[j]) >= Constants.EPS)
                scenAlpha += (dualsDelay[j] * Constants.OTP_TIME_LIMIT_IN_MINUTES);

        for (double[] dualBnd : dualsBound) {
            if (dualBnd != null)
                for (double j : dualBnd)
                    if (Math.abs(j) >= Constants.EPS)
                        scenAlpha += j;
        }

        BendersCut cut = new BendersCut(scenAlpha * probability, dualsLeg.length);

        double[] beta = cut.getBeta();
        for (int j = 0; j < dualsDelay.length; j++) {
            if (Math.abs(dualsDelay[j]) >= Constants.EPS)
                beta[j] -= (dualsDelay[j] * probability);
        }

        return cut;
    }

    double getPathSlack(Path path) {
        double slack = -dualsTail[path.getTail().getIndex()];
        ArrayList<Leg> legs = path.getLegs();
        ArrayList<Integer> delays = path.getDelayTimesInMin();
        for (int i = 0; i < legs.size(); ++i) {
            final int ind = legs.get(i).getIndex();
            slack -= (dualsLeg[ind] + (delays.get(i) * dualsDelay[ind]));
        }

        return  slack;
    }

    double getLegSlack(Leg leg) {
        return leg.getDelayCostPerMin() + dualsDelay[leg.getIndex()];
    }

    private static double[] getConvexCombination(double[] arr1, double[] arr2, double lambda) {
        double[] comb = new double[arr1.length];
        for (int i = 0; i < arr1.length; ++i)
            comb[i] = (lambda * arr1[i]) + ((1-lambda) * arr2[i]);
        return comb;
    }
}
