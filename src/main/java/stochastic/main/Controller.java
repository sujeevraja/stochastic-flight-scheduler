package stochastic.main;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import stochastic.delay.*;
import stochastic.domain.Leg;
import stochastic.dao.ScheduleDAO;
import stochastic.domain.Tail;
import stochastic.network.Path;
import stochastic.output.OutputManager;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.solver.BendersSolver;
import stochastic.solver.NaiveSolver;
import stochastic.solver.DepSolver;
import stochastic.utility.OptException;
import ilog.concert.IloException;
import org.apache.commons.math3.distribution.LogNormalDistribution;

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

    Controller() {
        dataRegistry = new DataRegistry();
        outputManager = new OutputManager(dataRegistry);
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
        // LogNormalDistribution distribution = new LogNormalDistribution(Parameters.getScale(), Parameters.getShape());
        // DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), distribution);

        // DelayGenerator dgen = new TestDelayGenerator(dataRegistry.getTails());

        // TODO correct distributions later.
        RealDistribution distribution;
        switch (Parameters.getDistributionType()) {
            case EXPONENTIAL:
                distribution = new ExponentialDistribution(Parameters.getDistributionMean());
            case TRUNCATED_NORMAL:
                distribution = new ExponentialDistribution(Parameters.getDistributionMean());
            case LOGNORMAL:
                distribution = new ExponentialDistribution(Parameters.getDistributionMean());
            default:
                distribution = new ExponentialDistribution(Parameters.getDistributionMean());
        }
        NewDelayGenerator dgen = new NewDelayGenerator(distribution, dataRegistry.getLegs());

        Scenario[] scenarios = dgen.generateScenarios(Parameters.getNumScenariosToGenerate());
        dataRegistry.setDelayScenarios(scenarios);

        double avgTotalPrimaryDelay = 0.0;
        for (Scenario s : scenarios)
            avgTotalPrimaryDelay += s.getTotalPrimaryDelay();
        avgTotalPrimaryDelay /= scenarios.length;

        logger.info("average total primary delay (minutes): " + avgTotalPrimaryDelay);
    }

    final void solveWithBenders() throws OptException {
        try {
            BendersSolver bendersSolver = new BendersSolver(dataRegistry);
            bendersSolver.solve();
            outputManager.addRescheduleSolution(bendersSolver.getFinalRescheduleSolution());
            outputManager.addKpi("benders solution time (seconds)", bendersSolver.getSolutionTime());
            outputManager.addKpi("benders iterations", bendersSolver.getIteration());
            outputManager.addKpi("benders lower bound", bendersSolver.getLowerBound());
            outputManager.addKpi("benders upper bound", bendersSolver.getUpperBound());
            outputManager.addKpi("benders gap (%)", bendersSolver.getPercentGap());
            outputManager.addKpi("benders cuts added", bendersSolver.getNumBendersCuts());

            double[] thetas = bendersSolver.getFinalThetaValues();
            if (Parameters.isBendersMultiCut()) {
                for (int i = 0; i < dataRegistry.getDelayScenarios().length; ++i)
                    outputManager.addKpi("benders theta (scenario " + i + ")", thetas[i]);
            }
            else
                outputManager.addKpi("benders theta", thetas[0]);
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("CPLEX error in Benders");
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to files during Benders");
        }
    }

    final void solveWithNaiveApproach() throws OptException {
        try {
            NaiveSolver naiveSolver = new NaiveSolver(dataRegistry);
            naiveSolver.solve();
            outputManager.addRescheduleSolution(naiveSolver.getFinalRescheduleSolution());
            outputManager.addKpi("naive model solution time (seconds)", naiveSolver.getSolutionTime());
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("exception solving naive model");
        }
    }

    final void solveWithDEP() throws OptException {
        DepSolver depSolver = new DepSolver();
        depSolver.solve(dataRegistry);
        outputManager.addRescheduleSolution(depSolver.getDepSolution());
        outputManager.addKpi("dep solution time (seconds)", depSolver.getSolutionTimeInSeconds());
        outputManager.addKpi("dep objective", depSolver.getObjValue());
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
                    logger.warn("shorterning it to " + turnTime);
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
            outputManager.writeOutput();
            if (Parameters.isCheckSolutionQuality())
                outputManager.checkSolutionQuality();
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing solution");
        }
    }
}
