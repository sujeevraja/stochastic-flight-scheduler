package stochastic.domain;

public class Leg {
    /**
     * Class used to represent leg data
     */
    private final Integer id;
    private final Integer fltNum;
    private Integer index; // position of object in an ArrayList that will store all legs.
    private final Integer depPort;
    private final Integer arrPort;
    private int turnTimeInMin;
    private final Integer origTailId;
    private final double rescheduleCostPerMin; // first stage reschedule cost
    private final double delayCostPerMin; // second stage reschedule cost

    // times are all UNIX epoch times in minutes.
    private final long origDepTime;
    private long depTime;
    private final long origArrTime;
    private long arrTime;

    public Leg(Integer id, Integer fltNum, Integer depPort, Integer arrPort, int turnTimeInMin,
               Integer origTailId, long depTime, long arrTime) {
        this.id = id;
        this.fltNum = fltNum;
        this.index = null;
        this.depPort = depPort;
        this.arrPort = arrPort;
        this.turnTimeInMin = turnTimeInMin;
        this.origTailId = origTailId;
        // int blockTimeInMin = (int) Duration.between(depTime, arrTime).toMinutes();

        // this.rescheduleCostPerMin = 100 - (0.1 * blockTimeInMin);
        // this.delayCostPerMin = 500 - (0.1 * blockTimeInMin);

        this.rescheduleCostPerMin = 1;
        this.delayCostPerMin = 10;

        // this.rescheduleCostPerMin = 100 - (0.1 * blockTimeInMin);
        // this.delayCostPerMin = rescheduleCostPerMin * 10;

        this.origDepTime = depTime;
        this.depTime = depTime;
        this.origArrTime = arrTime;
        this.arrTime = arrTime;
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

    public Integer getFltNum() {
        return fltNum;
    }

    public void setTurnTimeInMin(int turnTimeInMin) {
        this.turnTimeInMin = turnTimeInMin;
    }

    public int getTurnTimeInMin() {
        return turnTimeInMin;
    }

    public Integer getOrigTailId() {
        return origTailId;
    }

    public double getRescheduleCostPerMin() {
        return rescheduleCostPerMin;
    }

    public double getDelayCostPerMin() {
        return delayCostPerMin;
    }

    public long getDepTime() {
        return depTime;
    }

    public long getArrTime() {
        return arrTime;
    }

    public void reschedule(int numMinutes) {
        depTime += numMinutes;
        arrTime += numMinutes;
    }

    public void revertReschedule() {
        depTime = origDepTime;
        arrTime = origArrTime;
    }

    public boolean canConnectTo(Leg otherLeg) {
        return arrPort.equals(otherLeg.depPort) && otherLeg.depTime >= arrTime + turnTimeInMin;
    }

    @Override
    public final String toString() {
        return ("Leg(id=" + id + ",index=" + index + ",fltNum=" + fltNum + ",depPort=" + depPort +
                ",depTime=" + depTime + ",arrPort=" + arrPort + ",arrTime=" + arrTime +
                ",origTail=" + origTailId + ")");
    }
}
