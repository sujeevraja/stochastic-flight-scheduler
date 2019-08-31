package stochastic.solver;

import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SolverUtility {
    /**
     * This function generates a map that contains two paths for each tail: an empty path with no legs and a path
     * that has the original plan with leg delays adjusted to be the maximum of primary and propagated delays.
     *
     * @param tailHashMap     map to retrieve tails using tail ids.
     * @param originalPathMap map with original path of each tail (indexed by id).
     * @param primaryDelays   primary delays of a delay scenario to propagate on original paths.
     * @return map with delayed and empty paths for each tail.
     */
    public static HashMap<Integer, ArrayList<Path>> getOriginalPaths(HashMap<Integer, Tail> tailHashMap,
                                                                     HashMap<Integer, Path> originalPathMap,
                                                                     int[] primaryDelays) {
        HashMap<Integer, ArrayList<Path>> initialPaths = new HashMap<>();

        for (Map.Entry<Integer, Path> entry : originalPathMap.entrySet()) {
            int tailId = entry.getKey();
            Tail tail = tailHashMap.getOrDefault(tailId, null);

            Path origPath = entry.getValue();

            // The original path of the tail may not be valid due to random primary delays. To make the path valid, we
            // need to propagate changes in departure time of the first leg to the remaining legs on the path.
            // These delayed paths for all tails will serve as the initial set of columns for the second stage model.

            // Note that the leg coverage constraint in the second-stage model remains feasible even with just
            // these paths as we assume that there are no open legs in any dataset. This means that each leg
            // must be on the original path of some tail.

            Path pathWithDelays = new Path(tail);
            long currentTime = -1;
            for (Leg leg : origPath.getLegs()) {
                // find delayed departure time due to primary delay.
                int legDelay = primaryDelays[leg.getIndex()];
                long delayedDepTime = leg.getDepTime() + legDelay;

                // find delayed departure time due to propagated delay
                if (currentTime != -1 && currentTime > delayedDepTime)
                    delayedDepTime = currentTime;

                // Add leg to the path with maximum of primary and propagated delay.
                legDelay = (int) (delayedDepTime - leg.getDepTime());
                pathWithDelays.addLeg(leg, legDelay);

                // update current time based on leg's delayed arrival time and turn time.
                currentTime = leg.getArrTime() + legDelay + leg.getTurnTimeInMin();
            }

            ArrayList<Path> tailPaths = new ArrayList<>();
            tailPaths.add(new Path(tail)); // add empty path
            tailPaths.add(pathWithDelays);
            initialPaths.put(tailId, tailPaths);
        }

        return initialPaths;
    }

    static HashMap<Integer, ArrayList<Path>> getPathsForFullEnum(
        ArrayList<Path> paths, ArrayList<Tail> tails) {
        HashMap<Integer, ArrayList<Path>> tailPathsMap = new HashMap<>();
        for (Tail t : tails)
            tailPathsMap.put(t.getId(), new ArrayList<>(Collections.singletonList(new Path(t))));

        for (Path p : paths)
            tailPathsMap.get(p.getTail().getId()).add(p);

        return tailPathsMap;
    }
}
