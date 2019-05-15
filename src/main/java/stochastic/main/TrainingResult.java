package stochastic.main;

import java.io.Serializable;

public class TrainingResult implements Serializable {
    private String instance;
    private Double budgetFraction;
    private Double naiveRescheduleCost;
    private Double naiveSolutionTime;
    private Double depRescheduleCost;
    private Double rescheduleSolutionTime;
    private Double bendersRescheduleCost;
    private Double bendersSolutionTime;
    private Double bendersLowerBound;
    private Double bendersUpperBound;
    private Double bendersGap;
    private Double bendersNumCuts;
    private Double bendersNumIterations;

    public Double getBendersGap() {
        return bendersGap;
    }

    public Double getBendersLowerBound() {
        return bendersLowerBound;
    }

    public Double getBendersNumCuts() {
        return bendersNumCuts;
    }
    public Double getBendersNumIterations() {
        return bendersNumIterations;
    }

    public Double getBendersRescheduleCost() {
        return bendersRescheduleCost;
    }

    public Double getBendersSolutionTime() {
        return bendersSolutionTime;
    }

    public Double getBendersUpperBound() {
        return bendersUpperBound;
    }

    public Double getBudgetFraction() {
        return budgetFraction;
    }

    public Double getDepRescheduleCost() {
        return depRescheduleCost;
    }

    public Double getNaiveRescheduleCost() {
        return naiveRescheduleCost;
    }

    public Double getNaiveSolutionTime() {
        return naiveSolutionTime;
    }

    public Double getRescheduleSolutionTime() {
        return rescheduleSolutionTime;
    }

    public String getInstance() {
        return instance;
    }

    public void setBendersGap(Double bendersGap) {
        this.bendersGap = bendersGap;
    }

    public void setBendersLowerBound(Double bendersLowerBound) {
        this.bendersLowerBound = bendersLowerBound;
    }

    public void setBendersNumCuts(Double bendersNumCuts) {
        this.bendersNumCuts = bendersNumCuts;
    }

    public void setBendersNumIterations(Double bendersNumIterations) {
        this.bendersNumIterations = bendersNumIterations;
    }

    public void setBendersRescheduleCost(Double bendersRescheduleCost) {
        this.bendersRescheduleCost = bendersRescheduleCost;
    }

    public void setBendersSolutionTime(Double bendersSolutionTime) {
        this.bendersSolutionTime = bendersSolutionTime;
    }

    public void setBendersUpperBound(Double bendersUpperBound) {
        this.bendersUpperBound = bendersUpperBound;
    }

    public void setBudgetFraction(Double budgetFraction) {
        this.budgetFraction = budgetFraction;
    }

    public void setDepRescheduleCost(Double depRescheduleCost) {
        this.depRescheduleCost = depRescheduleCost;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public void setNaiveRescheduleCost(Double naiveRescheduleCost) {
        this.naiveRescheduleCost = naiveRescheduleCost;
    }

    public void setNaiveSolutionTime(Double naiveSolutionTime) {
        this.naiveSolutionTime = naiveSolutionTime;
    }

    public void setRescheduleSolutionTime(Double rescheduleSolutionTime) {
        this.rescheduleSolutionTime = rescheduleSolutionTime;
    }

    boolean allPopulated() {
        return instance != null
            && budgetFraction != null
            && naiveRescheduleCost != null
            && naiveSolutionTime != null
            && depRescheduleCost != null
            && rescheduleSolutionTime != null
            && bendersRescheduleCost != null
            && bendersSolutionTime != null
            && bendersLowerBound != null
            && bendersUpperBound != null
            && bendersGap != null
            && bendersNumCuts != null
            && bendersNumIterations != null;
    }
}
