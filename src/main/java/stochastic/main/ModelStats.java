package stochastic.main;

import java.io.Serializable;

public class ModelStats implements Serializable {
    private int numRows;
    private int numColumns;
    private int numNonZeroes;
    private double objective;

    public ModelStats(int numRows, int numColumns, int numNonZeroes, double objective) {
        this.numRows = numRows;
        this.numColumns = numColumns;
        this.numNonZeroes = numNonZeroes;
        this.objective = objective;
    }

    int getNumRows() {
        return numRows;
    }

    int getNumColumns() {
        return numColumns;
    }

    int getNumNonZeroes() {
        return numNonZeroes;
    }

    double getObjective() {
        return objective;
    }
}
