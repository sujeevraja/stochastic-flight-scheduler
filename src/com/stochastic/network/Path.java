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
    private static int pathCounter = 0;
    private int index;
    
	public Path(Tail tail) {
        this.tail = tail;
        legs = new ArrayList<>();
        delayTimesInMin = new ArrayList<>();
        cost = 0.0;
        index = pathCounter;
        pathCounter++;
    }

    @Override
    public String toString() {
	    StringBuilder pathStr = new StringBuilder();
	    pathStr.append(index);
	    pathStr.append(": ");
	    if (legs.isEmpty()) {
            pathStr.append("empty");
        }
        else {
	        pathStr.append(legs.get(0).getId());
	        for(int i = 1; i < legs.size(); ++i) {
	            pathStr.append(" -> ");
	            pathStr.append(legs.get(i).getId());
            }
        }
        return pathStr.toString();
    }

    public boolean equals(Path other) {
	    if (legs.size() != other.legs.size())
	        return false;

	    for (int i = 0; i < legs.size(); ++i) {
	        if (!legs.get(i).getId().equals(other.legs.get(i).getId()))
	            return false;

	        if (!delayTimesInMin.get(i).equals(other.delayTimesInMin.get(i)))
	            return false;
        }

        return true;
    }

    public static void resetPathCounter() {
	    pathCounter = 0;
    }

    public void addLeg(Leg leg, Integer delayTimeInMin) {
        legs.add(leg);
        
        if(delayTimeInMin == null)
        	delayTimeInMin = 0;
        
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

    public int getIndex() {
		return index;
	}    

	public void setIndex(int index) {
		this.index = index;
	}

	public ArrayList<Integer> getDelayTimesInMin() {
		return delayTimesInMin;
	}

	public void setDelayTimesInMin(ArrayList<Integer> delayTimesInMin) {
		this.delayTimesInMin = delayTimesInMin;
	}

	public void print() {
        logger.info("Path for tail " + tail.getId());
        for(int i = 0; i < legs.size(); ++i) {
            logger.info(legs.get(i));
            logger.info("delay time in minutes: " + delayTimesInMin.get(i));
        }
    }
	
}
