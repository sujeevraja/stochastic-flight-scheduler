package com.stochastic.utility;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;

public class CostUtility {
    /**
     * Utility class to calculate all costs for the solver.
     */

    // objective: minimize path costs
    // path types:
    // - empty path: no path is empty as tails are allowed to not have any path.
    // - path with at least 1 on-plan leg: reduce slightly, say 1 per flight.
    // - path with at least 1 off-plan leg: increase slightly, say 1 per flight
    // - path with at least 1 delayed leg (need to account for OTP):
    //   - delay <= 14 minutes: increase by 10 per minute.
    //   - delay > 14 minutes: increase by 20 per minute.

    private static final double onPlanBonus = 1;
    private static final double offPlanPenalty = 1;
    private static final double delayCostPerMin = 10;
    private static final double excessiveDelayCostPerMin = 20;
    private static final double legCancelCost = 1000.0;

    public static double getLegCancelCost() {
        return legCancelCost;
    }

    public static double getAssignCostForLegToTail(Leg leg, Tail tail, Integer delayTimeInMin) {
        double cost = 0;

        if(leg.getOrigTailId().equals(tail.getId()))
            cost -= onPlanBonus;
        else
            cost += offPlanPenalty;

        if(delayTimeInMin <= 14)
            cost += delayCostPerMin * delayTimeInMin;
        else
            cost += excessiveDelayCostPerMin * delayTimeInMin;

        return cost;
    }
}
