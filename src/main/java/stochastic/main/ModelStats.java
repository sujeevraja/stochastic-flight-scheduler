package stochastic.main;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    List<String> getCsvRow() {
        return new ArrayList<>(Arrays.asList(
            Integer.toString(numRows),
            Integer.toString(numColumns),
            Integer.toString(numNonZeroes),
            Double.toString(objective)));
    }
}
