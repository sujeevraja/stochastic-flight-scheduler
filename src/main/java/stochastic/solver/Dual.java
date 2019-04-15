package stochastic.solver;

import stochastic.domain.Leg;
import stochastic.network.Path;

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

    Dual() {
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

    Dual getFeasibleDual(Dual infeasibleDual, HashMap<Integer, ArrayList<Path>> tailPathsMap, ArrayList<Leg> legs) {
        Dual feasibleDual = new Dual();

        double lambda = 0.0;

        for (Leg leg : legs) {
            double infeasibleDualLegSlack = infeasibleDual.getLegSlack(leg);
            if (infeasibleDualLegSlack >= 0.0)
                continue;

            double lambdaLeg = (-infeasibleDualLegSlack / (feasibleDual.getLegSlack(leg) - infeasibleDualLegSlack));
            lambda = Math.max(lambda, lambdaLeg);
        }

        for (Map.Entry<Integer, ArrayList<Path>> entry : tailPathsMap.entrySet()) {
            ArrayList<Path> paths = entry.getValue();
            for (Path path : paths) {
                double infeasibleDualLegSlack = infeasibleDual.getPathSlack(path);
                if (infeasibleDualLegSlack >= 0.0)
                    continue;

                double lambdaLeg = (-infeasibleDualLegSlack / (feasibleDual.getPathSlack(path) - infeasibleDualLegSlack));
                lambda = Math.max(lambda, lambdaLeg);
            }
        }

        feasibleDual.dualsLeg = getConvexCombination(dualsLeg ,infeasibleDual.dualsLeg, lambda);
        feasibleDual.dualsTail = getConvexCombination(dualsTail, infeasibleDual.dualsTail, lambda);
        feasibleDual.dualsDelay = getConvexCombination(dualsDelay, infeasibleDual.dualsDelay, lambda);
        return feasibleDual;
    }

    private double getPathSlack(Path path) {
        double slack = -dualsTail[path.getTail().getId()];
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
