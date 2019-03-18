package com.stochastic.registry;

import com.stochastic.domain.Equipment;
import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class DataRegistry {
    /**
     * Holds all airline objects built from input data.
     */

    private Integer maxLegDelayInMin;
    private Equipment equipment;
    private ArrayList<Leg> legs;
    private ArrayList<Tail> tails;
    private HashMap<Integer, Path> tailHashMap;
    private LocalDateTime maxEndTime;

    // This is the updated number of scenarios after duplicate random delays have been removed.
    private Integer numScenarios;

    public DataRegistry() {
        equipment = null;
        legs = new ArrayList<>();
        tails = new ArrayList<>();
        maxEndTime = null;
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

    public void setEquipment(Equipment equipment) {
        this.equipment = equipment;
    }

    public Equipment getEquipment() {
        return equipment;
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

    public void setMaxEndTime(LocalDateTime maxEndTime) {
        this.maxEndTime = maxEndTime;
    }

    public LocalDateTime getMaxEndTime() {
        return maxEndTime;
    }

    public void setNumScenarios(Integer numScenarios) {
        this.numScenarios = numScenarios;
    }

    public Integer getNumScenarios() {
        return numScenarios;
    }
}

