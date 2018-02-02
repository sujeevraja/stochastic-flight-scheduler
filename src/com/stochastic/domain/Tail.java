package com.stochastic.domain;

import java.util.ArrayList;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Tail {
    /**
     * Class used to hold individual aircraft and route data.
     * Assumed that original schedule is always non-empty.
     */
    private final static Logger logger = LogManager.getLogger(Tail.class);
    private Integer id;
    private ArrayList<Leg> origSchedule;
    private Integer sourcePort;
    private Integer sinkPort;

    public Tail(Integer id, ArrayList<Leg> origSchedule) {
        try {
            this.id = id;
            this.origSchedule = origSchedule;
            sourcePort = origSchedule.get(0).getDepPort();
            sinkPort = origSchedule.get(origSchedule.size() - 1).getArrPort();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public Integer getId() {
        return id;
    }
}
