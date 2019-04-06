package stochastic.utility;

import stochastic.domain.Leg;
import stochastic.domain.Tail;

public class CostUtility {
    /**
     * Utility class to calculate all costs for the solver.
     */

    private static final double onPlanBonus = 1;
    private static final double offPlanPenalty = 1;
    private static final double delayCostPerMin = 10;
    private static final double excessiveDelayCostPerMin = 20;

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
