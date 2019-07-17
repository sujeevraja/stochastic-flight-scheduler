package stochastic.main;

import java.io.Serializable;

public class ModelStats implements Serializable {
    private int numRows;
    private int numColumns;
    private int numNonZeroes;

    public ModelStats(int numRows, int numColumns, int numNonZeroes) {
        this.numRows = numRows;
        this.numColumns = numColumns;
        this.numNonZeroes = numNonZeroes;
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
}
