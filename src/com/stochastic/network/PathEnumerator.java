package com.stochastic.network;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;

import java.lang.Math;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

public class PathEnumerator {
    /**
     * Class used to enumerate all paths for a particular tail.
     */
    private Tail tail;
    private ArrayList<Leg> legs;
    private ArrayList<Node> nodes;
    private Node sourceNode;
    private Node sinkNode;
    private HashMap<Integer, ArrayList<Integer>> adjacencyList;
    private Integer maxLegDelayInMin;
    private ArrayList<Integer> sourceNeighbors; // indices of nodes.
    private ArrayList<Integer> sinkNeighbors; // indices of nodes.

    private ArrayList<Path> paths;
    private ArrayList<Integer> currentPath;
    private ArrayList<Integer> delayTimes;
    private boolean[] onPath;

    PathEnumerator(Tail tail, ArrayList<Leg> legs, ArrayList<Node> nodes,
                   HashMap<Integer, ArrayList<Integer>> adjacencyList, Integer maxLegDelayInMin)  {
        this.tail = tail;
        this.legs = legs;
        this.nodes = nodes;
        sourceNode = nodes.get(0);
        sinkNode = nodes.get(nodes.size() - 1);
        this.adjacencyList = adjacencyList;
        this.maxLegDelayInMin = maxLegDelayInMin;

        paths = new ArrayList<>();
        currentPath = new ArrayList<>();
        delayTimes = new ArrayList<>();
        onPath = new boolean[nodes.size()];
        for(int i = 0; i < nodes.size(); ++i)
            onPath[i] = false;
    }

    public ArrayList<Path> generatePaths() {
        buildSourceAndSinkNeighbors();

        for(Integer nodeIndex : sourceNeighbors) {
            Node legNode = nodes.get(nodeIndex);
            Integer delayTime = (int) Duration.between(legNode.getDepTime(), sourceNode.getArrTime()).toMinutes();
            delayTime = Math.max(0, delayTime);
            depthFirstSearch(nodeIndex, delayTime);
        }
        return paths;
    }

    private void buildSourceAndSinkNeighbors() {
        // update source and sink ports.
        sourceNode.setPort(tail.getSourcePort());
        sinkNode.setPort(tail.getSinkPort());

        // don't make source and sink neighbors of each other here as we will always add an empty path
        // to ensure feasibility for each tail.

        // build connections to source and sink node.
        sourceNeighbors = new ArrayList<>();
        sinkNeighbors = new ArrayList<>();

        for(int i = 1; i < nodes.size() - 1; ++i) {
            Node node = nodes.get(i);
            Leg leg = legs.get(node.getLegIndex());

            if(canConnectFromSource(leg))
                sourceNeighbors.add(i);

            if(canConnectToSink(leg))
                sinkNeighbors.add(i);
        }
    }

    private void depthFirstSearch(Integer nodeIndex, Integer delayTimeInMin) {
        // add index to current path
        currentPath.add(nodeIndex);
        onPath[nodeIndex] = true;
        delayTimes.add(delayTimeInMin);

        // if the last leg can connect to the sink node, store the current path as a complete one
        Leg leg = legs.get(nodes.get(nodeIndex).getLegIndex());

        if(leg.getArrPort().equals(tail.getSinkPort())) {
            LocalDateTime newArrTime = leg.getArrTime().plusMinutes(delayTimeInMin);
            if(!newArrTime.isAfter(sinkNode.getDepTime()))
                storeCurrentPath();
        }

        // dive to current node's neighbors
        ArrayList<Integer> neighbors = adjacencyList.getOrDefault(nodeIndex, null);
        if(neighbors != null) {
            // the object returned by getArrTime() is immutable.
            // So, the leg's original arrival time won't get affected here.
            LocalDateTime arrivalTime = leg.getArrTime().plusMinutes(delayTimeInMin);

            for(Integer neighborIndex: neighbors) {
                if (onPath[neighborIndex])
                    continue;

                Leg neighborLeg = legs.get(nodes.get(neighborIndex).getLegIndex());
                LocalDateTime depTimeOnPath = arrivalTime.plusMinutes(leg.getTurnTimeInMin());
                Integer neighborDelayTime = depTimeOnPath.isAfter(neighborLeg.getDepTime())
                        ? (int) Duration.between(neighborLeg.getDepTime(), depTimeOnPath).toMinutes()
                        : 0;

                if(neighborDelayTime <= maxLegDelayInMin)
                    depthFirstSearch(neighborIndex, neighborDelayTime);
            }
        }

        currentPath.remove(currentPath.size() - 1);
        delayTimes.remove(delayTimes.size() - 1);
        onPath[nodeIndex] = false;
    }

    private void storeCurrentPath() {
        Path path = new Path(tail);
        for(int i = 0; i < currentPath.size(); ++i) {
            Node node = nodes.get(currentPath.get(i));
            path.addLeg(legs.get(node.getLegIndex()), delayTimes.get(i));
        }
        paths.add(path);
    }

    private boolean canConnectFromSource(Leg leg) {
        if(!sourceNode.getArrPort().equals(leg.getDepPort()))
            return false;

        LocalDateTime prevTime = sourceNode.getArrTime();
        return prevTime.isBefore(leg.getLatestDepTime()) || prevTime.equals(leg.getLatestDepTime());
    }

    private boolean canConnectToSink(Leg leg) {
        if(!leg.getArrPort().equals(sinkNode.getDepPort()))
            return false;

        LocalDateTime nextTime = sinkNode.getDepTime();
        return nextTime.equals(leg.getArrTime()) || nextTime.isAfter(leg.getArrTime());
    }
}
