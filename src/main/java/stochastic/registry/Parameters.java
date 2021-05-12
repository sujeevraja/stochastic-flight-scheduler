package stochastic.registry;

import stochastic.utility.Enums;

import java.util.HashMap;

public class Parameters {
    private static String instanceName;
    private static String instancePath;
    private static String outputPath;
    private static Enums.Model model;

    /**
     * This fraction will be used to set the reschedule time budget, which will be calculated as:
     * reschedule time budget = rescheduleBudgetFraction * (average total delay of all scenarios).
     */
    private static double rescheduleBudgetFraction;

    private static int flightRescheduleBound;
    private static int numSecondStageScenarios;

    private static Enums.DistributionType distributionType;
    private static double distributionMean;
    private static double distributionSd; // standard deviation
    private static boolean parsePrimaryDelaysFromFiles;

    private static Enums.FlightPickStrategy flightPickStrategy;

    private static boolean bendersMultiCut;
    private static double bendersTolerance;
    private static int numBendersIterations;
    private static boolean warmStartBenders;

    private static Enums.ColumnGenStrategy columnGenStrategy;
    private static int numReducedCostPaths; // number of reduced cost paths to collect in second stage.
    private static boolean useColumnCaching;

    private static boolean debugVerbose; // generates additional logging, writes lP files and solutions.
    private static boolean setCplexNames; // adds names to model variables and constraints.
    private static boolean showCplexOutput;

    private static boolean runSecondStageInParallel = false;
    private static int numThreadsForSecondStage = 1;

    // Parameters to check 2-stage solution quality
    private static boolean checkSolutionQuality;
    private static int numTestScenarios;

    // Parameters for expected excess formulation
    private static boolean expectedExcess;
    private static double riskAversion;
    private static int excessTarget;

    public static void setInstanceName(String instanceName) {
        Parameters.instanceName = instanceName;
    }

    public static String getInstanceName() {
        return instanceName;
    }

    public static void setInstancePath(String instancePath) {
        Parameters.instancePath = instancePath;
    }

    public static String getInstancePath() {
        return instancePath;
    }

    public static String getOutputPath() {
        return outputPath;
    }

    public static void setOutputPath(String outputPath) {
        Parameters.outputPath = outputPath;
    }

    public static void setModel(Enums.Model model) {
        Parameters.model = model;
    }

    public static Enums.Model getModel() {
        return model;
    }

    public static void setRescheduleBudgetFraction(double rescheduleBudgetFraction) {
        Parameters.rescheduleBudgetFraction = rescheduleBudgetFraction;
    }

    public static double getRescheduleBudgetFraction() {
        return rescheduleBudgetFraction;
    }

    public static void setFlightRescheduleBound(int flightRescheduleBound) {
        Parameters.flightRescheduleBound = flightRescheduleBound;
    }

    public static int getFlightRescheduleBound() {
        return flightRescheduleBound;
    }

    public static void setNumSecondStageScenarios(int numSecondStageScenarios) {
        Parameters.numSecondStageScenarios = numSecondStageScenarios;
    }

    public static int getNumSecondStageScenarios() {
        return numSecondStageScenarios;
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

    public static void setParsePrimaryDelaysFromFiles(boolean parsePrimaryDelaysFromFiles) {
        Parameters.parsePrimaryDelaysFromFiles = parsePrimaryDelaysFromFiles;
    }

    public static boolean isParsePrimaryDelaysFromFiles() {
        return parsePrimaryDelaysFromFiles;
    }

    public static void setFlightPickStrategy(Enums.FlightPickStrategy flightPickStrategy) {
        Parameters.flightPickStrategy = flightPickStrategy;
    }

    public static Enums.FlightPickStrategy getFlightPickStrategy() {
        return flightPickStrategy;
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

    public static void setColumnGenStrategy(Enums.ColumnGenStrategy columnGenStrategy) {
        Parameters.columnGenStrategy = columnGenStrategy;
    }

    public static Enums.ColumnGenStrategy getColumnGenStrategy() {
        return columnGenStrategy;
    }

    public static void setNumReducedCostPaths(int numReducedCostPaths) {
        Parameters.numReducedCostPaths = numReducedCostPaths;
    }

    public static int getNumReducedCostPaths() {
        return numReducedCostPaths;
    }

    public static void setUseColumnCaching(boolean useColumnCaching) {
        Parameters.useColumnCaching = useColumnCaching;
    }

    public static boolean isUseColumnCaching() {
        return useColumnCaching;
    }

    public static void setDebugVerbose(boolean debugVerbose) {
        Parameters.debugVerbose = debugVerbose;
    }

    public static boolean isDebugVerbose() {
        return debugVerbose;
    }

    public static void setSetCplexNames(boolean setCplexNames) {
        Parameters.setCplexNames = setCplexNames;
    }

    public static boolean isSetCplexNames() {
        return setCplexNames;
    }

    public static void setShowCplexOutput(boolean showCplexOutput) {
        Parameters.showCplexOutput = showCplexOutput;
    }

    public static boolean disableCplexOutput() {
        return !showCplexOutput;
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

    public static void setRiskAversion(double riskAversion) {
        Parameters.riskAversion = riskAversion;
    }

    public static double getRiskAversion() {
        return riskAversion;
    }

    public static void setExcessTarget(int excessTarget) {
        Parameters.excessTarget = excessTarget;
    }

    public static int getExcessTarget() {
        return excessTarget;
    }

    public static HashMap<String, Object> asMap() {
        HashMap<String, Object> results = new HashMap<>();
        results.put("instanceName", instanceName);
        results.put("model", model.name());
        results.put("budgetFraction", rescheduleBudgetFraction);
        results.put("flightRescheduleLimit", flightRescheduleBound);
        results.put("numTrainingScenarios", numSecondStageScenarios);
        results.put("distributionType", distributionType.name());
        results.put("distributionMean", distributionMean);
        results.put("distributionSd", distributionSd);
        results.put("flightPickStrategy", flightPickStrategy.name());
        results.put("bendersMultiCut", bendersMultiCut);
        results.put("bendersIterations", numBendersIterations);
        results.put("columnGenStrategy", columnGenStrategy.name());
        results.put("useColumnCaching", useColumnCaching);
        results.put("numThreads", numThreadsForSecondStage);
        results.put("numTestScenarios", numTestScenarios);
        return results;
    }
}
