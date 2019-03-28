package com.stochastic.network;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.registry.DataRegistry;

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
    private int[] delays;
    private HashMap<Integer, ArrayList<Integer>> adjacencyList;

    private ArrayList<Path> paths;

    private ArrayList<Integer> currentPath;
    private boolean[] onPath;

    // delayTimes[i]: arrival time of legs[i] on currentPath - original start time of legs[i].
    // This time has 2 lower bounds: the planned reschedule time from first stage, and the random delay generated
    // for the second stage scenario.
    private ArrayList<Integer> delayTimes;
       
    public PathEnumerator() {
		super();
	}

	PathEnumerator(Tail tail, ArrayList<Leg> legs, int[] delays, HashMap<Integer, ArrayList<Integer>> adjacencyList) {
        this.tail = tail;
        this.legs = legs;
        this.delays = delays;
        this.adjacencyList = adjacencyList;

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

            // add initial (1st-stage/random) delay.
            LocalDateTime newDepTime = getNewDepTime(leg);

            // generate paths starting from leg.
            int delayTime = (int) Duration.between(leg.getDepTime(), newDepTime).toMinutes();
            depthFirstSearch(i, delayTime);
        }
        return paths;
    }

    private void depthFirstSearch(Integer legIndex, Integer delayTimeInMin) {
         // This function uses DFS to recursively build and store paths and delay times of each event on the path.
         // delayTimeInMin is the total delay time of the leg on the current path i.e. difference between the updated
         // departure time and the original departure time. It has 3 lower bounds:
         // - first stage delay (from chosen reschedule duration).
         // - random delay (chosen from random scenario of second stage).
         // - minimum delay required for the current leg to connect to the last leg of the current path.

        // add index to current path
        currentPath.add(legIndex);
        onPath[legIndex] = true;
        delayTimes.add(delayTimeInMin);

        // if the last leg on the path can connect to the sink node, store the current path
        Leg leg = legs.get(legIndex);
        if(leg.getArrPort().equals(tail.getSinkPort())) {
            LocalDateTime newArrTime = leg.getArrTime().plusMinutes(delayTimeInMin);
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
                LocalDateTime newDepTime = getNewDepTime(neighborLeg);
                LocalDateTime minReqDepTime = arrivalTime.plusMinutes(leg.getTurnTimeInMin());
                LocalDateTime depTimeOnPath = newDepTime.isAfter(minReqDepTime)
                        ? newDepTime
                        : minReqDepTime;

                int neighborDelayTime = (int) Duration.between(neighborLeg.getDepTime(), depTimeOnPath).toMinutes();
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

    private LocalDateTime getNewDepTime(Leg leg)  {
        return leg.getDepTime().plusMinutes(delays[leg.getIndex()]);
    }

    private LocalDateTime getNewArrTime(Leg leg) {
        return leg.getArrTime().plusMinutes(delays[leg.getIndex()]);
    }
    
    public ArrayList<Path> addPaths(DataRegistry dataRegistry)
    {
    	ArrayList<Path> paths = new ArrayList<Path>();
    	
        ArrayList<Tail> tails = dataRegistry.getTails();
        ArrayList<Leg> legs = dataRegistry.getLegs();
    	
        // p1 - t1, l1,l2,l3
        Path p = new Path(tails.get(0));
        p.addLeg(legs.get(0), 0);
        p.addLeg(legs.get(1), 0);
        p.addLeg(legs.get(2), 0);
        p.setIndex(1);
        paths.add(p);
        
        // p2 - t1, l1,l2        
        p = new Path(tails.get(0));
        p.addLeg(legs.get(0), 0);
        p.addLeg(legs.get(1), 0);
        p.setIndex(2);        
        paths.add(p);

        // p3 - t2, l4,l5,l6,l7,l8        
        p = new Path(tails.get(1));
        p.addLeg(legs.get(3), 0);
        p.addLeg(legs.get(4), 0);
        p.addLeg(legs.get(5), 0);        
        p.addLeg(legs.get(6), 0);
        p.addLeg(legs.get(7), 0);
        p.setIndex(3);        
        paths.add(p);
        
//        p = new Path(tails.get(1));
//        p.addLeg(legs.get(6), 0);
//        p.addLeg(legs.get(7), 0);        
//        paths.add(p);
        
        return paths;
        
    }
    
}
