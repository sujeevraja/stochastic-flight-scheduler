package stochastic.main;

import stochastic.delay.*;
import stochastic.domain.Leg;
import stochastic.dao.ScheduleDAO;
import stochastic.domain.Tail;
import stochastic.network.Path;
import stochastic.output.OutputManager;
import stochastic.output.QualityChecker;
import stochastic.output.RescheduleSolution;
import stochastic.output.TestKPISet;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.solver.BendersSolver;
import stochastic.solver.NaiveSolver;
import stochastic.solver.DepSolver;
import stochastic.utility.OptException;
import ilog.concert.IloException;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class Controller {
    /**
     * Class that controls the entire solution process from reading data to writing output.
     */
    private final static Logger logger = LogManager.getLogger(Controller.class);
    private DataRegistry dataRegistry;
    private OutputManager outputManager;

    private RescheduleSolution naiveModelSolution;
    private double naiveModelSolutionTime;

    private RescheduleSolution depSolution;
    private double depObjective;
    private double depSolutionTime;

    private RescheduleSolution bendersSolution;
    private double bendersSolutionTime;
    private double bendersLowerBound;
    private double bendersUpperBound;
    private double bendersGap;
    private int bendersNumIterations;
    private int bendersNumCuts;

    Controller() {
        dataRegistry = new DataRegistry();
        // String timeStamp = new SimpleDateFormat("yyyy_MM_dd'T'HH_mm_ss").format(new Date());
        outputManager = new OutputManager(dataRegistry);
    }

    DataRegistry getDataRegistry() {
        return dataRegistry;
    }

    final void readData() throws OptException {
        logger.info("Started reading data...");

        // Read leg data and remove unnecessary legs
        String instancePath = Parameters.getInstancePath();
        ArrayList<Leg> legs = new ScheduleDAO(instancePath + "/Schedule.xml").getLegs();
        storeLegs(legs);
        // limitNumTails();
        logger.info("Collected leg and tail data from Schedule.xml.");
        logger.info("completed reading data.");

        logger.info("started building connection network...");
        dataRegistry.buildConnectionNetwork();
        dataRegistry.getNetwork().countPathsForTails(dataRegistry.getTails());
        logger.info("built connection network.");
    }

    /**
     * Generates delay realizations and probabilities for second stage scenarios.
     */
    final void buildScenarios() {
        // DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getLegs().size(), dataRegistry.getTails());
        // DelayGenerator dgen = new TestDelayGenerator(dataRegistry.getLegs().size(), dataRegistry.getTails());
        DelayGenerator dgen = new StrategicDelayGenerator(dataRegistry.getLegs());

        dataRegistry.setDelayGenerator(dgen);
        Scenario[] scenarios = dgen.generateScenarios(Parameters.getNumScenariosToGenerate());
        dataRegistry.setDelayScenarios(scenarios);

        double avgTotalPrimaryDelay = 0.0;
        for (Scenario s : scenarios)
            avgTotalPrimaryDelay += s.getTotalPrimaryDelay();
        avgTotalPrimaryDelay /= scenarios.length;

        logger.info("average total primary delay (minutes): " + avgTotalPrimaryDelay);

        final int budget = (int) Math.round(avgTotalPrimaryDelay * Parameters.getRescheduleBudgetFraction());
        dataRegistry.setRescheduleTimeBudget(budget);
    }

    final void solveWithBenders() throws OptException {
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
            bendersGap = bendersSolver.getPercentGap();
            bendersNumIterations = bendersSolver.getIteration();
            bendersNumCuts = bendersSolver.getNumBendersCuts();
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("CPLEX error in Benders");
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to files during Benders");
        }
    }

    final double getBendersRescheduleCost() {
        return bendersSolution.getRescheduleCost();
    }

    final double getBendersSolutionTime() {
        return bendersSolutionTime;
    }

    final double getBendersLowerBound() {
        return bendersLowerBound;
    }

    final double getBendersUpperBound() {
        return bendersUpperBound;
    }

    final double getBendersGap() {
        return bendersGap;
    }

    final int getBendersNumCuts() {
        return bendersNumCuts;
    }

    final int getBendersNumIterations() {
        return bendersNumIterations;
    }

    final void solveWithNaiveApproach() throws OptException {
        try {
            NaiveSolver naiveSolver = new NaiveSolver(dataRegistry);
            naiveSolver.solve();
            naiveModelSolution = naiveSolver.getFinalRescheduleSolution();
            naiveModelSolutionTime = naiveSolver.getSolutionTime();
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("exception solving naive model");
        }
    }

    final double getNaiveModelRescheduleCost() {
        return naiveModelSolution.getRescheduleCost();
    }

    final double getNaiveModelSolutionTime() {
        return naiveModelSolutionTime;
    }

    final void solveWithDEP() throws OptException {
        DepSolver depSolver = new DepSolver();
        depSolver.solve(dataRegistry);
        depSolution = depSolver.getDepSolution();
        depObjective = depSolver.getObjValue();
        depSolutionTime = depSolver.getSolutionTimeInSeconds();
    }

    final double getDepRescheduleCost() {
        return depSolution.getRescheduleCost();
    }

    final double getDepSolutionTime() {
        return depSolutionTime;
    }

    /**
     * Processes the parsed data to populate data registry containers like legs, tails and on-plan paths.
     *
     * @param inputLegs list of legs parsed from a Schedule.xml file.
     */
    private void storeLegs(ArrayList<Leg> inputLegs) {
        ArrayList<Leg> legs = new ArrayList<>();
        HashMap<Integer, ArrayList<Leg>> tailHashMap = new HashMap<>();

        // Collect legs and build a mapping between tail and legs.
        Integer index = 0;
        for (Leg leg : inputLegs) {
            Integer tailId = leg.getOrigTailId();

            leg.setIndex(index);
            ++index;
            legs.add(leg);

            if (tailHashMap.containsKey(tailId))
                tailHashMap.get(tailId).add(leg);
            else {
                ArrayList<Leg> tailLegs = new ArrayList<>();
                tailLegs.add(leg);
                tailHashMap.put(tailId, tailLegs);
            }
        }

        dataRegistry.setLegs(legs);
        logger.info("Number of legs: " + legs.size());

        // build tails from schedule
        ArrayList<Tail> tails = new ArrayList<>();
        for (Map.Entry<Integer, ArrayList<Leg>> entry : tailHashMap.entrySet()) {
            ArrayList<Leg> tailLegs = entry.getValue();
            tailLegs.sort(Comparator.comparing(Leg::getDepTime));
            tails.add(new Tail(entry.getKey(), tailLegs));
        }

        tails.sort(Comparator.comparing(Tail::getId));
        for (int i = 0; i < tails.size(); ++i)
            tails.get(i).setIndex(i);

        dataRegistry.setTails(tails);
        dataRegistry.buildIdTailMap();
        logger.info("Number of tails: " + tails.size());

        HashMap<Integer, Path> tailPaths = new HashMap<>();
        for (Map.Entry<Integer, ArrayList<Leg>> entry : tailHashMap.entrySet()) {
            ArrayList<Leg> tailLegs = entry.getValue();
            tailLegs.sort(Comparator.comparing(Leg::getDepTime));

            // correct turn times as we assume that the input schedule is always valid.
            for(int i = 0; i < tailLegs.size() - 1; ++i) {
                Leg leg = tailLegs.get(i);
                Leg nextLeg =  tailLegs.get(i+1);
                int turnTime = (int) Duration.between(leg.getArrTime(), nextLeg.getDepTime()).toMinutes();
                if (turnTime < leg.getTurnTimeInMin()) {
                    logger.warn("turn after leg " + leg.getId() + " is shorter than its turn time "
                            + leg.getTurnTimeInMin() + ".");
                    logger.warn("shortening it to " + turnTime);
                    leg.setTurnTimeInMin(turnTime);
                }
            }

            Path p = new Path(dataRegistry.getTail(entry.getKey()));

            for (Leg l : tailLegs)
                p.addLeg(l, 0);

            tailPaths.put(entry.getKey(), p);
        }
        dataRegistry.setTailOrigPathMap(tailPaths);
    }

    /**
     * This function helps reduce problem size for debugging/testing purposes.
     */
    private void limitNumTails() {
        // limit the stored tails.
        ArrayList<Tail> newTails = new ArrayList<>();
        ArrayList<Tail> oldTails = dataRegistry.getTails();

        int tailIndex = 0;

        for (int i = 0; i < 60; ++i, ++tailIndex) { // this causes infeasible 2nd stage, check why
        // for (int i = 10; i < 60; ++i, ++tailIndex) {
        // for (int i = 20; i < 60; ++i, ++tailIndex) {
        // for (int i = 30; i < 60; ++i, ++tailIndex) {
        // for (int i = 40; i < 60; ++i, ++tailIndex) {
        // for (int i = 43; i < 60; ++i, ++tailIndex) {
        // for (int i = 44; i < 60; ++i, ++tailIndex) {
        // for (int i = 44; i < 55; ++i, ++tailIndex) {
        // for (int i = 44; i < 54; ++i, ++tailIndex) {
        // for (int i = 45; i < 54; ++i, ++tailIndex) {
            Tail tail = oldTails.get(i);
            tail.setIndex(tailIndex);
            newTails.add(tail);
            logger.debug("selected tail " + tail.getId());
        }
        dataRegistry.setTails(newTails);

        // cleanup tail paths.
        HashMap<Integer, Path> tailHashMap = dataRegistry.getTailOrigPathMap();
        HashMap<Integer, Path> newTailPathMap = new HashMap<>();
        for (Tail tail : newTails)
            newTailPathMap.put(tail.getId(), tailHashMap.get(tail.getId()));
        dataRegistry.setTailOrigPathMap(newTailPathMap);

        // cleanup legs.
        ArrayList<Leg> newLegs = new ArrayList<>();
        int legIndex = 0;
        for (Leg leg : dataRegistry.getLegs()) {
            Integer tailId = leg.getOrigTailId();
            if (newTailPathMap.containsKey(tailId)) {
                leg.setIndex(legIndex);
                newLegs.add(leg);
                ++legIndex;
            }
        }
        dataRegistry.setLegs(newLegs);
    }

    void processSolution() throws OptException {
        try {
            ArrayList<RescheduleSolution> rescheduleSolutions = getAllRescheduleSolutions();
            for (RescheduleSolution sln : rescheduleSolutions) {
                if (!sln.isOriginalSchedule()) {
                    sln.writeCSV(dataRegistry.getLegs());
                    logger.info("wrote " + sln.getName() + " reschedule solution");
                    outputManager.addKpi(sln.getName() + " reschedule cost", sln.getRescheduleCost());
                }
            }

            outputManager.addKpi("naive model solution time (seconds)", naiveModelSolutionTime);
            outputManager.addKpi("dep solution time (seconds)", depSolutionTime);
            outputManager.addKpi("dep objective", depObjective);
            outputManager.addKpi("benders solution time (seconds)", bendersSolutionTime);
            outputManager.addKpi("benders iterations", bendersNumIterations);
            outputManager.addKpi("benders lower bound", bendersLowerBound);
            outputManager.addKpi("benders upper bound", bendersUpperBound);
            outputManager.addKpi("benders gap (%)", bendersGap);
            outputManager.addKpi("benders cuts added", bendersNumCuts);
            outputManager.writeOutput();

            if (Parameters.isCheckSolutionQuality()) {
                QualityChecker qc = new QualityChecker(dataRegistry);
                qc.generateTestDelays();
                qc.compareSolutions(rescheduleSolutions);
            }
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing solution");
        }
    }

    TestKPISet[] getAverageTestStats() {
        QualityChecker qc = new QualityChecker(dataRegistry);
        ArrayList<RescheduleSolution> rescheduleSolutions = getAllRescheduleSolutions();
        return qc.collectAverageTestStatsForBatchRun(rescheduleSolutions);
    }

    ArrayList<RescheduleSolution> getAllRescheduleSolutions() {
        ArrayList<RescheduleSolution> rescheduleSolutions = new ArrayList<>();
        RescheduleSolution original = new RescheduleSolution("original", 0, null);
        original.setOriginalSchedule(true);
        rescheduleSolutions.add(original);
        if (naiveModelSolution != null)
            rescheduleSolutions.add(naiveModelSolution);

        if (depSolution != null)
            rescheduleSolutions.add(depSolution);

        if (bendersSolution != null)
            rescheduleSolutions.add(bendersSolution);

        return rescheduleSolutions;
    }
}
