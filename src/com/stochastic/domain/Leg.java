package com.stochastic.domain;

import java.time.LocalDateTime;

public class Leg {
    /**
     * Class used to represent leg data
     */
    private Integer id;
    private Integer depPort;
    private Integer arrPort;
    private Integer turnTimeInMin;
    private Integer origTailId;
    private LocalDateTime depTime;
    private LocalDateTime arrTime;

    public Leg(Integer id, Integer depPort, Integer arrPort, Integer turnTimeInMin, Integer origTailId,
               LocalDateTime depTime, LocalDateTime arrTime) {
        this.id = id;
        this.depPort = depPort;
        this.arrPort = arrPort;
        this.turnTimeInMin = turnTimeInMin;
        this.origTailId = origTailId;
        this.depTime = depTime;
        this.arrTime = arrTime;
    }

    public Integer getId() {
        return id;
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

    public void setTurnTimeInMin(Integer turnTimeInMin) {
        this.turnTimeInMin = turnTimeInMin;
    }

    @Override
    public final String toString() {
        return ("Leg(id=" + id + ",depPort=" + depPort + ",depTime=" + depTime + ",arrPort=" + arrPort + ",arrTime="
                + arrTime + "origTail=" + origTailId);
    }
}
