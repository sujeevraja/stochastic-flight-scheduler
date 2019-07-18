package stochastic.main;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class TrainingResult implements Serializable {
    private String instance;
    private String strategy;
    private String distribution;
    private Double distributionMean;
    private Double distributionSd;
    private Double budgetFraction;

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
    private Double bendersGap;
    private Integer bendersNumCuts;
    private Integer bendersNumIterations;

    TrainingResult() {}

    String getInstance() {
        return instance;
    }

    void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    void setDistribution(String distribution) {
        this.distribution = distribution;
    }

    void setDistributionMean(Double distributionMean) {
        this.distributionMean = distributionMean;
    }

    void setDistributionSd(Double distributionSd) {
        this.distributionSd = distributionSd;
    }

    void setBudgetFraction(Double budgetFraction) {
        this.budgetFraction = budgetFraction;
    }

    Double getBudgetFraction() {
        return budgetFraction;
    }

    void setBendersGap(Double bendersGap) {
        this.bendersGap = bendersGap;
    }

    void setBendersLowerBound(Double bendersLowerBound) {
        this.bendersLowerBound = bendersLowerBound;
    }

    void setBendersNumCuts(Integer bendersNumCuts) {
        this.bendersNumCuts = bendersNumCuts;
    }

    void setBendersNumIterations(Integer bendersNumIterations) {
        this.bendersNumIterations = bendersNumIterations;
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

    void setDepModelStats(ModelStats depModelStats) {
        this.depModelStats = depModelStats;
    }

    void setDepRescheduleCost(Double depRescheduleCost) {
        this.depRescheduleCost = depRescheduleCost;
    }

    void setDepSolutionTime(Double depSolutionTime) {
        this.depSolutionTime = depSolutionTime;
    }

    void setInstance(String instance) {
        this.instance = instance;
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
        return instance != null
            && strategy != null
            && distribution != null
            && distributionMean != null
            && distributionSd != null
            && budgetFraction != null
            && naiveModelStats != null
            && naiveRescheduleCost != null
            && naiveSolutionTime != null
            && depModelStats != null
            && depRescheduleCost != null
            && depSolutionTime != null
            && bendersRescheduleCost != null
            && bendersSolutionTime != null
            && bendersLowerBound != null
            && bendersUpperBound != null
            && bendersGap != null
            && bendersNumCuts != null
            && bendersNumIterations != null;
    }


    static List<String> getCsvHeaders() {
        return new ArrayList<>(Arrays.asList(
                "instance",
                "strategy",
                "distribution",
                "mean",
                "standard deviation",
                "budget fraction",
                "Naive rows",
                "Naive columns",
                "Naive non-zeroes",
                "Naive objective",
                "Naive model reschedule cost",
                "Naive model solution time (seconds)",
                "DEP rows",
                "DEP columns",
                "DEP non-zeroes",
                "DEP objective",
                "DEP reschedule cost",
                "DEP solution time (seconds)",
                "Benders reschedule cost",
                "Benders solution time (seconds)",
                "Benders lower bound",
                "Benders upper bound",
                "Benders gap",
                "Benders number of cuts",
                "Benders number of iterations"));
    }

    List<String> getCsvRow() {
        ArrayList<String> row = new ArrayList<>(Arrays.asList(
            instance,
            strategy,
            distribution,
            Double.toString(distributionMean),
            Double.toString(distributionSd),
            Double.toString(budgetFraction)));

        row.addAll(naiveModelStats.getCsvRow());
        row.add(Double.toString(naiveRescheduleCost));
        row.add(Double.toString(naiveSolutionTime));

        row.addAll(depModelStats.getCsvRow());
        row.addAll(Arrays.asList(
            Double.toString(depRescheduleCost),
            Double.toString(depSolutionTime),
            Double.toString(bendersRescheduleCost),
            Double.toString(bendersSolutionTime),
            Double.toString(bendersLowerBound),
            Double.toString(bendersUpperBound),
            Double.toString(bendersGap),
            Integer.toString(bendersNumCuts),
            Integer.toString(bendersNumIterations)));

        return row;
    }
}
