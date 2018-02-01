package com.stochastic.network;

import java.time.LocalDateTime;

public class Node {
    /**
     * Class used to represent flight nodes in the connection network.
     */

    private Integer legId;
    private LocalDateTime depTime;
    private LocalDateTime arrTime;
    private Integer depPort;
    private Integer arrPort;

    public Node(Integer legId, LocalDateTime depTime, LocalDateTime arrTime, Integer depPort, Integer arrPort) {
        this.legId = legId;
        this.depTime = depTime;
        this.arrTime = arrTime;
        this.depPort = depPort;
        this.arrPort = arrPort;
    }
}
