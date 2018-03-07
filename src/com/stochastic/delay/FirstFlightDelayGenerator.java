package com.stochastic.delay;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;

import java.util.ArrayList;
import java.util.HashMap;

public class FirstFlightDelayGenerator implements DelayGenerator {
    /**
     * Class that generates random delays for the first flight of each tail.
     */
    private ArrayList<Tail> tails;

    public FirstFlightDelayGenerator(ArrayList<Tail> tails) {
        this.tails = tails;
    }

    @Override
    public HashMap<Integer, Integer> generateDelays() {
        HashMap<Integer, Integer> legDelayMap = new HashMap<>();
        for(Tail tail : tails) {
            if(tail.getId() == 10001) {
                Leg leg = tail.getOrigSchedule().get(0);
                legDelayMap.put(leg.getIndex(), 30);
            }
        }

        return legDelayMap;
    }
}
