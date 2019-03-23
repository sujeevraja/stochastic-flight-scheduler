package com.stochastic.network;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class PathCounter {
    private ArrayList<Leg> legs;
    private Tail tail;
    private int[] numPathsToSink;
    private HashMap<Integer, ArrayList<Integer>> adjacencyList; // keys and values are indices of leg list.

    PathCounter(Tail tail, ArrayList<Leg> legs, HashMap<Integer, ArrayList<Integer>> adjacencyList) {
        this.tail = tail;
        this.legs = legs;
        numPathsToSink = new int[legs.size()];
        Arrays.fill(numPathsToSink, -1);
        this.adjacencyList = adjacencyList;
    }

    int countPathsForTail() {
        int numPaths = 0;
        for (Leg leg : legs) {
            if (!tail.getSourcePort().equals(leg.getDepPort()))
                continue;

            Integer index = leg.getIndex();
            if (numPathsToSink[index] < 0)
                findNumPathsToSink(index);

            numPaths += numPathsToSink[index];
        }

        return numPaths;
    }

    private void findNumPathsToSink(Integer legIndex) {
        int numPaths = 0;
        if (legs.get(legIndex).getArrPort().equals(tail.getSinkPort()))
            numPaths += 1;

        ArrayList<Integer> neighbors = adjacencyList.getOrDefault(legIndex, null);
        if  (neighbors != null) {
            for (Integer neighborIndex : neighbors) {
                if (numPathsToSink[neighborIndex] < 0)
                    findNumPathsToSink(neighborIndex);

                numPaths += numPathsToSink[neighborIndex];
            }
        }

        numPathsToSink[legIndex] = numPaths;
    }
}
