package com.stochastic.controller;

import com.stochastic.domain.Leg;
import com.stochastic.dao.EquipmentsDAO;
import com.stochastic.dao.ScheduleDAO;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.postopt.SolutionManager;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.solver.MasterSolver;
import com.stochastic.solver.SubSolverWrapper;
import com.stochastic.utility.OptException;
import org.apache.commons.math3.distribution.LogNormalDistribution;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Controller {
    /**
     * Class that controls the entire solution process from reading data to writing output.
     */
    private final static Logger logger = LogManager.getLogger(Controller.class);
    private DataRegistry dataRegistry;
    private ArrayList<Integer> scenarioDelays;
    private ArrayList<Double> scenarioProbabilities;
    private String instancePath;
    private static ArrayList<Double> bounds = new ArrayList<>();

    public static ArrayList<Double> delayResults = new ArrayList<>();

    public static int[][] sceVal;

    public Controller() {
        dataRegistry = new DataRegistry();
    }

    public final void readData(String instancePath) throws OptException {
        logger.info("Started reading data...");
        this.instancePath = instancePath;

        // Read equipment data
        dataRegistry.setEquipment(new EquipmentsDAO(instancePath + "\\Equipments.xml").getEquipments().get(0));
        logger.info("Completed reading equipment data from Equipments.xml.");

        // Read leg data and remove unnecessary legs
        ArrayList<Leg> legs = new ScheduleDAO(instancePath + "\\Schedule.xml").getLegs();
        storeLegs(legs);
        logger.info("Collected leg and tail data from Schedule.xml.");
        logger.info("Completed reading data.");
    }

    public final void solve() throws OptException {
        ArrayList<Leg> legs = dataRegistry.getLegs();
        ArrayList<Tail> tails = dataRegistry.getTails();
        ArrayList<Integer> durations = Parameters.getDurations();

        int iter = -1;
        MasterSolver.MasterSolverInit(legs, tails, durations);
        MasterSolver.constructFirstStage();
        MasterSolver.writeLPFile("logs/before_cuts_master.lp");
        MasterSolver.solve(iter);
        MasterSolver.addColumn();

        double lBound;
        double uBound = Double.MAX_VALUE;

        logger.info("Algorithm starts.");

        // generate random delays for 2nd stage scenarios.
        // generateScenarioDelays(Parameters.getScale(), Parameters.getShape());
        generateTestDelays();

        // Update max delay and max end time
        int requiredMaxDelay = Collections.max(scenarioDelays);
        if (requiredMaxDelay > dataRegistry.getMaxLegDelayInMin())
            dataRegistry.setMaxLegDelayInMin(requiredMaxDelay);
        dataRegistry.setMaxEndTime(dataRegistry.getMaxEndTime().plusMinutes(dataRegistry.getMaxLegDelayInMin()));

        logScenarioDelays();

        // sceVal = new int[3][5];
        // Random rand = new Random();
        // for(int i=0; i<3;i++)
        //    for(int j=0; j<5;j++)
        //    	sceVal[i][j] = (i+20) + j; //rand.nextInt(40 - 20 + 1) + 20; // (max - min + 1) + min;  (i+20) + j

        do {
            iter++;
            SubSolverWrapper.SubSolverWrapperInit(dataRegistry, MasterSolver.getxValues(), iter);
            new SubSolverWrapper().solveSequential(scenarioDelays, scenarioProbabilities);
            // new SubSolverWrapper().solveParallel(scenarioDelays, scenarioProbabilities);

            MasterSolver.constructBendersCut(SubSolverWrapper.getAlpha(), SubSolverWrapper.getBeta());

            MasterSolver.writeLPFile("logs/master_" + iter + ".lp");
            MasterSolver.solve(iter);

            lBound = MasterSolver.getObjValue();

            logger.info("----- LB: " + lBound + " UB: " + uBound + " Iter: " + iter
                    + " SubSolverWrapper.getuBound(): " + SubSolverWrapper.getuBound());

            if (SubSolverWrapper.getuBound() < uBound)
                uBound = SubSolverWrapper.getuBound();

            logger.info("----- LB: " + lBound + " UB: " + uBound + " Iter: " + iter);
        }
        while (uBound - lBound > 0.001); // && (System.currentTimeMillis() - Optimizer.stTime)/1000 < Optimizer.runTime); // && iter < 10);

        MasterSolver.printSolution();
        MasterSolver.end();
        bounds.add(lBound);
        bounds.add(uBound);

        // if (lBound - uBound > 1) {
        //     logger.info("DATA PRINTED");
        //     SubSolverWrapper.ScenarioData.printData();
        // }

        logger.info("Algorithm ends.");
    }

    private void generateTestDelays() {
        scenarioDelays = new ArrayList<>(Collections.singletonList(45));
        scenarioProbabilities = new ArrayList<>(Collections.singletonList(1.0));

        // scenarioDelays = new ArrayList<>(Arrays.asList(22, 23, 30, 32, 33, 34, 36, 46, 52));
        // scenarioProbabilities = new ArrayList<>(Arrays.asList(0.1, 0.1, 0.1, 0.2, 0.1, 0.1, 0.1, 0.1, 0.1));
        dataRegistry.setNumScenarios(scenarioDelays.size());
        dataRegistry.setMaxLegDelayInMin(Collections.max(scenarioDelays));
    }

    private void generateScenarioDelays(double scale, double shape) {
        // Generates random delays that will be applied to the first flight of each tail's original schedule.
        // Also generates delay probabilities using frequency values.
        // This function changes the number of scenarios as well if delay times repeat.

        int numSamples = Parameters.getNumScenarios();
        LogNormalDistribution logNormal = new LogNormalDistribution(scale, shape);

        int[] delayTimes = new int[numSamples];
        for (int i = 0; i < numSamples; ++i)
            delayTimes[i] = (int) Math.round(logNormal.sample());

        Arrays.sort(delayTimes);
        scenarioDelays = new ArrayList<>();
        scenarioProbabilities = new ArrayList<>();

        DecimalFormat df = new DecimalFormat("##.##");
        df.setRoundingMode(RoundingMode.HALF_UP);

        final double baseProbability = 1.0 / numSamples;
        int numCopies = 1;

        scenarioDelays.add(delayTimes[0]);
        int prevDelayTime = delayTimes[0];
        for (int i = 1; i < numSamples; ++i) {
            int delayTime = delayTimes[i];

            if (delayTime != prevDelayTime) {
                final double prob = Double.parseDouble(df.format(numCopies * baseProbability));
                scenarioProbabilities.add(prob); // add probabilities for previous time.
                scenarioDelays.add(delayTime); // add new delay time.
                numCopies = 1;
            } else
                numCopies++;

            prevDelayTime = delayTime;
        }
        scenarioProbabilities.add(numCopies * baseProbability);
        dataRegistry.setNumScenarios(scenarioDelays.size());
    }

    private void logScenarioDelays() {
        logger.info("updated max 2nd stage delay: " + dataRegistry.getMaxLegDelayInMin());
        logger.info("updated number of scenarios: " + scenarioDelays.size());

        StringBuilder delayStr = new StringBuilder();
        StringBuilder probStr = new StringBuilder();
        delayStr.append("scenario delays: ");
        probStr.append("scenario probabilities: ");
        for (int i = 0; i < scenarioDelays.size(); ++i) {
            delayStr.append(scenarioDelays.get(i));
            delayStr.append(" ");
            probStr.append(scenarioProbabilities.get(i));
            probStr.append(" ");
        }

        logger.info(delayStr);
        logger.info(probStr);
    }

    private void storeLegs(ArrayList<Leg> inputLegs) {
        final ArrayList<Integer> tailIds = dataRegistry.getEquipment().getTailIds();
        ArrayList<Leg> legs = new ArrayList<>();
        HashMap<Integer, ArrayList<Leg>> tailHashMap = new HashMap<>();

        // Collect legs and build a mapping between tail and legs.
        LocalDateTime maxEndTime = null;
        Integer index = 0;
        for (Leg leg : inputLegs) {
            Integer tailId = leg.getOrigTailId();
            if (!tailIds.contains(tailId))
                continue;

            leg.setIndex(index);
            ++index;
            legs.add(leg);

            if (maxEndTime == null || leg.getArrTime().isAfter(maxEndTime))
                maxEndTime = leg.getArrTime();

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

        dataRegistry.setMaxEndTime(maxEndTime);
        logger.info("Maximum end time: " + maxEndTime);

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

        HashMap<Integer, Path> tailPaths = new HashMap<Integer, Path>();
        for (Map.Entry<Integer, ArrayList<Leg>> entry : tailHashMap.entrySet()) {
            ArrayList<Leg> tailLegs = entry.getValue();
            tailLegs.sort(Comparator.comparing(Leg::getDepTime));

            Path p = new Path(dataRegistry.getTail(entry.getKey()));

            for (Leg l : tailLegs)
                p.addLeg(l, 0);

            tailPaths.put(entry.getKey(), p);
        }
        dataRegistry.setTailHashMap(tailPaths);
    }

    public void generateDelays(int numTestScenarios) {
        SolutionManager.generateDelaysForComparison(numTestScenarios, dataRegistry);
    }

    public void processSolution(boolean qualifySolution, double[][] xValues,
                                int numTestScenarios) throws OptException {
        SolutionManager sm = new SolutionManager(instancePath, dataRegistry, scenarioDelays, scenarioProbabilities,
                xValues);
        if (qualifySolution)
            sm.compareSolutions(numTestScenarios);
        sm.writeOutput();
    }
}
