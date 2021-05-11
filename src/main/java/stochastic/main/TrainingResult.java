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

    private Boolean bendersDone;

    TrainingResult() {
        bendersDone = false;
    }

    void markBendersDone() {
        bendersDone = true;
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
            && bendersDone;
    }

    public HashMap<String, Object> asMap() {
        HashMap<String, Object> results = Parameters.asMap();

        results.putAll(naiveModelStats.asMap("naive"));
        results.put("naiveRescheduleCost", naiveRescheduleCost);
        results.put("naiveSolutionTimeSec", naiveSolutionTime);

        results.putAll(depModelStats.asMap("dep"));
        results.put("depRescheduleCost", depRescheduleCost);
        results.put("depSolutionTimeSec", depSolutionTime);

        return results;
    }
}
