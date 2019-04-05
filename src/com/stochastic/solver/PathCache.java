package com.stochastic.solver;

import com.stochastic.network.Path;

import java.util.*;

public class PathCache {
    /**
     * PathCache objects are used to store paths to use as initial solutions for second stage problems. These caches
     * are udpated with the solution of every Benders iteration and provide the updated paths to the next iteration.
     */
    private HashMap<Integer, ArrayList<Path>> originalPaths; // includes on-plan paths and empty paths
    private HashMap<Integer, Path> generatedPaths;

    public PathCache() {}

    public void setOriginalPaths(HashMap<Integer, ArrayList<Path>> originalPaths) {
        this.originalPaths = originalPaths;
    }

    void setGeneratedPaths(HashMap<Integer, Path> generatedPaths) {
        this.generatedPaths = generatedPaths;
    }

    HashMap<Integer, ArrayList<Path>> getInitialPaths() {
        HashMap<Integer, ArrayList<Path>> initialPaths = new HashMap<>();
        if (originalPaths != null) {
            for (Map.Entry<Integer, ArrayList<Path>> entry : originalPaths.entrySet())
                initialPaths.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        if (generatedPaths != null) {
            for (Map.Entry<Integer, Path> entry : generatedPaths.entrySet())
                initialPaths.get(entry.getKey()).add(entry.getValue());
        }

        return initialPaths;
    }
}
