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
    private Integer fltNum;
    private Integer origTail;
    private LocalDateTime depTime;
    private LocalDateTime arrTime;

    public Leg(Integer id, Integer depPort, Integer arrPort, Integer turnTimeInMin, Integer fltNum,
               Integer origTail, LocalDateTime depTime, LocalDateTime arrTime) {
        this.id = id;
        this.depPort = depPort;
        this.arrPort = arrPort;
        this.turnTimeInMin = turnTimeInMin;
        this.fltNum = fltNum;
        this.origTail = origTail;
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

    public Integer getOrigTail() {
        return origTail;
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
}
