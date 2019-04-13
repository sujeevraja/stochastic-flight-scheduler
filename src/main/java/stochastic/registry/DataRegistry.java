package stochastic.registry;

import stochastic.delay.Scenario;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Network;
import stochastic.network.Path;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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

    // origSlacks[i][j] = dep_j - (arr_i + turn_i) for connecting flights with indices i, j. All times used are
    // original times i.e. before rescheduling.
    private Integer[][] origSlacks;

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

        HashMap<Integer, ArrayList<Integer>> adjList = network.getAdjacencyList();
        int numLegs = legs.size();
        origSlacks = new Integer[numLegs][];

        // store slacks for all connections.
        for(int i = 0; i < numLegs; ++i) {
            origSlacks[i] = new Integer[numLegs];
            Arrays.fill(origSlacks[i], null);

            if (!adjList.containsKey(i))
                continue;

            Leg leg = legs.get(i);
            ArrayList<Integer> neighbors = adjList.get(i);

            for (Integer index : neighbors) {
                Leg nextLeg = legs.get(index);
                int slack = (int) Duration.between(leg.getArrTime(), nextLeg.getDepTime()).toMinutes();
                slack -= leg.getTurnTimeInMin();
                origSlacks[i][index] = slack;
            }
        }
    }

    public Network getNetwork() {
        return network;
    }

    public Integer[][] getOrigSlacks() {
        return origSlacks;
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

