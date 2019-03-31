package com.stochastic.solver;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.postopt.Solution;
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
import java.util.ArrayList;

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

    private Solution finalSolution;
    private double solutionTime;

    public BendersSolver(DataRegistry dataRegistry) throws IOException {
        this.dataRegistry = dataRegistry;
        if (Parameters.isDebugVerbose()) {
            cutWriter = new BufferedWriter(new FileWriter("logs/master__cuts.csv"));
            slnWriter = new BufferedWriter(new FileWriter("logs/master__solutions.csv"));
        }
        iteration = -1;
        lowerBound = -Double.MAX_VALUE;
        upperBound = Double.MAX_VALUE;
        solutionTime = 0.0;
    }

    public Solution getFinalSolution() {
        return finalSolution;
    }

    public double getSolutionTime() {
        return solutionTime;
    }

    public void solve() throws IloException, IOException, OptException {
        Instant start = Instant.now();
        ArrayList<Leg> legs = dataRegistry.getLegs();
        ArrayList<Tail> tails = dataRegistry.getTails();
        int[] durations = Parameters.getDurations();

        // Solve master problem once to initialize the algorithm.
        masterSolver = new MasterSolver(legs, tails, durations);
        masterSolver.constructFirstStage();

        if (Parameters.isDebugVerbose()) {
            writeCsvHeaders();
            masterSolver.writeLPFile("logs/master__before_cuts.lp");
        }

        masterSolver.solve(iteration);
        if (Parameters.isDebugVerbose())
            writeMasterSolution(iteration, masterSolver.getxValues());

        masterSolver.addColumn();

        logger.info("algorithm starts.");
        if (Parameters.isFullEnumeration())
            logger.info("pricing problem strategy: full enumeration");
        else
            logger.info("pricing problem strategy: labeling, " + Parameters.getReducedCostStrategy());

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

    private void runBendersIteration() throws IloException, IOException, OptException {
        ++iteration;
        SubSolverWrapper ssWrapper = new SubSolverWrapper(dataRegistry, masterSolver.getReschedules(), iteration,
                masterSolver.getFirstStageObjValue());

        if (Parameters.isRunSecondStageInParallel())
            ssWrapper.solveParallel();
        else
            ssWrapper.solveSequential();

        BendersData bendersData = ssWrapper.getBendersData();
        masterSolver.constructBendersCut(bendersData.getAlpha(), bendersData.getBeta());

        if (Parameters.isDebugVerbose()) {
            masterSolver.writeLPFile("logs/master_" + iteration + ".lp");
            writeBendersCut(iteration, bendersData.getBeta(), bendersData.getAlpha());
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

        if (bendersData.getUpperBound() < upperBound)
            upperBound = bendersData.getUpperBound();

        logger.info("----- updated upper bound: " + upperBound);
    }

    private boolean stoppingConditionReached() {
        double diff = upperBound - lowerBound;
        double tolerance = Parameters.getBendersTolerance() * Math.abs(upperBound);
        logger.info("----- diff: " + diff + " tolerance: " + tolerance);
        return diff <= tolerance; // && (System.currentTimeMillis() - Optimizer.stTime)/1000 < Optimizer.runTime); // && iter < 10);
    }

    private void storeFinalSolution() {
        finalSolution = new Solution(masterSolver.getFirstStageObjValue(), masterSolver.getReschedules());
        finalSolution.setThetaValue(masterSolver.getThetaValue());
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

    private void writeBendersCut(int iter, double[][] beta, double alpha) throws IOException {
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
