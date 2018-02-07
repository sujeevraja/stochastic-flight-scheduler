package com.stochastic.domain;

import java.util.ArrayList;

public class Equipment {
    /**
     * Used to store equipment data.
     */
    private Integer id;
    private Integer capacity;
    private ArrayList<Integer> tailIds;

    public Equipment(Integer id, Integer capacity, ArrayList<Integer> tailIds) {
        this.id = id;
        this.capacity = capacity;
        this.tailIds = tailIds;
    }

    public Integer getId() {
        return id;
    }

    public ArrayList<Integer> getTailIds() {
        return tailIds;
    }
}
