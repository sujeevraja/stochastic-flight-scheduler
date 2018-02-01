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
    private ArrayList<Leg> legs;
    private HashMap<Integer, Leg> legHashMap; // keys are leg ids.
    private HashMap<Integer, ArrayList<Integer>> origSchedule; // keys are tail ids, values are leg ids.

    public DataRegistry() {
        windowStart = null;
        windowEnd = null;
        equipment = null;
        legs = new ArrayList<>();
        legHashMap = new HashMap<>();
        origSchedule = new HashMap<>();
    }

    public ArrayList<Leg> getLegs() {
        return legs;
    }

    public void setWindowStart(LocalDateTime windowStart) {
        this.windowStart = windowStart;
    }

    public void setWindowEnd(LocalDateTime windowEnd) {
        this.windowEnd = windowEnd;
    }

    public void setEquipment(Equipment equipment) {
        this.equipment = equipment;
    }

    public void buildOrigSchedule(ArrayList<Leg> legs) {
        ArrayList<Integer> tails = equipment.getTails();
        for(Leg leg : legs) {
            if(leg.getArrTime().isBefore(windowStart)
                || leg.getDepTime().isAfter(windowEnd))
                continue;

            Integer tail = leg.getOrigTail();
            if(!tails.contains(tail))
                continue;

            this.legs.add(leg);
            legHashMap.put(leg.getId(), leg);

            if(origSchedule.containsKey(tail))
                origSchedule.get(tail).add(leg.getId());
            else {
                ArrayList<Integer> tailLegs = new ArrayList<>();
                tailLegs.add(leg.getId());
                origSchedule.put(tail, tailLegs);
            }
        }
    }
}
