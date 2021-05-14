package stochastic.main;

import ilog.concert.IloException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.dao.ScheduleDAO;
import stochastic.delay.DelayGenerator;
import stochastic.delay.Scenario;
import stochastic.delay.StrategicDelayGenerator;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Network;
import stochastic.output.RescheduleSolution;
import stochastic.registry.DataRegistry;
import stochastic.registry.DataRegistryBuilder;
import stochastic.registry.Parameters;
import stochastic.solver.BendersSolver;
import stochastic.solver.DepSolver;
import stochastic.solver.NaiveSolver;
import stochastic.solver.UpperBoundSolver;
import stochastic.utility.CSVHelper;
import stochastic.utility.Enums;
import stochastic.utility.OptException;

import java.io.*;
import java.util.*;

class Controller {
    /**
     * Class that controls the entire solution process from reading data to writing output.
     */
    private final static Logger logger = LogManager.getLogger(Controller.class);
    private final DataRegistry dataRegistry;

    private ModelStats naiveModelStats;
    private RescheduleSolution naiveModelSolution;
    private double naiveModelSolutionTime;

    private ModelStats depModelStats;
    private RescheduleSolution depSolution;
    private double depSolutionTime;

    private RescheduleSolution bendersSolution;
    private double bendersSolutionTime;
    private double bendersLowerBound;
    private double bendersUpperBound;
    private double bendersGlobalUpperBound;
    private double bendersGap;
    private double bendersOptimalityGap;

    private int bendersNumIterations;
    private int bendersNumCuts;

    Controller() throws OptException {
        logger.info("Started reading data...");
        final String filePath = Parameters.getInstancePath() + "/" + Parameters.getInstanceName();
        logger.debug("instance path: " + filePath);
        ArrayList<Leg> legs = new ScheduleDAO(filePath).getLegs();
        dataRegistry = (new DataRegistryBuilder(legs)).dataRegistry;
        logger.info("completed reading data.");
    }

    DataRegistry getDataRegistry() {
        return dataRegistry;
    }

    final void computeStats() {
        final ArrayList<Leg> legs = dataRegistry.getLegs();
        final ArrayList<Tail> tails = dataRegistry.getTails();
        final Network network = dataRegistry.getNetwork();
        final TreeMap<String, Object> stats = new TreeMap<>();
        stats.put("numLegs", legs.size());
        stats.put("numTails", tails.size());
        stats.put("numPaths", network.countPathsForTails(tails));
        stats.put("numConnections", network.getNumConnections());

        int hubVisits = 0;
        int nonHubVisits = 0;
        final int hub = dataRegistry.getHub();
        for (Leg leg : legs) {
            if (leg.getDepPort().equals(hub))
                ++hubVisits;
            else
                ++nonHubVisits;
        }
        stats.put("numHubVisits", hubVisits);
        stats.put("numNonHubVisits", nonHubVisits);
        stats.put("hubVisitPercent", hubVisits * 100.0 / (hubVisits + nonHubVisits));
        stats.put("numRoundTripsToHub", network.computeNumRoundTripsTo(hub));
        stats.put("numRoundTrips", network.computeNumRoundTripsTo(null));
        stats.put("networkDensity", network.computeDensity());
        for (Map.Entry<String, Object> entry : stats.entrySet())
            logger.info(entry.getKey() + " " + entry.getValue());
    }

    final void setDelayGenerator() {
        // DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getLegs().size(), dataRegistry.getTails());
        // DelayGenerator dgen = new TestDelayGenerator(dataRegistry.getLegs().size(), dataRegistry.getTails());
        DelayGenerator dgen = new StrategicDelayGenerator(dataRegistry.getLegs(), dataRegistry.getHub());
        dataRegistry.setDelayGenerator(dgen);
    }

    final void solve() throws OptException {
        Enums.Model model = Parameters.getModel();
        if (model == Enums.Model.BENDERS) solveWithBenders();
        else if (model == Enums.Model.DEP) solveWithDEP();
        else if (model == Enums.Model.NAIVE) solveWithNaiveApproach();
        else {
            logger.info("nothing to solve for model type " + model);
        }
    }

    private void solveWithBenders() throws OptException {
        try {
            BendersSolver bendersSolver = new BendersSolver(dataRegistry);
            if (Parameters.isWarmStartBenders())
                bendersSolver.solve(naiveModelSolution);
            else
                bendersSolver.solve(null);

            bendersSolution = bendersSolver.getFinalRescheduleSolution();
            bendersSolutionTime = bendersSolver.getSolutionTime();
            bendersLowerBound = bendersSolver.getLowerBound();
            bendersUpperBound = bendersSolver.getUpperBound();
            bendersNumIterations = bendersSolver.getIteration();
            bendersNumCuts = bendersSolver.getNumBendersCuts();

            bendersGlobalUpperBound = (
                new UpperBoundSolver(dataRegistry, bendersSolution)).findUpperBound();
            bendersGap = bendersSolver.getPercentGap();
            bendersOptimalityGap = ((bendersGlobalUpperBound - bendersLowerBound) /
                bendersGlobalUpperBound) * 100.0;

            logger.debug("Benders global upper bound: " + bendersGlobalUpperBound);
            logger.debug("Benders global optimality gap: " +
                String.format("%.2f", bendersOptimalityGap) + " %");
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("CPLEX error in Benders");
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to files during Benders");
        }
    }

    private void solveWithNaiveApproach() throws OptException {
        try {
            NaiveSolver naiveSolver = new NaiveSolver(dataRegistry);
            naiveSolver.solve();
            naiveModelStats = naiveSolver.getModelStats();
            naiveModelSolution = naiveSolver.getFinalRescheduleSolution();
            naiveModelSolutionTime = naiveSolver.getSolutionTime();
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("exception solving naive model");
        }
    }

    final ModelStats getNaiveModelStats() {
        return naiveModelStats;
    }

    final double getNaiveModelRescheduleCost() {
        return naiveModelSolution.getRescheduleCost();
    }

    final double getNaiveModelSolutionTime() {
        return naiveModelSolutionTime;
    }

    private void solveWithDEP() throws OptException {
        DepSolver depSolver = new DepSolver();
        depSolver.solve(dataRegistry);
        depModelStats = depSolver.getModelStats();
        depSolution = depSolver.getDepSolution();
        depSolutionTime = depSolver.getSolutionTimeInSeconds();
    }

    ModelStats getDepModelStats() {
        return depModelStats;
    }

    final double getDepRescheduleCost() {
        return depSolution.getRescheduleCost();
    }

    final double getDepSolutionTime() {
        return depSolutionTime;
    }

    void writeRescheduleSolutions() throws OptException {
        try {
            if (naiveModelSolution != null)
                naiveModelSolution.writeCSV(dataRegistry.getLegs());

            if (depSolution != null)
                depSolution.writeCSV(dataRegistry.getLegs());

            if (bendersSolution != null)
                bendersSolution.writeCSV(dataRegistry.getLegs());
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("problem writing reschedule solutions to csv");
        }
    }

    HashMap<String, Object> getBendersResults() {
        HashMap<String, Object> results = Parameters.asMap();
        results.put("bendersRescheduleCost", bendersSolution.getRescheduleCost());
        results.put("bendersSolutionTime", bendersSolutionTime);
        results.put("bendersLowerBound", bendersLowerBound);
        results.put("bendersUpperBound", bendersUpperBound);
        results.put("bendersGlobalUpperBound", bendersGlobalUpperBound);
        results.put("bendersGap", bendersGap);
        results.put("bendersOptimalityGap", bendersOptimalityGap);
        results.put("bendersIterations", bendersNumIterations);
        results.put("bendersNumCuts", bendersNumCuts);
        return results;
    }
}
