package stochastic.solver;

import stochastic.domain.Leg;
import stochastic.network.Path;
import stochastic.utility.Constants;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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

    Dual(int numLegs, int numTails) {
        this.dualsLeg = new double[numLegs];
        this.dualsTail = new double[numTails];
        this.dualsDelay = new double[numLegs];
        Arrays.fill(dualsLeg, 0);
        Arrays.fill(dualsTail, 0);
        Arrays.fill(dualsDelay, 0);
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

    Dual getFeasibleDual(Dual infeasibleDual, HashMap<Integer, ArrayList<Path>> tailPathsMap, ArrayList<Leg> legs) {
        double lambda = 0.0;

        for (Leg leg : legs) {
            double infeasibleDualLegSlack = infeasibleDual.getLegSlack(leg);
            if (infeasibleDualLegSlack >= 0.0)
                continue;

            double lambdaLeg = (-infeasibleDualLegSlack / (getLegSlack(leg) - infeasibleDualLegSlack));
            lambda = Math.max(lambda, lambdaLeg);
        }

        for (Map.Entry<Integer, ArrayList<Path>> entry : tailPathsMap.entrySet()) {
            ArrayList<Path> paths = entry.getValue();
            for (Path path : paths) {
                double infeasDualPathSlack = infeasibleDual.getPathSlack(path);
                if (infeasDualPathSlack >= 0.0)
                    continue;

                double lambdaPath = (-infeasDualPathSlack / (getPathSlack(path) - infeasDualPathSlack));
                lambda = Math.max(lambda, lambdaPath);
            }
        }

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

    private double getLegSlack(Leg leg) {
        return leg.getDelayCostPerMin() + dualsDelay[leg.getIndex()];
    }

    private static double[] getConvexCombination(double[] arr1, double[] arr2, double lambda) {
        double[] comb = new double[arr1.length];
        for (int i = 0; i < arr1.length; ++i)
            comb[i] = (lambda * arr1[i]) + ((1-lambda) * arr2[i]);
        return comb;
    }
}
