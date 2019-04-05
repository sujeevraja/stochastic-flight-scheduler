package com.stochastic.solver;

import com.stochastic.network.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class PathCache {
    /**
     * PathCache objects are used to store paths to use as initial solutions for second stage problems. These caches
     * are udpated with the solution of every Benders iteration and provide the updated paths to the next iteration.
     */
    private HashMap<Integer, Path> onPlanPaths;
    private HashMap<Integer, Path> generatedPaths;

    PathCache() {}

    void setGeneratedPaths(HashMap<Integer, Path> generatedPaths) {
        this.generatedPaths = generatedPaths;
    }

    void setOnPlanPaths(HashMap<Integer, Path> onPlanPaths) {
        this.onPlanPaths = onPlanPaths;
    }

    HashMap<Integer, ArrayList<Path>> getInitialPaths() {
        HashMap<Integer, ArrayList<Path>> initialPaths = new HashMap<>();
        if (onPlanPaths != null) {
            for (Map.Entry<Integer, Path> entry : onPlanPaths.entrySet())
                initialPaths.put(entry.getKey(), new ArrayList<>(Collections.singletonList(entry.getValue())));
        }

        if (generatedPaths != null) {
            for (Map.Entry<Integer, Path> entry : generatedPaths.entrySet())
                initialPaths.get(entry.getKey()).add(entry.getValue());
        }

        return initialPaths;
    }
}
