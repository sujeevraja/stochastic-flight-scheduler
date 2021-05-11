package stochastic.main;

import stochastic.registry.Parameters;

import java.io.Serializable;
import java.util.HashMap;

class TrainingResult implements Serializable {
    private ModelStats naiveModelStats;
    private Double naiveRescheduleCost;
    private Double naiveSolutionTime;

    private ModelStats depModelStats;
    private Double depRescheduleCost;
    private Double depSolutionTime;

    private Double bendersRescheduleCost;
    private Double bendersSolutionTime;
    private Double bendersLowerBound;
    private Double bendersUpperBound;
    private Double bendersGlobalUpperBound;
    private Double bendersGap;
    private Double bendersOptimalityGap;
    private Integer bendersNumCuts;

    TrainingResult() {}

    void setBendersGap(Double bendersGap) {
        this.bendersGap = bendersGap;
    }

    void setBendersLowerBound(Double bendersLowerBound) {
        this.bendersLowerBound = bendersLowerBound;
    }

    void setBendersNumCuts(Integer bendersNumCuts) {
        this.bendersNumCuts = bendersNumCuts;
    }

    void setBendersRescheduleCost(Double bendersRescheduleCost) {
        this.bendersRescheduleCost = bendersRescheduleCost;
    }

    void setBendersSolutionTime(Double bendersSolutionTime) {
        this.bendersSolutionTime = bendersSolutionTime;
    }

    void setBendersUpperBound(Double bendersUpperBound) {
        this.bendersUpperBound = bendersUpperBound;
    }

    void setBendersGlobalUpperBound(Double bendersGlobalUpperBound) {
        this.bendersGlobalUpperBound = bendersGlobalUpperBound;
    }

    void setBendersOptimalityGap(double bendersOptimalityGap) {
        this.bendersOptimalityGap = bendersOptimalityGap;
    }

    void setDepModelStats(ModelStats depModelStats) {
        this.depModelStats = depModelStats;
    }

    void setDepRescheduleCost(Double depRescheduleCost) {
        this.depRescheduleCost = depRescheduleCost;
    }

    void setDepSolutionTime(Double depSolutionTime) {
        this.depSolutionTime = depSolutionTime;
    }

    void setNaiveModelStats(ModelStats naiveModelStats) {
        this.naiveModelStats = naiveModelStats;
    }

    void setNaiveRescheduleCost(Double naiveRescheduleCost) {
        this.naiveRescheduleCost = naiveRescheduleCost;
    }

    void setNaiveSolutionTime(Double naiveSolutionTime) {
        this.naiveSolutionTime = naiveSolutionTime;
    }

    boolean allPopulated() {
        return naiveModelStats != null
            && naiveRescheduleCost != null
            && naiveSolutionTime != null
            && depModelStats != null
            && depRescheduleCost != null
            && depSolutionTime != null
            && bendersRescheduleCost != null
            && bendersSolutionTime != null
            && bendersLowerBound != null
            && bendersUpperBound != null
            && bendersGlobalUpperBound != null
            && bendersGap != null
            && bendersOptimalityGap != null
            && bendersNumCuts != null;
    }

    public HashMap<String, Object> asMap() {
        HashMap<String, Object> results = new HashMap<>();
        results.put("instanceName", Parameters.getInstanceName());
        results.put("model", Parameters.getModel());
        results.put("budgetFraction", Parameters.getRescheduleBudgetFraction());
        results.put("flightRescheduleLimit", Parameters.getFlightRescheduleBound());
        results.put("numTrainingScenarios", Parameters.getNumSecondStageScenarios());
        results.put("distributionType", Parameters.getDistributionType());
        results.put("distributionMean", Parameters.getDistributionMean());
        results.put("distributionSd", Parameters.getDistributionSd());
        results.put("flightPickStrategy", Parameters.getFlightPickStrategy());
        results.put("bendersMultiCut", Parameters.isBendersMultiCut());
        results.put("bendersIterations", Parameters.getNumBendersIterations());
        results.put("columnGenStrategy", Parameters.getColumnGenStrategy());
        results.put("useColumnCaching", Parameters.isUseColumnCaching());
        results.put("numThreads", Parameters.getNumThreadsForSecondStage());
        results.put("numTestScenarios", Parameters.getNumTestScenarios());

        results.putAll(naiveModelStats.asMap("naive"));
        results.put("naiveRescheduleCost", naiveRescheduleCost);
        results.put("naiveSolutionTimeSec", naiveSolutionTime);

        results.putAll(depModelStats.asMap("dep"));
        results.put("depRescheduleCost", depRescheduleCost);
        results.put("depSolutionTimeSec", depSolutionTime);

        results.put("bendersRescheduleCost", bendersRescheduleCost);
        results.put("bendersSolutionTime", bendersSolutionTime);
        results.put("bendersLowerBound", bendersLowerBound);
        results.put("bendersUpperBound", bendersUpperBound);
        results.put("bendersGlobalUpperBound", bendersGlobalUpperBound);
        results.put("bendersGap", bendersGap);
        results.put("bendersOptimalityGap", bendersOptimalityGap);
        results.put("bendersNumCuts", bendersNumCuts);
        return results;
    }
}
