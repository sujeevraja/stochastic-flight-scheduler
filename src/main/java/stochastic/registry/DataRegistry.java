package stochastic.registry;

import stochastic.delay.DelayGenerator;
import stochastic.delay.Scenario;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Network;
import stochastic.network.Path;

import java.util.ArrayList;
import java.util.HashMap;

public class DataRegistry {
    /**
     * Holds input data.
     */
    private final ArrayList<Leg> legs;
    private final ArrayList<Tail> tails;
    private final HashMap<Integer, Tail> idTailMap;
    private final HashMap<Integer, Path> tailOrigPathMap;
    private final Network network;
    private final int hub;

    private DelayGenerator delayGenerator;
    private Scenario[] delayScenarios;
    private int rescheduleTimeBudget;

    public DataRegistry(ArrayList<Leg> legs, ArrayList<Tail> tails, HashMap<Integer, Path> tailOrigPathMap, int hub) {
        this.legs = legs;
        network = new Network(legs);

        this.tails = tails;
        idTailMap = new HashMap<>();
        for (Tail tail : tails)
            idTailMap.put(tail.getId(), tail);

        this.tailOrigPathMap = tailOrigPathMap;
        this.hub = hub;
    }

    public HashMap<Integer, Path> getTailOrigPathMap() {
        return tailOrigPathMap;
    }

    public ArrayList<Leg> getLegs() {
        return legs;
    }

    public ArrayList<Tail> getTails() {
        return tails;
    }

    public HashMap<Integer, Tail> getIdTailMap() {
        return idTailMap;
    }

    public Network getNetwork() {
        return network;
    }

    public int getHub() { return hub; }

    public void setDelayGenerator(DelayGenerator delayGenerator) {
        this.delayGenerator = delayGenerator;
    }

    public DelayGenerator getDelayGenerator() {
        return delayGenerator;
    }

    public void setDelayScenarios(Scenario[] delayScenarios) {
        this.delayScenarios = delayScenarios;
    }

    public Scenario[] getDelayScenarios() {
        return delayScenarios;
    }

    public void setRescheduleTimeBudget(int rescheduleTimeBudget) {
        this.rescheduleTimeBudget = rescheduleTimeBudget;
    }

    public int getRescheduleTimeBudget() {
        return rescheduleTimeBudget;
    }
}
