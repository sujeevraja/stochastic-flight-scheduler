package com.stochastic.utility;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;

public class CostUtility {
    /**
     * Utility class to calculate all costs for the solver.
     */

    public static Double getAssignCostForLeg(Leg leg, Tail tail) {
        final Integer offPlanPenalty = 50;
        return leg.getOrigTailId().equals(tail.getId())
               ? 0.0
               : offPlanPenalty;
    }
}
