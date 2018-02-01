package com.stochastic.domain;

import java.util.ArrayList;

public class Equipment {
    /**
     * Used to store equipment data.
     */
    private Integer id;
    private Integer capacity;
    private ArrayList<Integer> tails;

    public Equipment(Integer id, Integer capacity, ArrayList<Integer> tails) {
        this.id = id;
        this.capacity = capacity;
        this.tails = tails;
    }

    public Integer getId() {
        return id;
    }

    public ArrayList<Integer> getTails() {
        return tails;
    }
}
