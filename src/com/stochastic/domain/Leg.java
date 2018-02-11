package com.stochastic.domain;

import java.time.Duration;
import java.time.LocalDateTime;

public class Leg {
    /**
     * Class used to represent leg data
     */
    private Integer id;
    private Integer index; // position of object in an ArrayList that will store all legs.
    private Integer depPort;
    private Integer arrPort;
    private Integer turnTimeInMin;
    private Integer origTailId;
    private Integer blockTimeInMin;
    private LocalDateTime depTime;
    private LocalDateTime arrTime;
    private LocalDateTime latestDepTime; // based on maximum allowed delay

    public Leg(Integer id, Integer depPort, Integer arrPort, Integer turnTimeInMin, Integer origTailId,
               LocalDateTime depTime, LocalDateTime arrTime, LocalDateTime latestDepTime) {
        this.id = id;
        this.index = null;
        this.depPort = depPort;
        this.arrPort = arrPort;
        this.turnTimeInMin = turnTimeInMin;
        this.origTailId = origTailId;
        this.blockTimeInMin = (int) Duration.between(depTime, arrTime).toMinutes();
        this.depTime = depTime;
        this.arrTime = arrTime;
        this.latestDepTime = latestDepTime;
    }

    public Integer getId() {
        return id;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public Integer getIndex() {
        return index;
    }

    public Integer getDepPort() {
        return depPort;
    }

    public Integer getArrPort() {
        return arrPort;
    }

    public Integer getTurnTimeInMin() {
        return turnTimeInMin;
    }

    public Integer getOrigTailId() {
        return origTailId;
    }

    public LocalDateTime getDepTime() {
        return depTime;
    }

    public LocalDateTime getArrTime() {
        return arrTime;
    }

    public Integer getBlockTimeInMin() {
        return blockTimeInMin;
    }

    public void setTurnTimeInMin(Integer turnTimeInMin) {
        this.turnTimeInMin = turnTimeInMin;
    }

    public LocalDateTime getLatestDepTime() {
        return latestDepTime;
    }

    @Override
    public final String toString() {
        return ("Leg(id=" + id + ",depPort=" + depPort + ",depTime=" + depTime + ",arrPort=" + arrPort + ",arrTime="
                + arrTime + ",origTail=" + origTailId + ")");
    }
}
