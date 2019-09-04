package stochastic.solver;

import stochastic.output.QualityChecker;
import stochastic.output.RescheduleSolution;
import stochastic.output.TestKPISet;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.Enums;
import stochastic.utility.OptException;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Finds an upper bound to the 2-stage formulation with binary route variables by fixing the
 * first-stage variable values with the given reschedule solution.
 */
public class UpperBoundSolver {
    private DataRegistry dataRegistry;
    private RescheduleSolution rescheduleSolution;

    public UpperBoundSolver(DataRegistry dataRegistry, RescheduleSolution rescheduleSolution) {
        this.dataRegistry = dataRegistry;
        this.rescheduleSolution = rescheduleSolution;
    }

    public final double findUpperBound() throws OptException {
        Enums.ColumnGenStrategy originalStrategy = Parameters.getColumnGenStrategy();
        Parameters.setColumnGenStrategy(Enums.ColumnGenStrategy.FULL_ENUMERATION);
        QualityChecker qc = new QualityChecker(dataRegistry, dataRegistry.getDelayScenarios());
        TestKPISet averageKPISet = qc.collectAverageTestStatsForBatchRun(
            new ArrayList<>(Collections.singletonList(rescheduleSolution)))[0];
        Parameters.setColumnGenStrategy(originalStrategy);
        return rescheduleSolution.getRescheduleCost() + averageKPISet.getKpi(
            Enums.TestKPI.delayCost);
    }
}
