package com.stochastic.network;

import com.stochastic.domain.Leg;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

import com.stochastic.domain.Tail;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Network {
    /**
     * Class used to hold a connection network.
     * In such a network, flights are nodes, while arcs exist between two nodes only if
     * time and space connectivity are satisfied.
     * This network will be used to enumerate paths for a set partitioning model that will
     * be solved for each tail.
     */

    private final static Logger logger = LogManager.getLogger(Network.class);
    private ArrayList<Tail> tails;
    private ArrayList<Leg> legs;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private HashMap<Integer, ArrayList<Leg>> adjacencyList;

    public Network(ArrayList<Tail> tails, ArrayList<Leg> legs, LocalDateTime startTime, LocalDateTime endTime) {
        this.tails = tails;
        this.legs = legs;
        this.startTime = startTime;
        this.endTime = endTime;

        /*
        logger.info("Started building flight nodes...");
        nodes = new ArrayList<>();
        for(Leg leg : legs) {
            Node node = new Node(leg.getId(), leg.getDepTime(), leg.getArrTime(), leg.getDepPort(), leg.getArrPort());
            nodes.add(node);
        }
        logger.info("Completed building flight nodes.");
        */

        logger.info("Started building adjacency list...");
        buildAdjacencyList();
        logger.info("Completed building adjacency list.");
    }

    public ArrayList<Path> enumeratePaths() {
        ArrayList<Path> paths = new ArrayList<>();
        for(Tail tail : tails) {
            PathEnumerator pe = new PathEnumerator(tail, legs, adjacencyList, startTime, endTime);
            paths.addAll(pe.getPaths());
        }
        return paths;
    }

    private void buildAdjacencyList() {
        adjacencyList = new HashMap<>();
        final Integer numLegs = legs.size();
        for(int i = 0; i < numLegs - 1; ++i) {
            Leg currLeg = legs.get(i);

            for (int j = i + 1; j < numLegs; ++j) {
                Leg nextLeg = legs.get(j);
                if(canConnect(currLeg, nextLeg))
                    addNeighbor(currLeg.getId(), nextLeg);
                else if (canConnect(nextLeg, currLeg))
                    addNeighbor(nextLeg.getId(), currLeg);
            }
        }
    }

    private boolean canConnect(Leg currLeg, Leg nextLeg) {
        return currLeg.getArrPort().equals(nextLeg.getDepPort())
                && (Duration.between(currLeg.getArrTime(), nextLeg.getDepTime()).toMinutes()
                    >= currLeg.getTurnTimeInMin());
    }

    private void addNeighbor(Integer legId, Leg neighbor) {
        if(adjacencyList.containsKey(legId))
            adjacencyList.get(legId).add(neighbor);
        else {
            ArrayList<Leg> neighbors = new ArrayList<>();
            neighbors.add(neighbor);
            adjacencyList.put(legId, neighbors);
        }
    }
}
