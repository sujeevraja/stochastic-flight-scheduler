package com.stochastic.controller;

import com.stochastic.domain.Leg;
import com.stochastic.dao.ScheduleDAO;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.postopt.NewSolutionManager;
import com.stochastic.postopt.Solution;
import com.stochastic.postopt.SolutionManager;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.solver.BendersData;
import com.stochastic.solver.MasterSolver;
import com.stochastic.solver.SubSolverWrapper;
import com.stochastic.utility.OptException;
import ilog.concert.IloException;
import org.apache.commons.math3.distribution.LogNormalDistribution;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Controller {
    /**
     * Class that controls the entire solution process from reading data to writing output.
     */
    private final static Logger logger = LogManager.getLogger(Controller.class);
    private DataRegistry dataRegistry;
    private String instancePath;
    private BufferedWriter cutWriter;
    private BufferedWriter slnWriter;

    public static ArrayList<Double> delayResults = new ArrayList<>();

    public static int[][] sceVal;

    // Members to process solution and output
    private Solution bendersSolution;

    class Scenarios {
        /**
         * Class that holds generate delays and probabilities of second-stage scenarios.
         *
         * This class is only for use within Controller. After scenario generation and aggregation of equal probability
         * scenarios, the updated data will be stored in the dataRegistry.
         */
        ArrayList<Integer> delays;
        ArrayList<Double> probabilities;

        Scenarios(ArrayList<Integer> delays, ArrayList<Double> probabilities) {
            this.delays = delays;
            this.probabilities = probabilities;
        }
    }

    public Controller() throws IOException {
        dataRegistry = new DataRegistry();
        if (Parameters.isDebugVerbose()) {
            cutWriter = new BufferedWriter(new FileWriter("logs/master__cuts.csv"));
            slnWriter = new BufferedWriter(new FileWriter("logs/master__solutions.csv"));
        }
    }

    public final void readData(String instancePath) throws OptException {
        logger.info("Started reading data...");
        this.instancePath = instancePath;

        // Read leg data and remove unnecessary legs
        ArrayList<Leg> legs = new ScheduleDAO(instancePath + "\\Schedule.xml").getLegs();
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
    public final void buildScenarios() {
        // Scenarios scenarios = generateScenarioDelays(Parameters.getScale(), Parameters.getShape());
        Scenarios scenarios = generateTestDelays();

        dataRegistry.setNumScenarios(scenarios.delays.size());

        int[] scenarioDelays = new int[scenarios.delays.size()];
        for(int i = 0; i < scenarioDelays.length; ++i)
            scenarioDelays[i] = scenarios.delays.get(i);
        dataRegistry.setScenarioDelays(scenarioDelays);

        double[] scenarioProbabilities = new double[scenarios.probabilities.size()];
        for (int j = 0; j < scenarioProbabilities.length; ++j)
            scenarioProbabilities[j] = scenarios.probabilities.get(j);
        dataRegistry.setScenarioProbabilities(scenarioProbabilities);

        logScenarioDelays();
    }

    public final void solve() throws IOException, IloException, OptException {
        ArrayList<Leg> legs = dataRegistry.getLegs();
        ArrayList<Tail> tails = dataRegistry.getTails();
        int[] durations = Parameters.getDurations();

        int iter = -1;
        MasterSolver masterSolver = new MasterSolver(legs, tails, durations);
        masterSolver.constructFirstStage();

        if (Parameters.isDebugVerbose()) {
            writeCsvHeaders();
            masterSolver.writeLPFile("logs/master__before_cuts.lp");
        }

        masterSolver.solve(iter);

        if (Parameters.isDebugVerbose())
            writeMasterSolution(iter, masterSolver.getxValues());

        masterSolver.addColumn();

        logger.info("algorithm starts.");
        if (Parameters.isFullEnumeration())
            logger.info("pricing problem strategy: full enumeration");
        else
            logger.info("pricing problem strategy: labeling, " + Parameters.getReducedCostStrategy());

        double lBound;
        double uBound = Double.MAX_VALUE;
        boolean stoppingCondition;
        do {
            iter++;
            SubSolverWrapper ssWrapper = new SubSolverWrapper(dataRegistry, masterSolver.getReschedules(), iter,
                    masterSolver.getFirstStageObjValue());

            if (Parameters.isRunSecondStageInParallel())
                ssWrapper.solveParallel();
            else
                ssWrapper.solveSequential();

            BendersData bendersData = ssWrapper.getBendersData();
            masterSolver.constructBendersCut(bendersData.getAlpha(), bendersData.getBeta());

            if (Parameters.isDebugVerbose()) {
                masterSolver.writeLPFile("logs/master_" + iter + ".lp");
                writeBendersCut(iter, bendersData.getBeta(), bendersData.getAlpha());
            }

            masterSolver.solve(iter);

            if (Parameters.isDebugVerbose()) {
                masterSolver.writeCPLEXSolution("logs/master_" + iter + ".xml");
                writeMasterSolution(iter, masterSolver.getxValues());
            }

            lBound = masterSolver.getObjValue();

            if (bendersData.getUpperBound() < uBound)
                uBound = bendersData.getUpperBound();

            logger.info("----- LB: " + lBound + " UB: " + uBound + " Iter: " + iter
                    + " bendersData.getUpperBound(): " + bendersData.getUpperBound());

            double diff = uBound - lBound;
            double tolerance = Parameters.getBendersTolerance() * Math.abs(uBound);
            stoppingCondition = diff <= tolerance;
            logger.info("----- diff: " + diff + " tolerance: " + tolerance + " stop: " + stoppingCondition);
        } while (!stoppingCondition); // && (System.currentTimeMillis() - Optimizer.stTime)/1000 < Optimizer.runTime); // && iter < 10);

        // store final solution
        bendersSolution = new Solution(masterSolver.getFirstStageObjValue(), dataRegistry.getLegs(),
                masterSolver.getReschedules());
        bendersSolution.setThetaValue(masterSolver.getThetaValue());

        masterSolver.end();
        logger.info("algorithm ends.");

        if (Parameters.isDebugVerbose()) {
            cutWriter.close();
            slnWriter.close();
        }
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

    /**
     * This funciton is an alternative to generateScenarioDelays() and can be used to study a specific set of random
     * delay scenarios.
     */
    private Scenarios generateTestDelays() {
        ArrayList<Integer> scenarioDelays;
        ArrayList<Double> scenarioProbabilities;

        // scenarioDelays = new ArrayList<>(Collections.singletonList(45));
        // scenarioProbabilities = new ArrayList<>(Collections.singletonList(1.0));

        scenarioDelays = new ArrayList<>(Arrays.asList(45, 60));
        scenarioProbabilities = new ArrayList<>(Arrays.asList(0.5, 0.5));

        // scenarioDelays = new ArrayList<>(Arrays.asList(22, 23, 30, 32, 33, 34, 36, 46, 52));
        // scenarioProbabilities = new ArrayList<>(Arrays.asList(0.1, 0.1, 0.1, 0.2, 0.1, 0.1, 0.1, 0.1, 0.1));

        return new Scenarios(scenarioDelays, scenarioProbabilities);
    }

    /**
     * Generates random delays that will be applied to the first flight of each tail's original schedule.
     * Also generates delay probabilites using frequency values. This function also updates the number of scenarios
     * if delay times repeat.
     *
     * @param scale scale parameter of the lognormal distribution used to generate random delays
     * @param shape shape parameter of the distribution
     * @return generated scenarios
     */
    private Scenarios generateScenarioDelays(double scale, double shape) {
        int numSamples = Parameters.getNumScenariosToGenerate();
        LogNormalDistribution logNormal = new LogNormalDistribution(scale, shape);

        int[] delayTimes = new int[numSamples];
        for (int i = 0; i < numSamples; ++i)
            delayTimes[i] = (int) Math.round(logNormal.sample());

        Arrays.sort(delayTimes);
        ArrayList<Integer> delays = new ArrayList<>();
        ArrayList<Double> probabilities = new ArrayList<>();

        DecimalFormat df = new DecimalFormat("##.##");
        df.setRoundingMode(RoundingMode.HALF_UP);

        final double baseProbability = 1.0 / numSamples;
        int numCopies = 1;

        delays.add(delayTimes[0]);
        int prevDelayTime = delayTimes[0];
        for (int i = 1; i < numSamples; ++i) {
            int delayTime = delayTimes[i];

            if (delayTime != prevDelayTime) {
                final double prob = Double.parseDouble(df.format(numCopies * baseProbability));
                probabilities.add(prob); // add probabilities for previous time.
                delays.add(delayTime); // add new delay time.
                numCopies = 1;
            } else
                numCopies++;

            prevDelayTime = delayTime;
        }
        probabilities.add(numCopies * baseProbability);

        return new Scenarios(delays, probabilities);
    }

    private void logScenarioDelays() {
        StringBuilder delayStr = new StringBuilder();
        StringBuilder probStr = new StringBuilder();
        delayStr.append("scenario delays:");
        probStr.append("scenario probabilities:");

        int numScenarios = dataRegistry.getNumScenarios();
        int[] scenarioDelays = dataRegistry.getScenarioDelays();
        double[] scenarioProbabilities = dataRegistry.getScenarioProbabilities();

        for (int i = 0; i < numScenarios; ++i) {
            delayStr.append(" ");
            delayStr.append(scenarioDelays[i]);
            probStr.append(" ");
            probStr.append(scenarioProbabilities[i]);
        }
        logger.info(delayStr);
        logger.info(probStr);
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
        dataRegistry.setTailHashMap(tailPaths);
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
        HashMap<Integer, Path> tailHashMap = dataRegistry.getTailHashMap();
        HashMap<Integer, Path> newTailPathMap = new HashMap<>();
        for (Tail tail : newTails)
            newTailPathMap.put(tail.getId(), tailHashMap.get(tail.getId()));
        dataRegistry.setTailHashMap(newTailPathMap);

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

    public void generateDelays(int numTestScenarios) {
        SolutionManager.generateDelaysForComparison(numTestScenarios, dataRegistry);
    }

    public void processSolution(boolean qualifySolution, double[][] xValues,
                                int numTestScenarios) throws OptException {
        SolutionManager sm = new SolutionManager(instancePath, dataRegistry, xValues);
        if (qualifySolution)
            sm.compareSolutions(numTestScenarios);
        sm.writeOutput();
    }

    public void newProcessSolution() throws IOException {
        NewSolutionManager nsm = new NewSolutionManager(bendersSolution);
        nsm.writeOutput();
    }
}
