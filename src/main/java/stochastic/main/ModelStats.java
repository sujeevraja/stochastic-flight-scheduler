package stochastic.main;

import java.io.Serializable;
import java.util.HashMap;

public class ModelStats implements Serializable {
    private final int numRows;
    private final int numColumns;
    private final int numNonZeroes;
    private final double objective;

    public ModelStats(int numRows, int numColumns, int numNonZeroes, double objective) {
        this.numRows = numRows;
        this.numColumns = numColumns;
        this.numNonZeroes = numNonZeroes;
        this.objective = objective;
    }

    HashMap<String, Object> asMap(String prefix) {
        HashMap<String, Object> data = new HashMap<>();
        data.put(prefix + "NumRows", numRows);
        data.put(prefix + "NumColumns", numColumns);
        data.put(prefix + "NumNonZeroes", numNonZeroes);
        data.put(prefix + "Objective", objective);
        return data;
    }
}
