package com.stochastic.registry;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Network;
import com.stochastic.network.Path;

import java.util.ArrayList;
import java.util.HashMap;

public class DataRegistry {
    /**
     * Holds all airline objects built from input data.
     */

    private Integer maxLegDelayInMin;
    private ArrayList<Leg> legs;
    private ArrayList<Tail> tails;
    private HashMap<Integer, Path> tailHashMap;
    private Network network;

    // This is the updated number of scenarios after duplicate random delays have been removed.
    private Integer numScenarios;

    public DataRegistry() {
        legs = new ArrayList<>();
        tails = new ArrayList<>();
    }
    
    public HashMap<Integer, Path> getTailHashMap() {
		return tailHashMap;
	}

	public void setTailHashMap(HashMap<Integer, Path> tailHashMap) {
		this.tailHashMap = tailHashMap;
	}

	public ArrayList<Leg> getLegs() {
        return legs;
    }

    public void setMaxLegDelayInMin(Integer maxLegDelayInMin) {
        this.maxLegDelayInMin = maxLegDelayInMin;
    }

    public Integer getMaxLegDelayInMin() {
        return maxLegDelayInMin;
    }

    public void setLegs(ArrayList<Leg> legs) {
        this.legs = legs;
    }

    public void setTails(ArrayList<Tail> tails) {
        this.tails = tails;
    }

    public ArrayList<Tail> getTails() {
        return tails;
    }

    public Tail getTail(Integer id) {
    	for(Tail t: tails)
    		if(t.getId().equals(id))
    			return t;
    	
        return null;
    }

    public void setNumScenarios(Integer numScenarios) {
        this.numScenarios = numScenarios;
    }

    public Integer getNumScenarios() {
        return numScenarios;
    }

    public void buildConnectionNetwork() {
        network = new Network(legs);
    }

    public Network getNetwork() {
        return network;
    }
}

