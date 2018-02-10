package com.stochastic.controller;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Network;
import com.stochastic.network.Path;
import com.stochastic.solver.SecondStageSolver;
import com.stochastic.utility.OptException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;

class SecondStageController {
    /**
     * Solves the second stage of the model by building a flight connection network, enumerating paths,
     * building and solving a set partitioning model.
     */
    private final static Logger logger = LogManager.getLogger(SecondStageController.class);
    private ArrayList<Leg> legs;
    private ArrayList<Tail> tails;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private Integer maxLegDelayInMin;

    SecondStageController(ArrayList<Leg> legs, ArrayList<Tail> tails, LocalDateTime windowStart,
                          LocalDateTime windowEnd, Integer maxLegDelayInMin) {
        this.legs = legs;
        this.tails = tails;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.maxLegDelayInMin = maxLegDelayInMin;
    }

    void solve() throws OptException {
        Network network = new Network(tails, legs, windowStart, windowEnd, maxLegDelayInMin);
        ArrayList<Path> paths = network.enumeratePaths();
        SecondStageSolver sss = new SecondStageSolver(paths, legs, tails);
        sss.solveWithCPLEX();
    }
}
