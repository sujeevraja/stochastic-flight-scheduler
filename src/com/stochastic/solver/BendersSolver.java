package com.stochastic.solver;

import com.stochastic.delay.Scenario;
import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.postopt.RescheduleSolution;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.OptException;
import ilog.concert.IloException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BendersSolver {
    /**
     * Class that solves the 2-stage stochastic optimization problem using Benders decomposition.
     */
    private final static Logger logger = LogManager.getLogger(BendersSolver.class);
    private DataRegistry dataRegistry;
    private BufferedWriter cutWriter;
    private BufferedWriter slnWriter;

    private MasterSolver masterSolver;
    private int iteration;
    private double lowerBound;
    private double upperBound;
    private PathCache[] secondStageCaches;

    private RescheduleSolution finalRescheduleSolution;
    private double[] finalThetaValues;
    private int numBendersCuts;
    private double solutionTime;
    private double percentGap;

    public BendersSolver(DataRegistry dataRegistry) throws IOException {
        this.dataRegistry = dataRegistry;
        if (Parameters.isDebugVerbose()) {
            cutWriter = new BufferedWriter(new FileWriter("logs/master__cuts.csv"));
            slnWriter = new BufferedWriter(new FileWriter("logs/master__solutions.csv"));
        }
        iteration = 0;
        lowerBound = -Double.MAX_VALUE;
        upperBound = Double.MAX_VALUE;
        secondStageCaches = new PathCache[dataRegistry.getDelayScenarios().length];
        solutionTime = 0.0;
    }

    public RescheduleSolution getFinalRescheduleSolution() {
        return finalRescheduleSolution;
    }

    public double[] getFinalThetaValues() {
        return finalThetaValues;
    }

    public int getNumBendersCuts() {
        return numBendersCuts;
    }

    public double getSolutionTime() {
        return solutionTime;
    }

    public int getIteration() {
        return iteration;
    }

    public double getPercentGap() {
        return percentGap;
    }

    public void solve() throws IloException, IOException, OptException {
        Instant start = Instant.now();
        ArrayList<Leg> legs = dataRegistry.getLegs();
        ArrayList<Tail> tails = dataRegistry.getTails();
        int[] durations = Parameters.getDurations();

        // Solve master problem once to initialize the algorithm.
        masterSolver = new MasterSolver(legs, tails, durations, dataRegistry.getDelayScenarios().length);
        masterSolver.constructFirstStage();

        if (Parameters.isDebugVerbose()) {
            writeCsvHeaders();
            masterSolver.writeLPFile("logs/master__before_cuts.lp");
        }

        masterSolver.solve(iteration);
        if (Parameters.isDebugVerbose())
            writeMasterSolution(iteration, masterSolver.getxValues());

        masterSolver.addTheta();

        logger.info("algorithm starts.");
        if (Parameters.isFullEnumeration())
            logger.info("pricing problem strategy: full enumeration");
        else
            logger.info("pricing problem strategy: labeling, " + Parameters.getReducedCostStrategy());

        cacheOnPlanPathsForSecondStage();

        // Run Benders iterations until the stopping condition is reached.
        do { runBendersIteration();
        } while (!stoppingConditionReached());

        Instant end = Instant.now();

        solutionTime = Duration.between(start, end).toMillis() / 1000.0;
        logger.info("Benders solution time: " + solutionTime + " seconds");

        storeFinalSolution();

        masterSolver.end();
        masterSolver = null;
        logger.info("algorithm ends.");

        if (Parameters.isDebugVerbose()) {
            cutWriter.close();
            slnWriter.close();
        }
    }

    /**
     * This function caches original paths with propagated delays and empty paths for each scenario.
     */
    private void cacheOnPlanPathsForSecondStage() {
        Scenario[] scenarios = dataRegistry.getDelayScenarios();
        for (int i = 0; i < scenarios.length; ++i) {
            secondStageCaches[i] = new PathCache();
            HashMap<Integer, ArrayList<Path>> origpaths = SolverUtility.getOriginalPaths(dataRegistry.getIdTailMap(),
                    dataRegistry.getTailOrigPathMap(), scenarios[i].getPrimaryDelays());
            secondStageCaches[i].setCachedPaths(origpaths);
        }
    }

    private void runBendersIteration() throws IloException, IOException, OptException {
        ++iteration;
        SubSolverWrapper ssWrapper = new SubSolverWrapper(dataRegistry, masterSolver.getReschedules(), iteration,
                masterSolver.getRescheduleCost(), secondStageCaches);

        if (Parameters.isRunSecondStageInParallel())
            ssWrapper.solveParallel();
        else
            ssWrapper.solveSequential();

        BendersData bendersData = ssWrapper.getBendersData();
        if (Parameters.isBendersMultiCut()) {
            ArrayList<BendersCut> cuts = bendersData.getCuts();
            double[] thetaValues = masterSolver.getThetaValues();

            for (int i = 0; i < dataRegistry.getDelayScenarios().length; ++i) {
                BendersCut cut = bendersData.getCut(i);
                if (thetaValues == null || cut.separates(masterSolver.getxValues(), thetaValues[i])) {
                    masterSolver.addBendersCut(cut, i);
                    if (Parameters.isDebugVerbose())
                        writeBendersCut(iteration, i, cut.getBeta(), cut.getAlpha());
                }
            }
        }
        else {
            BendersCut cut = bendersData.getCut(0);
            masterSolver.addBendersCut(cut, 0);
            if (Parameters.isDebugVerbose())
                writeBendersCut(iteration, 0, cut.getBeta(), cut.getAlpha());
        }

        if (Parameters.isDebugVerbose()) {
            masterSolver.writeLPFile("logs/master_" + iteration + ".lp");
        }

        masterSolver.solve(iteration);

        if (Parameters.isDebugVerbose()) {
            masterSolver.writeCPLEXSolution("logs/master_" + iteration + ".xml");
            writeMasterSolution(iteration, masterSolver.getxValues());
        }

        lowerBound = masterSolver.getObjValue();

        logger.info("----- iteration: " + iteration);
        logger.info("----- lower bound: " + lowerBound);
        logger.info("----- upper bound: " + upperBound);
        logger.info("----- upper bound from subsolver: " + bendersData.getUpperBound());
        logger.info("----- number of cuts added: " + masterSolver.getNumBendersCuts());

        if (bendersData.getUpperBound() < upperBound)
            upperBound = bendersData.getUpperBound();

        logger.info("----- updated upper bound: " + upperBound);
    }

    private boolean stoppingConditionReached() {
        double diff = upperBound - lowerBound;
        double tolerance = Parameters.getBendersTolerance() * upperBound;
        percentGap = (diff * 100.0 / upperBound);
        logger.info("----- diff: " + diff + " tolerance: " + tolerance);
        logger.info("----- Benders gap (%): " + percentGap);
        if (iteration >= Parameters.getNumBendersIterations()) {
            logger.info("----- benders iteration limit reached");
            return true;
        }
        return diff <= tolerance;
    }

    private void storeFinalSolution() {
        finalRescheduleSolution = new RescheduleSolution("benders", masterSolver.getRescheduleCost(),
                masterSolver.getReschedules());
        finalThetaValues = masterSolver.getThetaValues();
        numBendersCuts = masterSolver.getNumBendersCuts();
    }

    private void writeCsvHeaders() throws IOException {
        StringBuilder row = new StringBuilder();
        row.append("iter");
        int[] durations = Parameters.getDurations();
        ArrayList<Leg> legs = dataRegistry.getLegs();
        for (int duration : durations) {
            for (Leg leg : legs) {
                row.append(",x_");
                row.append(duration);
                row.append("_");
                row.append(leg.getId());
            }
        }

        slnWriter.write(row.toString());
        slnWriter.write("\n");
        cutWriter.write(row.toString());
        cutWriter.write(",rhs\n");
    }

    private void writeMasterSolution(int iter, double[][] xValues) throws IOException {
        int[] durations = Parameters.getDurations();
        ArrayList<Leg> legs = dataRegistry.getLegs();

        StringBuilder row = new StringBuilder();
        row.append(iter);

        for (int i = 0; i < durations.length; ++i) {
            for (int j = 0; j < legs.size(); ++j) {
                row.append(",");
                row.append(xValues[i][j]);
            }
        }

        row.append("\n");
        slnWriter.write(row.toString());
    }

    private void writeBendersCut(int iter, int cutIndex, double[][] beta, double alpha) throws IOException {
        int[] durations = Parameters.getDurations();
        ArrayList<Leg> legs = dataRegistry.getLegs();

        StringBuilder row = new StringBuilder();
        row.append(iter);

        for (int i = 0; i < durations.length; ++i) {
            for (int j = 0; j < legs.size(); ++j) {
                row.append(",");
                row.append(beta[i][j]);
            }
        }

        row.append(",");
        row.append(alpha);
        row.append("\n");

        cutWriter.write(row.toString());
    }
}
