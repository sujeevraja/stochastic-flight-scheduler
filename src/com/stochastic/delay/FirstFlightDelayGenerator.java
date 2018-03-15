package com.stochastic.delay;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;

import java.util.ArrayList;
import java.util.HashMap;

public class FirstFlightDelayGenerator implements DelayGenerator {
    /**
     * Class that adds the given delay value to the first flight of eath tail.
     */
    private ArrayList<Tail> tails;
    private Integer delayTimeInMin;

    public FirstFlightDelayGenerator(ArrayList<Tail> tails, int delayTimeInMin) {
        this.tails = tails;
        this.delayTimeInMin = delayTimeInMin;
    }

    @Override
    public HashMap<Integer, Integer> generateDelays() {
        HashMap<Integer, Integer> delayMap = new HashMap<>();

        for(Tail tail : tails) {
            ArrayList<Leg> tailLegs = tail.getOrigSchedule();
            if(!tailLegs.isEmpty()) {
                Leg leg = tailLegs.get(0);
                delayMap.put(leg.getIndex(), delayTimeInMin);
            }
        }

        return delayMap;
    }
}
