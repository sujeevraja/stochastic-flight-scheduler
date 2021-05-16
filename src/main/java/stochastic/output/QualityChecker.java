package stochastic.output;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.delay.Scenario;
import stochastic.domain.Leg;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.solver.PathCache;
import stochastic.solver.SolverUtility;
import stochastic.solver.SubSolverRunnable;
import stochastic.utility.CSVHelper;
import stochastic.utility.Enums;
import stochastic.utility.OptException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * QualityChecker generates random delays to compare different reschedule solutions including the
 * original schedule (i.e no reschedule). This is done by running the second-stage model as a MIP.
 */
public class QualityChecker {
    private final static Logger logger = LogManager.getLogger(QualityChecker.class);
    private final DataRegistry dataRegistry;
    private IloCplex cplex;
    private final int[] zeroReschedules;
    private final Scenario[] testScenarios;

    public QualityChecker(DataRegistry dataRegistry, Scenario[] testScenarios) {
        this.dataRegistry = dataRegistry;
        zeroReschedules = new int[dataRegistry.getLegs().size()];
        Arrays.fill(zeroReschedules, 0);
        this.testScenarios = testScenarios;
    }

    private void initCplex() throws OptException {
        try {
            this.cplex = new IloCplex();
            if (Parameters.disableCplexOutput())
                cplex.setOut(null);
        } catch (IloException ex) {
            throw new OptException("error initializing CPLEX for quality checks");
        }
    }

    private void endCplex() {
        cplex.end();
        cplex = null;
    }

    public TestKPISet collectAverageTestStatsForBatchRun(
            RescheduleSolution rescheduleSolution) throws OptException {
        initCplex();
        logger.info("starting test runs for " + rescheduleSolution.getName());
        TestKPISet[] kpis = new TestKPISet[testScenarios.length];
        for (int j = 0; j < testScenarios.length; ++j) {
            logger.info("starting scenario " + j + " out of " + testScenarios.length);
            DelaySolution delaySolution = getDelaySolution(testScenarios[j], j,
                rescheduleSolution.getName(), rescheduleSolution.getReschedules());
            kpis[j] = delaySolution.getTestKPISet();
        }
        TestKPISet averageKPISet = new TestKPISet();
        averageKPISet.storeAverageKPIs(kpis);
        logger.info("completed test runs for " + rescheduleSolution.getName());
        endCplex();

        return averageKPISet;
    }

    private DelaySolution getDelaySolution(Scenario scen, int scenarioNum, String slnName,
                                           int[] proposedReschedules) {
        if (proposedReschedules != null) {
            for (Leg leg :dataRegistry.getLegs())
                leg.reschedule(proposedReschedules[leg.getIndex()]);
        }

        // solve routing MIP and collect solution
        int[] delays = scen.getPrimaryDelays();
        PathCache pathCache = new PathCache();
        pathCache.setCachedPaths(SolverUtility.getOriginalPaths(dataRegistry.getIdTailMap(),
                dataRegistry.getTailOrigPathMap(), delays));

        SubSolverRunnable ssr = new SubSolverRunnable(dataRegistry, 0, scenarioNum,
            scen.getProbability(), zeroReschedules, delays, pathCache);
        ssr.setCplex(cplex);
        ssr.setFilePrefix(slnName);
        ssr.setSolveForQuality(true);

        Instant start = Instant.now();
        ssr.run();
        double slnTime = Duration.between(start, Instant.now()).toMillis() / 1000.0;

        DelaySolution delaySolution = ssr.getDelaySolution();
        delaySolution.setSolutionTimeInSeconds(slnTime);

        if (proposedReschedules != null) {
            for (Leg leg : dataRegistry.getLegs())
                leg.revertReschedule();
        }

        return delaySolution;
    }
}
