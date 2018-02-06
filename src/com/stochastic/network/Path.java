package com.stochastic.network;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.utility.CostUtility;

import java.util.ArrayList;

public class Path {
    /**
     * Represents a column for the model.
     * Holds the tail for which it was generated and a list of legs.
     */
    private Tail tail;
    private ArrayList<Leg> legs;
    private Double cost;

    Path(Tail tail) {
        this.tail = tail;
        legs = new ArrayList<>();
        cost = 0.0;
    }

    void addLeg(Leg leg) {
        legs.add(leg);
        cost += CostUtility.getAssignCostForLeg(leg, tail);
    }
}
