package com.stochastic.registry;

import com.stochastic.domain.Equipment;

import java.util.ArrayList;
import java.util.HashMap;

public class DataRegistry {
    /**
     * Holds all airline objects built from input data.
     */
    private ArrayList<Equipment> equipments;
    private HashMap<Integer, Integer> tailEqpMap;

    public DataRegistry() {
    }

    public void setEquipments(ArrayList<Equipment> equipments) {
        this.equipments = equipments;
    }

    public ArrayList<Equipment> getEquipments() {
        return equipments;
    }

    public final HashMap<Integer, Integer> getTailEqpMap() {
        return tailEqpMap;
    }

    public void buildTailEqpMap() {
        tailEqpMap = new HashMap<>();
        for(Equipment eqp : equipments)
            for (Integer tail : eqp.getTails())
                tailEqpMap.put(tail, eqp.getId());
    }
}
