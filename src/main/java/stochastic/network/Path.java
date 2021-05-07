package stochastic.network;

import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.utility.OptException;

import java.util.ArrayList;

/**
 * Represents a column for the model.
 * Holds the tail for which it was generated and a list of legs.
 */
public class Path {
    private final Tail tail;
    private final ArrayList<Leg> legs;
    private final ArrayList<Integer> propagatedDelays;
    private static int pathCounter = 0;
    private final int index;

    public Path(Tail tail) {
        this.tail = tail;
        legs = new ArrayList<>();
        propagatedDelays = new ArrayList<>();
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

        propagatedDelays.add(delayTimeInMin);
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

    public ArrayList<Integer> getPropagatedDelays() {
        return propagatedDelays;
    }

    public void checkLegality() throws OptException {
        if (legs.isEmpty())
            throw new OptException("empty path for " + tail);

        // Check connection with source station.
        if (!legs.get(0).getDepPort().equals(tail.getSourcePort()))
            throw new OptException("source station mismatch for " + tail);

        // Check connection with sink station.
        if (!legs.get(legs.size() - 1).getArrPort().equals(tail.getSinkPort()))
            throw new OptException("sink station mismatch for " + tail);

        // Check connection between legs.
        for (int i = 0; i < legs.size() - 1; ++i) {
            Leg currLeg = legs.get(i);
            Leg nextLeg = legs.get(i+1);
            if (!currLeg.canConnectTo(nextLeg))
                throw new OptException("invalid leg connection on path for " + tail);
        }
    }
}
