package stochastic.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.domain.Leg;
import stochastic.domain.Tail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Network {
    /**
     * Class used to hold a connection network.
     * In such a network, lets are nodes. Arcs exist between two nodes only if time and space connectivity are
     * satisfied. This network will be used to enumerate paths for a set partitioning model that will
     * be solved for each tail.
     */
    private final static Logger logger = LogManager.getLogger(Network.class);

    private final ArrayList<Leg> legs;
    private HashMap<Integer, ArrayList<Integer>> adjacencyList; // keys and values are indices of leg list.

    public Network(ArrayList<Leg> legs) {
        this.legs = legs;
        buildAdjacencyList();
    }

    public int getNumConnections() {
        int numConnections = 0;
        for (Map.Entry<Integer, ArrayList<Integer>> entry : adjacencyList.entrySet()) {
            numConnections += entry.getValue().size();
        }
        return numConnections;
    }

    public long computeNumRoundTripsTo(Integer airportId) {
        long numTrips = 0;
        for (Leg leg : legs) {
            if (airportId != null && !leg.getDepPort().equals(airportId))
                continue;

            final Integer index = leg.getIndex();
            if (!adjacencyList.containsKey(index))
                continue;

            ArrayList<Integer> neighborLegIndices = adjacencyList.get(index);
            final Integer depPort = leg.getDepPort();
            for (Integer neighborIndex : neighborLegIndices) {
                Leg neighbor = legs.get(neighborIndex);
                if (neighbor.getArrPort().equals(depPort))
                    ++numTrips;
            }
        }
        return numTrips;
    }

    public double computeDensity() {
        long numVertices = legs.size();
        long numEdges = 0;
        for (ArrayList<Integer> neighbors : adjacencyList.values()) {
            numEdges += neighbors.size();
        }
        return (2.0 * (numEdges)) / (numVertices * (numVertices - 1));
    }

    public long countPathsForTails(ArrayList<Tail> tails) {
        long totalNumPaths = 0;
        for (Tail tail : tails) {
            PathCounter pc = new PathCounter(tail, legs, adjacencyList);
            long numPathsForTail = pc.countPathsForTail();
            logger.info("number of paths for tail " + tail.getId() + "(" + tail.getSourcePort() + " -> "
                    + tail.getSinkPort() + "): " + numPathsForTail);
            totalNumPaths += numPathsForTail;
        }
        return totalNumPaths;
    }

    public ArrayList<Path> enumeratePathsForTails(ArrayList<Tail> tails, int[] delays) {
        ArrayList<Path> paths = new ArrayList<>();
        for (Tail tail : tails) {
            PathEnumerator pe = new PathEnumerator(tail, legs, delays, adjacencyList);
            ArrayList<Path> tailPaths = pe.generatePaths();
            paths.addAll(tailPaths);
        }
        Path.resetPathCounter();
        return paths;
    }

    private void buildAdjacencyList() {
        // Builds leg adjacency list by evaluating connections including delays.
        logger.info("started building adjacency list...");
        adjacencyList = new HashMap<>();
        final int numLegs = legs.size();
        for (int i = 0; i < numLegs - 1; ++i) {
            Leg currLeg = legs.get(i);

            for (int j = i + 1; j < numLegs; ++j) {
                Leg nextLeg = legs.get(j);

                if (currLeg.canConnectTo(nextLeg))
                    addNeighbor(i, j);
                if (nextLeg.canConnectTo(currLeg))
                    addNeighbor(j, i);
            }
        }
        logger.info("completed building adjacency list.");
    }

    public ArrayList<Integer> getNeighbors(int legIndex) {
        return adjacencyList.getOrDefault(legIndex, null);
    }

    private void addNeighbor(Integer legIndex, Integer neighborIndex) {
        if (adjacencyList.containsKey(legIndex))
            adjacencyList.get(legIndex).add(neighborIndex);
        else {
            ArrayList<Integer> neighbors = new ArrayList<>();
            neighbors.add(neighborIndex);
            adjacencyList.put(legIndex, neighbors);
        }
    }
}
