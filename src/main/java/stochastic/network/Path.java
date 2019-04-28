package stochastic.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.domain.Leg;
import stochastic.domain.Tail;

import java.util.ArrayList;

public class Path {
    /**
     * Represents a column for the model.
     * Holds the tail for which it was generated and a list of legs.
     */
    private final static Logger logger = LogManager.getLogger(Path.class);
    private Tail tail;
    private ArrayList<Leg> legs;
    private ArrayList<Integer> delayTimesInMin;
    private static int pathCounter = 0;
    private int index;

    public Path(Tail tail) {
        this.tail = tail;
        legs = new ArrayList<>();
        delayTimesInMin = new ArrayList<>();
        index = pathCounter;
        pathCounter++;
    }

    @Override
    public String toString() {
        StringBuilder pathStr = new StringBuilder();
        pathStr.append(index);
        pathStr.append(": ");
        if (legs.isEmpty()) {
            pathStr.append("empty");
        } else {
            pathStr.append(legs.get(0).getId());
            for (int i = 1; i < legs.size(); ++i) {
                pathStr.append(" -> ");
                pathStr.append(legs.get(i).getId());
            }
        }
        return pathStr.toString();
    }

    static void resetPathCounter() {
        pathCounter = 0;
    }

    public void addLeg(Leg leg, Integer delayTimeInMin) {
        legs.add(leg);

        if (delayTimeInMin == null)
            delayTimeInMin = 0;

        delayTimesInMin.add(delayTimeInMin);
    }

    public Tail getTail() {
        return tail;
    }

    public ArrayList<Leg> getLegs() {
        return legs;
    }

    public int getIndex() {
        return index;
    }

    public ArrayList<Integer> getDelayTimesInMin() {
        return delayTimesInMin;
    }

    public void print() {
        logger.info("Path for tail " + tail.getId());
        for (int i = 0; i < legs.size(); ++i) {
            logger.info(legs.get(i));
            logger.info("delay time in minutes: " + delayTimesInMin.get(i));
        }
    }
}
