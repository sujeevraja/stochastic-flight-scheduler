package stochastic.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.utility.OptException;

import java.util.ArrayList;
import java.util.HashMap;

class PathEnumerator {
    /**
     * Class used to enumerate all paths for a particular tail.
     */
    private final static Logger logger = LogManager.getLogger(PathEnumerator.class);
    private Tail tail;
    private ArrayList<Leg> legs;
    private int[] delays;
    private HashMap<Integer, ArrayList<Integer>> adjacencyList;

    private ArrayList<Path> paths;

    private ArrayList<Integer> currentPath;
    private boolean[] onPath;

    /**
     * delayTimes[i] the delay of legs[i] on the current path stored in "currentPath". It is the
     * sum of delay propagated to it by upstream legs and its own random primary delay.
     */
    private ArrayList<Integer> delayTimes;

    PathEnumerator(
        Tail tail, ArrayList<Leg> legs, int[] delays,
        HashMap<Integer, ArrayList<Integer>> adjacencyList) {
        this.tail = tail;
        this.legs = legs;
        this.delays = delays;
        this.adjacencyList = adjacencyList;

        paths = new ArrayList<>();
        currentPath = new ArrayList<>();
        delayTimes = new ArrayList<>();
        onPath = new boolean[legs.size()];
        for (int i = 0; i < legs.size(); ++i)
            onPath[i] = false;
    }

    ArrayList<Path> generatePaths() throws OptException {
        for (int i = 0; i < legs.size(); ++i) {
            Leg leg = legs.get(i);

            if (!tail.getSourcePort().equals(leg.getDepPort()))
                continue;

            // generate paths starting from leg.
            depthFirstSearch(i, delays[leg.getIndex()]);
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
     * @param delayTimeInMin delay time incurred by leg when added to the current path
     */
    private void depthFirstSearch(Integer legIndex, Integer delayTimeInMin) throws OptException {
        // add index to current path
        currentPath.add(legIndex);
        onPath[legIndex] = true;
        delayTimes.add(delayTimeInMin);

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

                int slack = (int) (neighborLeg.getDepTime() - leg.getArrTime());
                slack -= leg.getTurnTimeInMin();

                final int propagatedDelay = Math.max(0, delayTimeInMin - slack);
                final int totalDelay = propagatedDelay + delays[neighborIndex];
                depthFirstSearch(neighborIndex, totalDelay);
            }
        }

        currentPath.remove(currentPath.size() - 1);
        delayTimes.remove(delayTimes.size() - 1);
        onPath[legIndex] = false;
    }

    private void storeCurrentPath() throws OptException {
        Path path = new Path(tail);
        for (int i = 0; i < currentPath.size(); ++i) {
            Leg leg = legs.get(currentPath.get(i));
            path.addLeg(leg, delayTimes.get(i));
            if (delayTimes.get(i) < delays[leg.getIndex()]) {
                logger.error(path);
                logger.error(leg);
                logger.error("primary delay: " + delays[leg.getIndex()]);
                logger.error("total delay: " + delayTimes.get(i));
                throw new OptException("total delay less than primary delay");
            }
        }
        paths.add(path);
    }
}
