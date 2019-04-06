package stochastic.network;

import stochastic.domain.Leg;

import java.time.LocalDateTime;

class Node {
    /**
     * Class used to represent nodes in the connection network.
     */

    private Integer legIndex;
    private LocalDateTime depTime;
    private LocalDateTime arrTime;
    private Integer depPort;
    private Integer arrPort;
    private boolean sourceNode;
    private boolean sinkNode;

    private Node(Integer legIndex, LocalDateTime depTime, LocalDateTime arrTime, Integer depPort, Integer arrPort) {
        this.legIndex = legIndex;
        this.depTime = depTime;
        this.arrTime = arrTime;
        this.depPort = depPort;
        this.arrPort = arrPort;
        sourceNode = false;
        sinkNode = false;
    }

    Node(Leg leg) {
        this(leg.getIndex(), leg.getDepTime(), leg.getArrTime(), leg.getDepPort(), leg.getArrPort());
    }

    public void setSourceNode(boolean sourceNode) {
        this.sourceNode = sourceNode;
    }

    public void setSinkNode(boolean sinkNode) {
        this.sinkNode = sinkNode;
    }

    public Integer getLegIndex() {
        return legIndex;
    }

    public void setPort(Integer port) {
        depPort = port;
        arrPort = port;
    }

    public Integer getDepPort() {
        return depPort;
    }

    public Integer getArrPort() {
        return arrPort;
    }

    public LocalDateTime getDepTime() {
        return depTime;
    }

    public LocalDateTime getArrTime() {
        return arrTime;
    }

    public boolean isSourceNode() {
        return sourceNode;
    }

    public boolean isSinkNode() {
        return sinkNode;
    }
}
