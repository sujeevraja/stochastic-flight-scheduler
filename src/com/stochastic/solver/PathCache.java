package com.stochastic.solver;

import com.stochastic.network.Path;

import java.util.*;

public class PathCache {
    /**
     * PathCache objects are used to store paths to use as initial solutions for second stage problems. These caches
     * are udpated with the solution of every Benders iteration and provide the updated paths to the next iteration.
     */
    private HashMap<Integer, ArrayList<Path>> cachedPaths; // includes on-plan paths and empty paths

    public PathCache() {}

    public void setCachedPaths(HashMap<Integer, ArrayList<Path>> cachedPaths) {
        this.cachedPaths = cachedPaths;
    }

    void addPaths(HashMap<Integer, Path> paths) {
        for (Map.Entry<Integer, Path> entry : paths.entrySet()) {
            if (cachedPaths.containsKey(entry.getKey()))
                cachedPaths.get(entry.getKey()).add(entry.getValue());
            else
                cachedPaths.put(entry.getKey(), new ArrayList<>(Collections.singletonList(entry.getValue())));
        }
    }

    public HashMap<Integer, ArrayList<Path>> getCachedPaths() {
        return cachedPaths;
    }
}
