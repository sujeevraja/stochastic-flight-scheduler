package stochastic.main;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class TrainingResult implements Serializable {
    private String instance;
    private Double budgetFraction;
    private Double naiveRescheduleCost;
    private Double naiveSolutionTime;
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

    Double getBudgetFraction() {
        return budgetFraction;
    }

    String getInstance() {
        return instance;
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

    void setBudgetFraction(Double budgetFraction) {
        this.budgetFraction = budgetFraction;
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

    void setNaiveRescheduleCost(Double naiveRescheduleCost) {
        this.naiveRescheduleCost = naiveRescheduleCost;
    }

    void setNaiveSolutionTime(Double naiveSolutionTime) {
        this.naiveSolutionTime = naiveSolutionTime;
    }

    boolean allPopulated() {
        return instance != null
            && budgetFraction != null
            && naiveRescheduleCost != null
            && naiveSolutionTime != null
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

    List<String> getCsvRow() {
        return new ArrayList<>(Arrays.asList(
            instance,
            Double.toString(budgetFraction),
            Double.toString(naiveRescheduleCost),
            Double.toString(naiveSolutionTime),
            Double.toString(depRescheduleCost),
            Double.toString(depSolutionTime),
            Double.toString(bendersRescheduleCost),
            Double.toString(bendersSolutionTime),
            Double.toString(bendersLowerBound),
            Double.toString(bendersUpperBound),
            Double.toString(bendersGap),
            Integer.toString(bendersNumCuts),
            Integer.toString(bendersNumIterations)));
    }
}
