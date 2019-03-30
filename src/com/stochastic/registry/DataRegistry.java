package com.stochastic.registry;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Network;
import com.stochastic.network.Path;

import java.util.ArrayList;
import java.util.HashMap;

public class DataRegistry {
    /**
     * Holds input data.
     */
    private ArrayList<Leg> legs;
    private ArrayList<Tail> tails;
    private HashMap<Integer, Path> tailHashMap;
    private Network network;

    private Integer numScenarios;
    private int[] scenarioDelays;
    private double[] scenarioProbabilities;

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

    public void buildConnectionNetwork() {
        network = new Network(legs);
    }

    public Network getNetwork() {
        return network;
    }

    public void setNumScenarios(Integer numScenarios) {
        this.numScenarios = numScenarios;
    }

    public Integer getNumScenarios() {
        return numScenarios;
    }

    public void setScenarioDelays(int[] scenarioDelays) {
        this.scenarioDelays = scenarioDelays;
    }

    public int[] getScenarioDelays() {
        return scenarioDelays;
    }

    public void setScenarioProbabilities(double[] scenarioProbabilities) {
        this.scenarioProbabilities = scenarioProbabilities;
    }

    public double[] getScenarioProbabilities() {
        return scenarioProbabilities;
    }
}

