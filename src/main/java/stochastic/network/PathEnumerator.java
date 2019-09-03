package stochastic.network;

import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.solver.SolverUtility;

import java.util.ArrayList;
import java.util.HashMap;

class PathEnumerator {
    /**
     * Class used to enumerate all paths for a particular tail.
     */
    private Tail tail;
    private ArrayList<Leg> legs;
    private int[] primaryDelays;
    private HashMap<Integer, ArrayList<Integer>> adjacencyList;

    private ArrayList<Path> paths;

    private ArrayList<Integer> currentPath;
    private boolean[] onPath;

    /**
     * delayTimes[i] the delay of legs[i] on the current path stored in "currentPath". It is the
     * sum of delay propagated to it by upstream legs and its own random primary delay.
     */
    private ArrayList<Integer> propagatedDelays;

    PathEnumerator(
        Tail tail, ArrayList<Leg> legs, int[] primaryDelays,
        HashMap<Integer, ArrayList<Integer>> adjacencyList) {
        this.tail = tail;
        this.legs = legs;
        this.primaryDelays = primaryDelays;
        this.adjacencyList = adjacencyList;

        paths = new ArrayList<>();
        currentPath = new ArrayList<>();
        propagatedDelays = new ArrayList<>();
        onPath = new boolean[legs.size()];
        for (int i = 0; i < legs.size(); ++i)
            onPath[i] = false;
    }

    ArrayList<Path> generatePaths() {
        for (int i = 0; i < legs.size(); ++i) {
            Leg leg = legs.get(i);

            if (!tail.getSourcePort().equals(leg.getDepPort()))
                continue;

            // generate paths starting from leg.
            depthFirstSearch(i, 0);
        }
        return paths;
    }

    /**
     * Uses DFS to recursively build and store paths including delay times on each path.
     * Delay incurred by a leg when added to a path is the sum of
     * - delay propagated to it by the previous leg
     * - its own random primary delay
     *
     * @param legIndex index of leg in "legs" member to add to the current path
     * @param propagatedDelay delay time incurred by leg when added to the current path
     */
    private void depthFirstSearch(Integer legIndex, Integer propagatedDelay) {
        // add index to current path
        currentPath.add(legIndex);
        onPath[legIndex] = true;
        propagatedDelays.add(propagatedDelay);

        // if the last leg on the path can connect to the sink node, store the current path
        Leg leg = legs.get(legIndex);
        if (leg.getArrPort().equals(tail.getSinkPort()))
            storeCurrentPath();

        // dive to current node's neighbors
        if (adjacencyList.containsKey(legIndex)) {
            ArrayList<Integer> neighbors = adjacencyList.get(legIndex);
            for (Integer neighborIndex : neighbors) {
                if (onPath[neighborIndex])
                    continue;

                Leg neighborLeg = legs.get(neighborIndex);
                final int propagatedDelayToNext = SolverUtility.getPropagatedDelay(
                    leg, neighborLeg, propagatedDelay + primaryDelays[legIndex]);
                depthFirstSearch(neighborIndex, propagatedDelayToNext);
            }
        }

        currentPath.remove(currentPath.size() - 1);
        propagatedDelays.remove(propagatedDelays.size() - 1);
        onPath[legIndex] = false;
    }

    private void storeCurrentPath() {
        Path path = new Path(tail);
        for (int i = 0; i < currentPath.size(); ++i) {
            Leg leg = legs.get(currentPath.get(i));
            path.addLeg(leg, propagatedDelays.get(i));
        }
        paths.add(path);
    }
}
