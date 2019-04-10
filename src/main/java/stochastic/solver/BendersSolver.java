package stochastic.solver;

import stochastic.delay.Scenario;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Path;
import stochastic.output.RescheduleSolution;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.CSVHelper;
import stochastic.utility.OptException;
import ilog.concert.IloException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

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
                writeBendersCut(iteration, -1, cut.getBeta(), cut.getAlpha());
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
        ArrayList<String> cutRow = new ArrayList<>(Arrays.asList("iter", "scenario"));
        ArrayList<String> slnRow = new ArrayList<>(Collections.singletonList("iter"));
        int[] durations = Parameters.getDurations();
        ArrayList<Leg> legs = dataRegistry.getLegs();
        for (int duration : durations) {
            for (Leg leg : legs) {
                String name = "x_" + duration + "_" + leg.getId();
                cutRow.add(name);
                slnRow.add(name);
            }
        }

        CSVHelper.writeLine(slnWriter, slnRow);
        cutRow.add("rhs");
        CSVHelper.writeLine(cutWriter, cutRow);
    }

    private void writeMasterSolution(int iter, double[] xValues) throws IOException {
        ArrayList<Leg> legs = dataRegistry.getLegs();

        ArrayList<String> row = new ArrayList<>();
        row.add(Integer.toString(iter));

        for (int j = 0; j < legs.size(); ++j)
            row.add(Double.toString(xValues[j]));

        row.add("\n");
        CSVHelper.writeLine(slnWriter, row);
    }

    private void writeBendersCut(int iter, int cutIndex, double[] beta, double alpha) throws IOException {
        ArrayList<Leg> legs = dataRegistry.getLegs();

        ArrayList<String> row = new ArrayList<>();
        row.add(Integer.toString(iter));
        row.add(Integer.toString(cutIndex));

        for (int j = 0; j < legs.size(); ++j) {
            row.add(Double.toString(beta[j]));
        }

        row.add(Double.toString(alpha));
        CSVHelper.writeLine(cutWriter, row);
    }

    public double getLowerBound() {
        return lowerBound;
    }

    public double getUpperBound() {
        return upperBound;
    }
}
