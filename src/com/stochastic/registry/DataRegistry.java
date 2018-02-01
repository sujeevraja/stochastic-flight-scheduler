package com.stochastic.registry;

import com.stochastic.domain.Equipment;
import com.stochastic.domain.Leg;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

public class DataRegistry {
    /**
     * Holds all airline objects built from input data.
     */

    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private Equipment equipment;
    private HashMap<Integer, ArrayList<Leg>> origSchedule; // keys are tail ids

    public DataRegistry() {
        windowStart = null;
        windowEnd = null;
        equipment = null;
        origSchedule = null;
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

    public void setEquipment(Equipment equipment) {
        this.equipment = equipment;
    }

    public void buildOrigSchedule(ArrayList<Leg> legs) {
        origSchedule = new HashMap<>();
        ArrayList<Integer> tails = equipment.getTails();
        for(Leg leg : legs) {
            if(leg.getArrTime().isBefore(windowStart)
                || leg.getDepTime().isAfter(windowEnd))
                continue;

            Integer tail = leg.getOrigTail();
            if(!tails.contains(tail))
                continue;

            if(origSchedule.containsKey(tail))
                origSchedule.get(tail).add(leg);
            else {
                ArrayList<Leg> tailLegs = new ArrayList<>();
                tailLegs.add(leg);
                origSchedule.put(tail, tailLegs);
            }
        }
    }
}
