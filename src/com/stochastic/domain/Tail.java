package com.stochastic.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;

public class Tail {
    /**
     * Class used to hold individual aircraft and route data.
     * Assumed that original schedule is always non-empty.
     */
    private Integer id;
    private ArrayList<Leg> origSchedule;
    private Integer sourcePort;
    private Integer sinkPort;
    private LocalDateTime sourceTime;
    private LocalDateTime sinkTime;

    public Tail(Integer id, ArrayList<Leg> origSchedule) {
        try {
            this.id = id;
            this.origSchedule = origSchedule;
            sourcePort = origSchedule.get(0).getDepPort();
            sinkPort = origSchedule.get(origSchedule.size() - 1).getArrPort();
            sourceTime = origSchedule.get(0).getDepTime();
            sinkTime = origSchedule.get(origSchedule.size() - 1).getArrTime();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public Integer getId() {
        return id;
    }

    public ArrayList<Leg> getOrigSchedule() {
        return origSchedule;
    }

    public Integer getSourcePort() {
        return sourcePort;
    }

    public Integer getSinkPort() {
        return sinkPort;
    }

    public LocalDateTime getSourceTime() {
        return sourceTime;
    }

    public LocalDateTime getSinkTime() {
        return sinkTime;
    }
}
