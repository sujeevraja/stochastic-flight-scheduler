package stochastic.registry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Path;
import stochastic.utility.OptException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class DataRegistryBuilder {
    private final static Logger logger = LogManager.getLogger(DataRegistryBuilder.class);
    public DataRegistry dataRegistry;

    private static class ParsedLegData {
        public final ArrayList<Leg> legs;
        public final HashMap<Integer, ArrayList<Leg>> tailRouteMap;

        public ParsedLegData(ArrayList<Leg> legs, HashMap<Integer, ArrayList<Leg>> tailRouteMap) {
            this.legs = legs;
            this.tailRouteMap = tailRouteMap;
        }
    }

    public DataRegistryBuilder(ArrayList<Leg> legs) throws OptException {
        ParsedLegData parsedLegData = parseLegs(legs);
        ArrayList<Tail> tails = buildTails(parsedLegData.tailRouteMap);
        HashMap<Integer, Path> tailPathMap = buildOriginalPaths(tails);

        Integer hub = findHub(legs);
        dataRegistry = new DataRegistry(parsedLegData.legs, tails, tailPathMap, hub);
        logger.info("Collected leg and tail data from Schedule.xml.");
    }

    /**
     * Built tail objects with original scheduled based on assigned tail ids of given legs.
     *
     * @param legs List of leg (flight) objects each with an assigned tail id.
     * @return Map of tail ids list of legs in corresponding original routes.
     */
    private ParsedLegData parseLegs(ArrayList<Leg> legs) {
        ArrayList<Leg> parsedLegs = new ArrayList<>();
        HashMap<Integer, ArrayList<Leg>> tailRouteMap = new HashMap<>();
        Integer index = 0;
        for (Leg leg : legs) {
            Integer tailId = leg.getOrigTailId();

            leg.setIndex(index);
            ++index;
            parsedLegs.add(leg);

            if (tailRouteMap.containsKey(tailId))
                tailRouteMap.get(tailId).add(leg);
            else {
                ArrayList<Leg> tailLegs = new ArrayList<>();
                tailLegs.add(leg);
                tailRouteMap.put(tailId, tailLegs);
            }
        }
        return new ParsedLegData(parsedLegs, tailRouteMap);
    }

    private static ArrayList<Tail> buildTails(HashMap<Integer, ArrayList<Leg>> tailRouteMap) {
        ArrayList<Tail> tails = new ArrayList<>();
        for (Map.Entry<Integer, ArrayList<Leg>> entry : tailRouteMap.entrySet()) {
            ArrayList<Leg> tailLegs = entry.getValue();
            tailLegs.sort(Comparator.comparing(Leg::getDepTime));
            fixTurnTimes(tailLegs);
            tails.add(new Tail(entry.getKey(), tailLegs));
        }

        tails.sort(Comparator.comparing(Tail::getId));
        for (int i = 0; i < tails.size(); ++i)
            tails.get(i).setIndex(i);

        return tails;
    }

    private static void fixTurnTimes(ArrayList<Leg> tailLegs) {
        tailLegs.sort(Comparator.comparing(Leg::getDepTime));
        for (int i = 0; i < tailLegs.size() - 1; ++i) {
            Leg leg = tailLegs.get(i);
            Leg nextLeg = tailLegs.get(i + 1);
            int turnTime = (int) (nextLeg.getDepTime() - leg.getArrTime());
            if (turnTime < leg.getTurnTimeInMin()) {
                logger.warn("turn after leg " + leg.getId() + " is shorter than its turn time "
                    + leg.getTurnTimeInMin() + ".");
                logger.warn("shortening it to " + turnTime);
                leg.setTurnTimeInMin(turnTime);
            }
        }
    }

    private static HashMap<Integer, Path> buildOriginalPaths(ArrayList<Tail> tails) throws OptException {
        HashMap<Integer, Path> tailPaths = new HashMap<>();
        for (Tail tail : tails) {
            ArrayList<Leg> tailLegs = tail.getOrigSchedule();
            Path p = new Path(tail);

            for (Leg l : tailLegs)
                p.addLeg(l, 0);

            p.checkLegality();
            tailPaths.put(tail.getId(), p);
        }
        return tailPaths;
    }

    /**
     * Find id of hub i.e. airport with most departures from given legs.
     *
     * @return Hub airport id
     */
    private Integer findHub(ArrayList<Leg> legs) {
        HashMap<Integer, Integer> portDeparturesMap = new HashMap<>();
        for (Leg leg : legs) {
            final int port = leg.getDepPort();
            portDeparturesMap.put(port, portDeparturesMap.getOrDefault(port, 0) + 1);
        }

        int maxNumDepartures = 0;
        int hub = -1;
        for (Map.Entry<Integer, Integer> entry : portDeparturesMap.entrySet()) {
            if (entry.getValue() > maxNumDepartures) {
                hub = entry.getKey();
                maxNumDepartures = entry.getValue();
            }
        }
        return hub;
    }
}
