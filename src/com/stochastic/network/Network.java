package com.stochastic.network;

import com.stochastic.domain.Leg;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
    private HashMap<Integer, Integer> legDelayMap;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int maxLegDelayInMin;
    private HashMap<Integer, ArrayList<Integer>> adjacencyList; // keys and values are indices of leg list.

    public Network(ArrayList<Tail> tails, ArrayList<Leg> legs, HashMap<Integer, Integer> legDelayMap,
                   LocalDateTime startTime, LocalDateTime endTime, int maxLegDelayInMin) {
        this.tails = tails;
        this.legs = legs;
        this.legDelayMap = legDelayMap;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxLegDelayInMin = maxLegDelayInMin;

        logger.info("Started building leg nodes...");
        logger.info("Completed building leg nodes.");
        logger.info("Started building adjacency list...");
        buildAdjacencyList();
        logger.info("Completed building adjacency list.");
    }    
    
    public HashMap<Integer, ArrayList<Integer>> getAdjacencyList() {
		return adjacencyList;
	}
    
	public void setAdjacencyList(HashMap<Integer, ArrayList<Integer>> adjacencyList) {
		this.adjacencyList = adjacencyList;
	}

	public ArrayList<Path> enumerateAllPaths() {
        ArrayList<Path> paths = new ArrayList<>();
        for(Tail tail : tails) {
            PathEnumerator pe = new PathEnumerator(tail, legs, legDelayMap, adjacencyList, startTime, endTime,
                    maxLegDelayInMin);
            ArrayList<Path> tailPaths = pe.generatePaths();
            logger.info("Number of paths for tail " + tail.getId() + ": " + tailPaths.size());
            
            System.out.println();            
            for(Path p: tailPaths)
            {
                for(Leg l: p.getLegs())            	
                    System.out.print(l.getDepPort()+","+l.getDepTime().getHour() +":"+
                    		l.getDepTime().getMinute() + ","+l.getArrPort()+","+l.getArrTime().getHour() + ":" +
                    		l.getArrTime().getMinute() + " --> ");
                
                System.out.println();                
            }
            paths.addAll(tailPaths);
        }
        logger.info("Total number of paths: " + paths.size());
        Path.resetPathCounter();
        return paths;
    }

    private void buildAdjacencyList() {
        // Builds leg adjacency list by evaluating connections including delays.
        adjacencyList = new HashMap<>();
        final Integer numLegs = legs.size();
        for(int i = 0; i < numLegs - 1; ++i) {
            Leg currLeg = legs.get(i);

            for (int j = i + 1; j < numLegs; ++j) {
                Leg nextLeg = legs.get(j);

                if(canConnect(currLeg, nextLeg))
                    addNeighbor(i, j);
                if (canConnect(nextLeg, currLeg))
                    addNeighbor(j, i);
            }
        }
    }

    private boolean canConnect(Leg currLeg, Leg nextLeg) {
        if(!currLeg.getArrPort().equals(nextLeg.getDepPort()))
            return false;

        final LocalDateTime earliestArrTime = currLeg.getArrTime().plusMinutes(
                legDelayMap.getOrDefault(currLeg.getIndex(), 0));
        final LocalDateTime latestDepTime = nextLeg.getDepTime()
                .plusMinutes(legDelayMap.getOrDefault(nextLeg.getIndex(), 0))
                .plusMinutes(maxLegDelayInMin);

        int maxTurnTime = (int) Duration.between(earliestArrTime, latestDepTime).toMinutes();
        return maxTurnTime >= currLeg.getTurnTimeInMin();
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
