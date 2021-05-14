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

    final void buildScenarios() throws OptException {
        if (Parameters.isParsePrimaryDelaysFromFiles())
            parsePrimaryDelaysFromFiles();
        else
            buildScenariosFromDistribution();
    }

    /**
     * Generates delay realizations and probabilities for second stage scenarios.
     */
    private void buildScenariosFromDistribution() {
        DelayGenerator dgen = dataRegistry.getDelayGenerator();
        Scenario[] scenarios = dgen.generateScenarios(Parameters.getNumSecondStageScenarios());
        dataRegistry.setDelayScenarios(scenarios);

        double avgTotalPrimaryDelay = 0.0;
        for (Scenario s : scenarios)
            avgTotalPrimaryDelay += s.getTotalPrimaryDelay();
        avgTotalPrimaryDelay /= scenarios.length;

        logger.info("average total primary delay (minutes): " + avgTotalPrimaryDelay);

        final int budget = (int) Math.round(avgTotalPrimaryDelay * Parameters.getRescheduleBudgetFraction());
        dataRegistry.setRescheduleTimeBudget(budget);
    }

    final void writeScenariosToFile() throws OptException {
        logger.info("starting scenario writing...");
        Scenario[] scenarios = dataRegistry.getDelayScenarios();
        ArrayList<Leg> legs = dataRegistry.getLegs();
        String prefix = Parameters.getOutputPath() + "/primary_delays_";
        String suffix = ".csv";
        List<String> headers = new ArrayList<>(Arrays.asList("leg_id", "delay_minutes"));

        for (int i = 0; i < scenarios.length; ++i) {
            String filePath = prefix + i + suffix;
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
                CSVHelper.writeLine(writer, headers);

                int[] primaryDelays = scenarios[i].getPrimaryDelays();
                for (int j = 0; j < primaryDelays.length; ++j) {
                    if (primaryDelays[j] > 0) {
                        Leg leg = legs.get(j);
                        List<String> line = new ArrayList<>(Arrays.asList(
                            leg.getId().toString(),
                            Integer.toString(primaryDelays[j])));

                        CSVHelper.writeLine(writer, line);
                    }
                }

                writer.close();
            } catch (IOException ex) {
                logger.error(ex);
                throw new OptException("error writing primary delays to file");
            }

        }
        logger.info("completed scenario writing.");
    }

    private void parsePrimaryDelaysFromFiles() throws OptException {
        logger.info("staring primary delay parsing...");
        ArrayList<Leg> legs = dataRegistry.getLegs();
        final int numScenarios = Parameters.getNumSecondStageScenarios();
        final double probability = 1.0 / numScenarios;
        Scenario[] scenarios = new Scenario[numScenarios];
        final String prefix = Parameters.getOutputPath() + "/primary_delays_";
        final String suffix = ".csv";

        // Build map from leg id to index for delay data collection.
        Map<Integer, Integer> legIdIndexMap = new HashMap<>();
        for (int i = 0; i < legs.size(); ++i)
            legIdIndexMap.put(legs.get(i).getId(), i);

        double avgTotalPrimaryDelay = 0.0;

        for (int i = 0; i < numScenarios; ++i) {
            // read delay data
            String filePath = prefix + i + suffix;
            Map<Integer, Integer> primaryDelayMap = new HashMap<>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(filePath));
                CSVHelper.parseLine(reader); // parse once to skip headers

                while (true) {
                    List<String> line = CSVHelper.parseLine(reader);
                    if (line == null)
                        break;

                    int legId = Integer.parseInt(line.get(0));
                    int primaryDelay = Integer.parseInt(line.get(1));
                    primaryDelayMap.put(legId, primaryDelay);
                }

                reader.close();
            } catch (IOException ex) {
                logger.error(ex);
                throw new OptException("error reading primary delays from file");
            }

            // build scenario, store it
            int[] delays = new int[legs.size()];
            Arrays.fill(delays, 0);
            for (Map.Entry<Integer, Integer> entry : primaryDelayMap.entrySet())
                delays[legIdIndexMap.get(entry.getKey())] = entry.getValue();

            scenarios[i] = new Scenario(probability, delays);
            avgTotalPrimaryDelay += scenarios[i].getTotalPrimaryDelay();
        }

        dataRegistry.setDelayScenarios(scenarios);

        avgTotalPrimaryDelay /= scenarios.length;
        logger.info("average total primary delay (minutes): " + avgTotalPrimaryDelay);

        final int budget = (int) Math.round(
            avgTotalPrimaryDelay * Parameters.getRescheduleBudgetFraction());
        dataRegistry.setRescheduleTimeBudget(budget);
        logger.info("completed primary delay parsing.");
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
