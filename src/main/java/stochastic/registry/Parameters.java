package stochastic.registry;

import stochastic.utility.Enums;

public class Parameters {
    private static String instancePath;
    private static int rescheduleTimeBudget;
    private static int flightRescheduleBound;
    private static int numScenariosToGenerate;

    private static double scale;
    private static double shape;

    private static Enums.DistributionType distributionType;
    private static double distributionMean;
    private static double distributionSd; // standard deviation

    private static Enums.FlightPickStrategy flightPickStrategy;

    private static boolean solveDEP;
    private static boolean bendersMultiCut;
    private static double bendersTolerance;
    private static int numBendersIterations;
    private static boolean warmStartBenders;

    private static Enums.ReducedCostStrategy reducedCostStrategy;
    private static int numReducedCostPaths; // number of reduced cost paths to collect in second stage.

    private static boolean fullEnumeration; // set to false to use labeling procedure
    private static boolean debugVerbose; // generates additional logging, writes lP files and solutions.

    private static boolean runSecondStageInParallel = false;
    private static int numThreadsForSecondStage = 1;

    // Parameters to check 2-stage solution quality
    private static boolean checkSolutionQuality;
    private static int numTestScenarios;

    // Parameters for expected excess formulation
    private static boolean expectedExcess;
    private static double rho;
    private static double excessTarget;

    public static void setInstancePath(String instancePath) {
        Parameters.instancePath = instancePath;
    }

    public static String getInstancePath() {
        return instancePath;
    }

    public static void setRescheduleTimeBudget(int rescheduleTimeBudget) {
        Parameters.rescheduleTimeBudget = rescheduleTimeBudget;
    }

    public static int getRescheduleTimeBudget() {
        return rescheduleTimeBudget;
    }

    public static void setFlightRescheduleBound(int flightRescheduleBound) {
        Parameters.flightRescheduleBound = flightRescheduleBound;
    }

    public static int getFlightRescheduleBound() {
        return flightRescheduleBound;
    }

    public static void setNumScenariosToGenerate(int numScenariosToGenerate) {
        Parameters.numScenariosToGenerate = numScenariosToGenerate;
    }

    public static int getNumScenariosToGenerate() {
        return numScenariosToGenerate;
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

    public static void setDistributionType(Enums.DistributionType distributionType) {
        Parameters.distributionType = distributionType;
    }

    public static Enums.DistributionType getDistributionType() {
        return distributionType;
    }

    public static void setDistributionMean(double distributionMean) {
        Parameters.distributionMean = distributionMean;
    }

    public static double getDistributionMean() {
        return distributionMean;
    }

    public static void setDistributionSd(double distributionSd) {
        Parameters.distributionSd = distributionSd;
    }

    public static double getDistributionSd() {
        return distributionSd;
    }

    public static void setFlightPickStrategy(Enums.FlightPickStrategy flightPickStrategy) {
        Parameters.flightPickStrategy = flightPickStrategy;
    }

    public static Enums.FlightPickStrategy getFlightPickStrategy() {
        return flightPickStrategy;
    }

    public static void setSolveDEP(boolean solveDEP) {
        Parameters.solveDEP = solveDEP;
    }

    public static boolean isSolveDEP() {
        return solveDEP;
    }

    public static void setBendersMultiCut(boolean bendersMultiCut) {
        Parameters.bendersMultiCut = bendersMultiCut;
    }

    public static boolean isBendersMultiCut() {
        return bendersMultiCut;
    }

    public static void setBendersTolerance(double bendersTolerance) {
        Parameters.bendersTolerance = bendersTolerance;
    }

    public static double getBendersTolerance() {
        return bendersTolerance;
    }

    public static void setNumBendersIterations(int numBendersIterations) {
        Parameters.numBendersIterations = numBendersIterations;
    }

    public static int getNumBendersIterations() {
        return numBendersIterations;
    }

    public static void setWarmStartBenders(boolean warmStartBenders) {
        Parameters.warmStartBenders = warmStartBenders;
    }

    public static boolean isWarmStartBenders() {
        return warmStartBenders;
    }

    public static void setReducedCostStrategy(Enums.ReducedCostStrategy reducedCostStrategy) {
        Parameters.reducedCostStrategy = reducedCostStrategy;
    }

    public static Enums.ReducedCostStrategy getReducedCostStrategy() {
        return reducedCostStrategy;
    }

    public static void setNumReducedCostPaths(int numReducedCostPaths) {
        Parameters.numReducedCostPaths = numReducedCostPaths;
    }

    public static int getNumReducedCostPaths() {
        return numReducedCostPaths;
    }

    public static void setFullEnumeration(boolean fullEnumeration) {
        Parameters.fullEnumeration = fullEnumeration;
    }

    public static boolean isFullEnumeration() {
        return fullEnumeration;
    }

    public static void setDebugVerbose(boolean debugVerbose) {
        Parameters.debugVerbose = debugVerbose;
    }

    public static boolean isDebugVerbose() {
        return debugVerbose;
    }

    public static void setRunSecondStageInParallel(boolean runSecondStageInParallel) {
        Parameters.runSecondStageInParallel = runSecondStageInParallel;
    }

    public static boolean isRunSecondStageInParallel() {
        return runSecondStageInParallel;
    }

    public static void setNumThreadsForSecondStage(int numThreadsForSecondStage) {
        Parameters.numThreadsForSecondStage = numThreadsForSecondStage;
    }

    public static int getNumThreadsForSecondStage() {
        return numThreadsForSecondStage;
    }

    public static void setCheckSolutionQuality(boolean checkSolutionQuality) {
        Parameters.checkSolutionQuality = checkSolutionQuality;
    }

    public static boolean isCheckSolutionQuality() {
        return checkSolutionQuality;
    }

    public static void setNumTestScenarios(int numTestScenarios) {
        Parameters.numTestScenarios = numTestScenarios;
    }

    public static int getNumTestScenarios() {
        return numTestScenarios;
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
}
