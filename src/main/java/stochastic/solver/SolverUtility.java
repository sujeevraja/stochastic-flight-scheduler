package stochastic.solver;

import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SolverUtility {
    /**
     * Generates a map that has the original plan with propagated delays based on the given primary
     * delays.
     *
     * @param tailHashMap     map to retrieve tails using tail ids.
     * @param originalPathMap map with original path of each tail (indexed by id).
     * @param primaryDelays   primary delays of a delay scenario to propagate on original paths.
     * @return map with delayed and empty paths for each tail.
     */
    public static HashMap<Integer, ArrayList<Path>> getOriginalPaths(
        HashMap<Integer, Tail> tailHashMap, HashMap<Integer, Path> originalPathMap,
        int[] primaryDelays) {
        HashMap<Integer, ArrayList<Path>> initialPaths = new HashMap<>();

        for (Map.Entry<Integer, Path> entry : originalPathMap.entrySet()) {
            int tailId = entry.getKey();
            Tail tail = tailHashMap.getOrDefault(tailId, null);

            Path origPath = entry.getValue();

            // The original path of the tail may not be valid due to random primary delays. To make
            // the path valid, we need to propagate changes in departure time of the first leg to
            // the remaining legs on the path. These delayed paths for all tails will serve as the
            // initial set of columns for the second stage model.

            // Note that the leg coverage constraint in the second-stage model remains feasible
            // even with just these paths as we assume that there are no open legs in any data set.
            // This means that each leg must be on the original path of some tail.
            Path pathWithDelays = new Path(tail);
            ArrayList<Leg> pathLegs = origPath.getLegs();

            int propagatedDelay = 0;
            int prevPrimaryDelay = 0;
            Leg prevLeg = null;
            for (int i = 0; i < pathLegs.size(); ++i) {
                Leg leg = pathLegs.get(i);
                if (i > 0) {
                    propagatedDelay = SolverUtility.getPropagatedDelay(prevLeg, leg,
                        propagatedDelay + prevPrimaryDelay);
                }
                pathWithDelays.addLeg(leg, propagatedDelay);
                prevLeg = leg;
                prevPrimaryDelay = primaryDelays[leg.getIndex()];
            }

            // Don't allow empty paths as they may cause a mismatch between source and sink
            // stations.
            ArrayList<Path> tailPaths = new ArrayList<>();
            tailPaths.add(pathWithDelays);
            initialPaths.put(tailId, tailPaths);
        }

        return initialPaths;
    }

    static HashMap<Integer, ArrayList<Path>> getPathsForFullEnum(ArrayList<Path> paths) {
        HashMap<Integer, ArrayList<Path>> tailPathsMap = new HashMap<>();
        for (Path p : paths) {
            Integer tailId = p.getTail().getId();
            tailPathsMap.putIfAbsent(tailId, new ArrayList<>());
            tailPathsMap.get(tailId).add(p);
        }
        return tailPathsMap;
    }

    static int getSlack(Leg incomingLeg, Leg outgoingLeg) {
        int slack = (int) (outgoingLeg.getDepTime() - incomingLeg.getArrTime());
        return slack - incomingLeg.getTurnTimeInMin();
    }

    public static int getPropagatedDelay(Leg incomingLeg, Leg outgoingLeg, int totalIncomingDelay) {
        final int slack = getSlack(incomingLeg, outgoingLeg);
        return Math.max(0, totalIncomingDelay - slack);
    }

    static double getPropagatedDelay(Leg incomingLeg, Leg outgoingLeg, double totalIncomingDelay) {
        final int slack = getSlack(incomingLeg, outgoingLeg);
        return Math.max(0.0, totalIncomingDelay - slack);
    }
}
