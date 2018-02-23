package com.stochastic.registry;

import com.stochastic.domain.Equipment;
import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class DataRegistry {
    /**
     * Holds all airline objects built from input data.
     */

    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private Integer maxLegDelayInMin;
    private Equipment equipment;
    private ArrayList<Leg> legs;
    private ArrayList<Tail> tails;
    private ArrayList<Integer> durations;    
    
    public DataRegistry() {
        windowStart = null;
        windowEnd = null;
        equipment = null;
        legs = new ArrayList<>();
        tails = new ArrayList<>();
        durations = new ArrayList<Integer>();
        Collections.addAll(durations, new Integer(0), new Integer(5),new Integer(10),new Integer(15));
    }    
    
    public ArrayList<Integer> getDurations() {
		return durations;
	}

	public void setDurations(ArrayList<Integer> durations) {
		this.durations = durations;
	}

	public ArrayList<Leg> getLegs() {
        return legs;
    }

    public void setWindowStart(LocalDateTime windowStart) {
        this.windowStart = windowStart;
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public void setWindowEnd(LocalDateTime windowEnd) {
        this.windowEnd = windowEnd;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
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
}
