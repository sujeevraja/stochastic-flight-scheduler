package com.stochastic.registry;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Network;
import com.stochastic.network.Path;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class DataRegistry {
    /**
     * Holds input data.
     */
    private ArrayList<Leg> legs;
    private ArrayList<Tail> tails;
    private HashMap<Integer, Path> tailOrigPathMap;
    private Network network;

    // origSlacks[i][j] = dep_j - (arr_i + turn_i) for connecting flights i, j. All times used are original times
    // i.e. before rescheduling.
    private Integer[][] origSlacks;

    private Integer numScenarios;
    private int[] scenarioDelays;
    private double[] scenarioProbabilities;

    public DataRegistry() {
        legs = new ArrayList<>();
        tails = new ArrayList<>();
    }
    
    public HashMap<Integer, Path> getTailOrigPathMap() {
		return tailOrigPathMap;
	}

	public void setTailOrigPathMap(HashMap<Integer, Path> tailOrigPathMap) {
		this.tailOrigPathMap = tailOrigPathMap;
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

        HashMap<Integer, ArrayList<Integer>> adjList = network.getAdjacencyList();
        int numLegs = legs.size();
        origSlacks = new Integer[numLegs][];

        // store slacks for all connections.
        for(int i = 0; i < numLegs; ++i) {
            origSlacks[i] = new Integer[numLegs];
            Arrays.fill(origSlacks[i], null);

            if (!adjList.containsKey(i))
                continue;

            Leg leg = legs.get(i);
            ArrayList<Integer> neighbors = adjList.get(i);

            for (Integer index : neighbors) {
                Leg nextLeg = legs.get(index);
                int slack = (int) Duration.between(leg.getArrTime(), nextLeg.getDepTime()).toMinutes();
                slack -= leg.getTurnTimeInMin();
                origSlacks[i][index] = slack;
            }
        }
    }

    public Network getNetwork() {
        return network;
    }

    public Integer[][] getOrigSlacks() {
        return origSlacks;
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

