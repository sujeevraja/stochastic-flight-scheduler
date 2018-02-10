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
    private ArrayList<Node> nodes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxLegDelayInMin;
    private HashMap<Integer, ArrayList<Integer>> adjacencyList; // keys and values are indices of nodes list.

    public Network(ArrayList<Tail> tails, ArrayList<Leg> legs, LocalDateTime startTime, LocalDateTime endTime,
                   Integer maxLegDelayInMin) {
        this.tails = tails;
        this.legs = legs;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxLegDelayInMin = maxLegDelayInMin;

        logger.info("Started building leg nodes...");
        buildNodes();
        logger.info("Completed building leg nodes.");
        logger.info("Started building adjacency list...");
        buildAdjacencyList();
        logger.info("Completed building adjacency list.");
    }

    public ArrayList<Path> enumeratePaths() {
        ArrayList<Path> paths = new ArrayList<>();
        for(Tail tail : tails) {
            PathEnumerator pe = new PathEnumerator(tail, legs, nodes, adjacencyList, maxLegDelayInMin);
            ArrayList<Path> tailPaths = pe.generatePaths();
            logger.info("Number of paths for tail " + tail.getId() + ": " + tailPaths.size());
            paths.addAll(tailPaths);
        }
        logger.info("Total number of paths: " + paths.size());
        return paths;
    }

    private void buildNodes() {
        nodes = new ArrayList<>();

        // add source node
        Node sourceNode = new Node(null, startTime, startTime, null, null);
        sourceNode.setSourceNode(true);
        nodes.add(sourceNode);

        // add leg nodes
        for(Leg leg : legs) {
            Node node = new Node(leg);
            nodes.add(node);
        }

        // add sink node
        Node sinkNode = new Node(null, endTime, endTime, null, null);
        sinkNode.setSinkNode(true);
        nodes.add(sinkNode);
    }

    private void buildAdjacencyList() {
        // Builds adjacency list only for leg nodes as soure/sink nodes need tail info.
        // Assumes that:
        // - first node of nodes is sourceNode
        // - last node of nodes is sinkNodes
        // - the middle nodes have the same ordering as the legs list.

        adjacencyList = new HashMap<>();

        final Integer numNodes = nodes.size();
        for(int i = 1; i < numNodes - 2; ++i) {
            Leg currLeg = legs.get(nodes.get(i).getLegIndex());

            for (int j = i + 1; j < numNodes - 1; ++j) {
                Leg nextLeg = legs.get(nodes.get(j).getLegIndex());

                if(canConnect(currLeg, nextLeg))
                    addNeighbor(i, j);
                if (canConnect(nextLeg, currLeg))
                    addNeighbor(j, i);
            }
        }
    }

    private boolean canConnect(Leg currLeg, Leg nextLeg) {
        return currLeg.getArrPort().equals(nextLeg.getDepPort())
                && (Duration.between(currLeg.getArrTime(), nextLeg.getLatestDepTime()).toMinutes()
                    >= currLeg.getTurnTimeInMin());
    }

    private void addNeighbor(Integer legIndex, Integer neighborIndex) {
        if(adjacencyList.containsKey(legIndex))
            adjacencyList.get(legIndex).add(neighborIndex);
        else {
            ArrayList<Integer> neighbors = new ArrayList<>();
            neighbors.add(neighborIndex);
            adjacencyList.put(legIndex, neighbors);
        }
    }
}
