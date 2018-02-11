package com.stochastic.network;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.utility.CostUtility;
import java.util.ArrayList;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Path {
    /**
     * Represents a column for the model.
     * Holds the tail for which it was generated and a list of legs.
     */
    private final static Logger logger = LogManager.getLogger(Path.class);
    private Tail tail;
    private ArrayList<Leg> legs;
    private ArrayList<Integer> delayTimesInMin;
    private double cost;

    Path(Tail tail) {
        this.tail = tail;
        legs = new ArrayList<>();
        delayTimesInMin = new ArrayList<>();
        cost = 0.0;
    }

    void addLeg(Leg leg, Integer delayTimeInMin) {
        legs.add(leg);
        delayTimesInMin.add(delayTimeInMin);
        cost += CostUtility.getAssignCostForLegToTail(leg, tail, delayTimeInMin);
    }

    public Tail getTail() {
        return tail;
    }

    public ArrayList<Leg> getLegs() {
        return legs;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public double getCost() {
        return cost;
    }

    public void print() {
        logger.info("Path for tail " + tail.getId());
        for(int i = 0; i < legs.size(); ++i) {
            logger.info(legs.get(i));
            logger.info("delay time in minutes: " + delayTimesInMin.get(i));
        }
    }
}
