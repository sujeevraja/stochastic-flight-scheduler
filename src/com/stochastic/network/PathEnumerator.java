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
    private HashMap<Integer, ArrayList<Integer>> adjacencyList;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxLegDelayInMin;

    private ArrayList<Path> paths;
    private ArrayList<Integer> currentPath;
    private ArrayList<Integer> delayTimes;
    private boolean[] onPath;

    PathEnumerator(Tail tail, ArrayList<Leg> legs, HashMap<Integer, ArrayList<Integer>> adjacencyList,
                   LocalDateTime startTime, LocalDateTime endTime, Integer maxLegDelayInMin)  {
        this.tail = tail;
        this.legs = legs;
        this.adjacencyList = adjacencyList;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxLegDelayInMin = maxLegDelayInMin;

        paths = new ArrayList<>();
        currentPath = new ArrayList<>();
        delayTimes = new ArrayList<>();
        onPath = new boolean[legs.size()];
        for(int i = 0; i < legs.size(); ++i)
            onPath[i] = false;
    }

    public ArrayList<Path> generatePaths() {
        for(int i = 0; i < legs.size(); ++i) {
            Leg leg = legs.get(i);

            if(!tail.getSourcePort().equals(leg.getDepPort()))
                continue;

            Integer delayTime = Math.max(0, (int) Duration.between(leg.getDepTime(), startTime).toMinutes());
            if(delayTime <= maxLegDelayInMin)
                depthFirstSearch(i, delayTime);
        }
        return paths;
    }

    private void depthFirstSearch(Integer legIndex, Integer delayTimeInMin) {
        // add index to current path
        currentPath.add(legIndex);
        onPath[legIndex] = true;
        delayTimes.add(delayTimeInMin);
        Leg leg = legs.get(legIndex);

        // if the last leg on the path can connect to the sink node, store the current path
        if(leg.getArrPort().equals(tail.getSinkPort())) {
            LocalDateTime newArrTime = leg.getArrTime().plusMinutes(delayTimeInMin);
            if(!newArrTime.isAfter(endTime))
                storeCurrentPath();
        }

        // dive to current node's neighbors
        if(adjacencyList.containsKey(legIndex)) {
            ArrayList<Integer> neighbors = adjacencyList.get(legIndex);
            // the object returned by getArrTime() is immutable.
            // So, the leg's original arrival time won't get affected here.
            LocalDateTime arrivalTime = leg.getArrTime().plusMinutes(delayTimeInMin);

            for(Integer neighborIndex: neighbors) {
                if (onPath[neighborIndex])
                    continue;

                Leg neighborLeg = legs.get(neighborIndex);
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
        onPath[legIndex] = false;
    }

    private void storeCurrentPath() {
        Path path = new Path(tail);
        for(int i = 0; i < currentPath.size(); ++i) {
            Leg leg = legs.get(currentPath.get(i));
            path.addLeg(leg, delayTimes.get(i));
        }
        paths.add(path);
    }
}
