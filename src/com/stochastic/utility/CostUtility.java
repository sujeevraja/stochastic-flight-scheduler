package com.stochastic.utility;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;

public class CostUtility {
    /**
     * Utility class to calculate all costs for the solver.
     */
    private static final double emptyPathCost = -5000.0;
    private static final double legCancelCost = 1000.0;

    public static double getEmptyPathCost() {
        return emptyPathCost;
    }

    public static double getLegCancelCost() {
        return legCancelCost;
    }

    public static double getAssignCostForLegToTail(Leg leg, Tail tail) {
        final double offPlanAssignBonus = 50.0;
        final double onPlanAssignBonus = 75.0;

        return leg.getOrigTailId().equals(tail.getId())
               ? onPlanAssignBonus
               : offPlanAssignBonus;
    }
}
