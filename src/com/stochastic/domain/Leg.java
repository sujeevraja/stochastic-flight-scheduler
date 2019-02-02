package com.stochastic.domain;

import java.time.Duration;
import java.time.LocalDateTime;

public class Leg {
    /**
     * Class used to represent leg data
     */
    private Integer id;
    private Integer fltNum;
    private Integer index; // position of object in an ArrayList that will store all legs.
    private Integer depPort;
    private Integer arrPort;
    private int turnTimeInMin;
    private Integer origTailId;
    private int blockTimeInMin;
    private LocalDateTime depTime;
    private LocalDateTime arrTime;
    private LocalDateTime latestDepTime; // based on maximum allowed delay

    // info for labeling algorithm       
    
    public Leg(Integer id, Integer fltNum, Integer depPort, Integer arrPort, int turnTimeInMin,
               Integer origTailId, LocalDateTime depTime, LocalDateTime arrTime,
               LocalDateTime latestDepTime) {
        this.id = id;
        this.fltNum = fltNum;
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

    public int getTurnTimeInMin() {
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

    public void reschedule(int numMinutes) {
        depTime = depTime.plusMinutes(numMinutes);
        arrTime = arrTime.plusMinutes(numMinutes);
//        arrTime = depTime.plusMinutes(numMinutes);
    }

    @Override
    public final String toString() {
        return ("Leg(id=" + id + ",index=" + index + ",fltNum=" + fltNum + ",depPort=" + depPort +
                ",depTime=" + depTime + ",arrPort=" + arrPort + ",arrTime=" + arrTime + ",origTail=" +
                origTailId + ")");
    }
}
