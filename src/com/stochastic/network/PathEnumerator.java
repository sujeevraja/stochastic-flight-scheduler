package com.stochastic.network;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

public class PathEnumerator {
    /**
     * Class used to enumerate paths between source and sink for a particular tail.
     */
    private Tail tail;
    private ArrayList<Leg> legs;
    private HashMap<Integer, ArrayList<Leg>> adjacencyList;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private HashMap<Integer, ArrayList<ArrayList<Leg>>> pathsFromLegToSink;  // keys are leg ids.

    PathEnumerator(Tail tail, ArrayList<Leg> legs, HashMap<Integer, ArrayList<Leg>> adjacencyList,
                   LocalDateTime startTime, LocalDateTime endTime) {
        this.tail = tail;
        this.legs = legs;
        this.adjacencyList = adjacencyList;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    ArrayList<Path> getPaths() {
        pathsFromLegToSink = new HashMap<>();
        ArrayList<Path> paths = new ArrayList<>();

        // add empty path if possible
        if(tail.getSourcePort().equals(tail.getSinkPort()))
            paths.add(new Path(tail));

        // add paths through legs
        for(Leg leg : legs) {
            if(!canConnectToSource(leg))
                continue;

            if(!pathsFromLegToSink.containsKey(leg.getId()))
                addPathsFromLeg(leg);

            for(ArrayList<Leg> pathLegs : pathsFromLegToSink.get(leg.getId())) {
                Path path = new Path(tail);

                for(Leg pathLeg : pathLegs)
                    path.addLeg(pathLeg);

                paths.add(path);
            }
        }

        return paths;
    }

    private void addPathsFromLeg(Leg leg) {
        ArrayList<ArrayList<Leg>> legsOnPaths = new ArrayList<>();

        // add direct path to sink if possible
        if(canConnectToSink(leg)) {
            ArrayList<Leg> pathLegs = new ArrayList<>();
            pathLegs.add(leg);
            legsOnPaths.add(pathLegs);
        }

        // add paths using paths from neighbors of the leg to sink.
        if(adjacencyList.containsKey(leg.getId())) {
            for(Leg neighborLeg : adjacencyList.get(leg.getId())) {
                if (!pathsFromLegToSink.containsKey(neighborLeg.getId()))
                    addPathsFromLeg(neighborLeg);

                ArrayList<ArrayList<Leg>> neighborPaths = pathsFromLegToSink.get(neighborLeg.getId());
                for(ArrayList<Leg> neighborPath : neighborPaths) {
                    ArrayList<Leg> pathFromLeg = new ArrayList<>();
                    pathFromLeg.add(leg);
                    pathFromLeg.addAll(neighborPath);
                    legsOnPaths.add(pathFromLeg);
                }
            }
        }

        pathsFromLegToSink.put(leg.getId(), legsOnPaths);
    }

    private boolean canConnectToSource(Leg leg) {
        return tail.getSourcePort().equals(leg.getDepPort())
                && (startTime.isBefore(leg.getDepTime()) || startTime.equals(leg.getDepTime()));
    }

    private boolean canConnectToSink(Leg leg) {
        return tail.getSinkPort().equals(leg.getArrPort())
                && (endTime.isAfter(leg.getArrTime()) || endTime.equals(leg.getArrTime()));
    }
}
