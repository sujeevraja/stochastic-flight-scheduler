package stochastic.registry;

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
    private ArrayList<Leg> legs;
    private ArrayList<Tail> tails;
    private HashMap<Integer, Tail> idTailMap;
    private HashMap<Integer, Path> tailOrigPathMap;
    private Network network;

    private Scenario[] delayScenarios;
    private int rescheduleTimeBudget;

    public DataRegistry() {
        legs = new ArrayList<>();
        tails = new ArrayList<>();
    }
    
    public HashMap<Integer, Path> getTailOrigPathMap() {
		return tailOrigPathMap;
	}

	public void setTailOrigPathMap(HashMap<Integer, Path> tailOrigPathMap) {
		this.tailOrigPathMap = tailOrigPathMap;
	}

	public ArrayList<Leg> getLegs() {
        return legs;
    }

    public void setLegs(ArrayList<Leg> legs) {
        this.legs = legs;
    }

    public void setTails(ArrayList<Tail> tails) {
        this.tails = tails;
    }

    public ArrayList<Tail> getTails() {
        return tails;
    }

    public void buildIdTailMap() {
        idTailMap = new HashMap<>();
        for (Tail tail : tails)
            idTailMap.put(tail.getId(), tail);
    }

    public Tail getTail(Integer id) {
        return idTailMap.getOrDefault(id, null);
    }

    public HashMap<Integer, Tail> getIdTailMap() {
        return idTailMap;
    }

    public void buildConnectionNetwork() {
        network = new Network(legs);
    }

    public Network getNetwork() {
        return network;
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

