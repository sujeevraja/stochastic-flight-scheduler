package com.stochastic.registry;

public class Parameters {
    private static Integer numScenarios;
    private static double scale;
    private static double shape;
    private static int[] durations;
    private static boolean fullEnumeration;
    private static boolean debugVerbose; // generates additional logging, writes lP files and solutions.

    // Parameters for expected excess formulation
    private static boolean expectedExcess;
    private static double rho;
    private static double excessTarget;

    public static void setNumScenarios(Integer numScenarios) {
        Parameters.numScenarios = numScenarios;
    }

    public static Integer getNumScenarios() {
        return numScenarios;
    }

    public static void setScale(double scale) {
        Parameters.scale = scale;
    }

    public static double getScale() {
        return scale;
    }

    public static void setShape(double shape) {
        Parameters.shape = shape;
    }

    public static double getShape() {
        return shape;
    }

    public static void setDurations(int[] durations) {
        Parameters.durations = durations;
    }

    public static int[] getDurations() {
        return durations;
    }

    public static int getNumDurations() {
        return durations.length;
    }

    public static void setFullEnumeration(boolean fullEnumeration) {
        Parameters.fullEnumeration = fullEnumeration;
    }

    public static boolean isFullEnumeration() {
        return fullEnumeration;
    }

    public static void setExpectedExcess(boolean expectedExcess) {
        Parameters.expectedExcess = expectedExcess;
    }

    public static boolean isExpectedExcess() {
        return expectedExcess;
    }

    public static void setRho(double rho) {
        Parameters.rho = rho;
    }

    public static double getRho() {
        return rho;
    }

    public static void setExcessTarget(double excessTarget) {
        Parameters.excessTarget = excessTarget;
    }

    public static double getExcessTarget() {
        return excessTarget;
    }

    public static void setDebugVerbose(boolean debugVerbose) {
        Parameters.debugVerbose = debugVerbose;
    }

    public static boolean isDebugVerbose() {
        return debugVerbose;
    }
}
