package com.stochastic.solver;

import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Network;
import com.stochastic.network.Path;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.OptException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SubSolverWrapper {
    /**
     * Wrapper class that can be used to solve the second-stage problems in parallel.
     */
    private final static Logger logger = LogManager.getLogger(SubSolverWrapper.class);
    private static DataRegistry dataRegistry;
    private static double alpha;
    private static int iter;
    private static double[][] beta;
    private static int numThreads = 2;
    private static HashMap<Integer, HashMap<Integer, ArrayList<Path>>> hmPaths = new HashMap<>();


    private static double[][] xValues;
    private static double uBound;

    public static void SubSolverWrapperInit(DataRegistry dataRegistry, double[][] xValues, int iter)
            throws OptException {
        try {
            SubSolverWrapper.dataRegistry = dataRegistry;
            SubSolverWrapper.xValues = xValues;
            SubSolverWrapper.iter = iter;

            alpha = 0;
            uBound = MasterSolver.getFSObjValue();
            beta = new double[Parameters.getNumDurations()][dataRegistry.getLegs().size()];
        } catch (Exception e) {
            logger.error(e.getStackTrace());
            throw new OptException("error at SubSolverWrapperInit");
        }
    }

    private synchronized static void calculateAlpha(double[] dualsLegs, double[] dualsTail, double[] dualsDelay,
                                                    double[][] dualsBnd, double dualRisk) {
        ArrayList<Leg> legs = dataRegistry.getLegs();

        logger.debug("initial alpha value: " + alpha);

        for (int j = 0; j < legs.size(); j++) {
            alpha += (dualsLegs[j]); //*prb);
        }

        for (int j = 0; j < dataRegistry.getTails().size(); j++) {
            alpha += (dualsTail[j]); //*prb);
        }

        for (int j = 0; j < legs.size(); j++) {
            alpha += (dualsDelay[j] * 14); //prb*14);
        }

        for (double[] dualBnd : dualsBnd)
            if (dualBnd != null)
                for (double j : dualBnd)
                    alpha += j; //*prb);

        if (Parameters.isExpectedExcess())
            alpha += (dualRisk * Parameters.getExcessTarget()); //*prb);

        logger.debug("final alpha value: " + alpha);
    }

    private synchronized static void calculateBeta(double[] dualsDelay, double dualRisk) {
        ArrayList<Integer> durations = Parameters.getDurations();
        ArrayList<Leg> legs = dataRegistry.getLegs();

        for (int i = 0; i < durations.size(); i++)
            for (int j = 0; j < legs.size(); j++) {
                beta[i][j] += dualsDelay[j] * -durations.get(i); // * prb;

                if (Parameters.isExpectedExcess())
                    beta[i][j] += dualRisk * durations.get(i); // * prb;
            }
    }

    public void solveSequential(ArrayList<Integer> scenarioDelays, ArrayList<Double> probabilities) {
        final int numScenarios = dataRegistry.getNumScenarios();
        for (int i = 0; i < numScenarios; i++) {
            DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), scenarioDelays.get(i));
            HashMap<Integer, Integer> legDelays = dgen.generateDelays();
            SubSolverRunnable subSolverRunnable = new SubSolverRunnable(i, legDelays, probabilities.get(i));
            subSolverRunnable.run();
            logger.info("Solved scenario " + i + " numScenarios: " + numScenarios);
        }
    }

    public void solveParallel(ArrayList<Integer> scenarioDelays, ArrayList<Double> probabilities) throws OptException {
        try {
            ExecutorService exSrv = Executors.newFixedThreadPool(numThreads);

            for (int i = 0; i < dataRegistry.getNumScenarios(); i++) {
                // Thread.sleep(500);

                DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), scenarioDelays.get(i));
                HashMap<Integer, Integer> legDelays = dgen.generateDelays();

                SubSolverRunnable subSolverRunnable = new SubSolverRunnable(i, legDelays, probabilities.get(i));
                exSrv.execute(subSolverRunnable); // this calls run() method below
                logger.info("Solved scenario " + i);
            }

            exSrv.shutdown();
            while (!exSrv.isTerminated())
                Thread.sleep(100);
        } catch (InterruptedException ie) {
            logger.error(ie.getStackTrace());
            throw new OptException("error at buildSubModel");
        }
    }

    class SubSolverRunnable implements Runnable {
        private int scenarioNum;
        private HashMap<Integer, Integer> randomDelays;
        private double probability;
        private final double eps = 1.0e-5;

        SubSolverRunnable(int scenarioNum, HashMap<Integer, Integer> randomDelays, double probability) {
            this.scenarioNum = scenarioNum;
            this.randomDelays = randomDelays;
            this.probability = probability;
        }

        public void updatePaths(Tail t, ArrayList<Path> arrT) {
            if (hmPaths.containsKey(this.scenarioNum)) {
                HashMap<Integer, ArrayList<Path>> pAll = hmPaths.get(this.scenarioNum);
                ArrayList<Path> arrPath;

                if (pAll.containsKey(t.getId()))
                    arrPath = pAll.get(t.getId());
                else
                    arrPath = new ArrayList<>();

                arrPath.addAll(arrT);
            } else {
                HashMap<Integer, ArrayList<Path>> pAll = new HashMap<>();
                ArrayList<Path> arrPath = new ArrayList<>(arrT);
                pAll.put(t.getId(), arrPath);
                hmPaths.put(this.scenarioNum, pAll);
            }
        }

        private HashMap<Integer, Path> getInitialPaths(HashMap<Integer, Integer> legDelayMap) {
            HashMap<Integer, Path> initialPaths = new HashMap<>();

            for (Map.Entry<Integer, Path> entry : dataRegistry.getTailHashMap().entrySet()) {
                int tailId = entry.getKey();
                Tail tail = dataRegistry.getTail(tailId);

                Path origPath = entry.getValue();

                // The original path of the tail may not be valid due to random delays or reschedules of the first
                // leg caused by the first stage model. To make the path valid, we need to propagate changes in
                // departure time of the first leg to the remaining legs on the path. These delayed paths for all
                // tails will serve as the initial set of columns for the second stage model.

                // Note that the leg coverage constraint in the second-stage model remains feasible even with just
                // these paths as we assume that there are no open legs in any dataset. This means that each leg
                // must be on the original path of some tail.

                Path pathWithDelays = new Path(tail);
                LocalDateTime currentTime = null;
                for (Leg leg : origPath.getLegs()) {
                    // find delayed departure time due to original delay (planned 1st stange + random 2nd stage)
                    Integer legDelay = legDelayMap.getOrDefault(leg.getIndex(), 0);
                    LocalDateTime delayedDepTime = leg.getDepTime().plusMinutes(legDelay);

                    // find delayed departure time due to propagated delay
                    if (currentTime != null && currentTime.isAfter(delayedDepTime))
                        delayedDepTime = currentTime;

                    legDelay = (int) Duration.between(leg.getDepTime(), delayedDepTime).toMinutes();
                    pathWithDelays.addLeg(leg, legDelay);

                    // update current time based on leg's delayed arrival time and turn time.
                    currentTime = leg.getArrTime().plusMinutes(legDelay).plusMinutes(leg.getTurnTimeInMin());
                }
                initialPaths.put(tailId, pathWithDelays);
            }

            return initialPaths;
        }

        //exSrv.execute(buildSDThrObj) calls brings you here
        public void run() {
            if (Parameters.isFullEnumeration())
                solveWithFullEnumeration();
            else
                solveWithLabeling();
        }

        private void solveWithLabeling() {
            try {
                HashMap<Integer, ArrayList<Path>> pathsAll;

                SubSolver s1 = new SubSolver(randomDelays, probability);
                HashMap<Integer, Integer> legDelayMap = getLegDelays( dataRegistry.getLegs(),
                        Parameters.getDurations(), xValues);

                if (hmPaths.containsKey(this.scenarioNum))
                    pathsAll = hmPaths.get(this.scenarioNum);
                else {
                    // load on-plan paths with propagated delays into the container that will be provided to SubSolver.
                    HashMap<Integer, Path> initialPaths = getInitialPaths(legDelayMap);
                    pathsAll = new HashMap<>();
                    for (Map.Entry<Integer, Path> entry : initialPaths.entrySet()) {
                        ArrayList<Path> paths = new ArrayList<>();
                        paths.add(entry.getValue());
                        pathsAll.put(entry.getKey(), paths);
                    }
                }

                boolean solveAgain = true;
                double uBoundValue = 0;

                double[] dualsLeg;
                double[] dualsTail;
                double[] dualsDelay;

                int wCnt = -1;
                while (solveAgain) {
                    wCnt++;
                    // beta x + theta >= alpha - Benders cut
                    SubSolver s = new SubSolver(randomDelays, probability);
                    s.constructSecondStage(xValues, dataRegistry, scenarioNum, iter, pathsAll);
                    s.solve();
                    s.collectDuals();
                    s.writeLPFile("logs/", iter, wCnt, this.scenarioNum);
                    uBoundValue = s.getObjValue();
                    calculateAlpha(s.getDualsLeg(), s.getDualsTail(), s.getDualsDelay(), s.getDualsBnd(), s.getDualsRisk());
                    calculateBeta(s.getDualsDelay(), s.getDualsRisk());

                    dualsLeg = s.getDualsLeg();
                    dualsTail = s.getDualsTail();
                    dualsDelay = s.getDualsDelay();

                    s.end();

                    boolean pathAdded = false;
                    int index = 0;
                    for (Tail t : dataRegistry.getTails()) {
                        ArrayList<Path> arrT = new LabelingAlgorithm().getPaths(dataRegistry, dataRegistry.getTails(),
                                legDelayMap, t, dualsLeg, dualsTail[index], dualsDelay, pathsAll.get(t.getId()));

                        // add the paths to the master list
                        if (arrT.size() > 0) {
//                            updatePaths(t, arrT); dont add the paths since the list changes everytime based on the new xValue
                            pathAdded = true;

                            logger.debug(wCnt + " Label-Start: " + t.getId());
                            for (Path p : arrT)
                                logger.debug(p);
                            logger.debug(wCnt + " Label-End: " + t.getId());
                        }

                        logger.debug(wCnt + " pathsAll-size: " + pathsAll.get(t.getId()).size());

                        ArrayList<Path> paths = pathsAll.get(t.getId());
                        paths.addAll(arrT);
                        index++;

                        logger.debug(wCnt + " PathsAll-Start: " + t.getId());
                        for (Path p : paths)
                            logger.debug(p);
                        logger.debug(wCnt + " PathsAll-End: " + t.getId());
                    }

                    if (!pathAdded)
                        solveAgain = false;
                }

                uBound += uBoundValue; // from last iteration

            } catch (OptException oe) {
                logger.error("submodel run for scenario " + scenarioNum + " failed.");
                logger.error(oe);
                System.exit(17);
            }
        }

        private void solveWithFullEnumeration() {
            try {
                // Enumerate all paths for each tail.
                HashMap<Integer, Integer> legDelayMap = getLegDelays(
                        dataRegistry.getLegs(), Parameters.getDurations(), xValues);

                Network network = new Network(dataRegistry.getTails(), dataRegistry.getLegs(), legDelayMap,
                        dataRegistry.getWindowStart(), dataRegistry.getWindowEnd(),
                        dataRegistry.getMaxLegDelayInMin());

                ArrayList<Path> allPaths = network.enumerateAllPaths();

                // Store paths for each tail separately.
                HashMap<Integer, ArrayList<Path>> tailPathsMap = new HashMap<>();
                for (Tail t : dataRegistry.getTails())
                    tailPathsMap.put(t.getId(), new ArrayList<>());

                for(Path p : allPaths)
                    tailPathsMap.get(p.getTail().getId()).add(p);

                // beta x + theta >= alpha - Benders cut
                SubSolver s = new SubSolver(randomDelays, probability);
                s.constructSecondStage(xValues, dataRegistry, scenarioNum, iter, tailPathsMap);
                s.solve();
                s.collectDuals();
                s.writeLPFile("logs/", iter, -1, this.scenarioNum);
                calculateAlpha(s.getDualsLeg(), s.getDualsTail(), s.getDualsDelay(), s.getDualsBnd(),
                        s.getDualsRisk());
                calculateBeta(s.getDualsDelay(), s.getDualsRisk());
                s.end();

                uBound += s.getObjValue();
            } catch (OptException oe) {
                logger.error("submodel run for scenario " + scenarioNum + " failed.");
                logger.error(oe);
                System.exit(17);
            }
        }

        private HashMap<Integer, Integer> getLegDelays(ArrayList<Leg> legs, ArrayList<Integer> durations,
                                                      double[][] xValues) {
            // Collect planned delays from first stage solution.
            HashMap<Integer, Integer> plannedDelays = new HashMap<>();
            for(int i = 0; i < durations.size(); ++i) {
                for(int j = 0; j < legs.size(); ++j) {
                    if(xValues[i][j] >= eps)
                        plannedDelays.put(legs.get(j).getIndex(), durations.get(i));
                }
            }

            // Combine planned and delay maps into a single one.
            HashMap<Integer, Integer> combinedDelayMap = new HashMap<>();
            for(Leg leg : legs) {
                int delayTime = 0;
                boolean updated = false;

                if(randomDelays.containsKey(leg.getIndex())) {
                    delayTime = randomDelays.get(leg.getIndex());
                    updated = true;
                }

                if(plannedDelays.containsKey(leg.getIndex())) {
                    delayTime = Math.max(delayTime, plannedDelays.get(leg.getIndex()));
                    updated = true;
                }

                if(updated)
                    combinedDelayMap.put(leg.getIndex(), delayTime);
            }

            return combinedDelayMap;
        }
    }

    public static double getuBound() {
        return uBound;
    }

    public static double getAlpha() {
        return alpha;
    }

    public static double[][] getBeta() {
        return beta;
    }

    public static class ScenarioData {
        int sceNo;
        int iter;
        int pathIndex;
        int tailIndex;
        int legId;

        public int getSceNo() {
            return sceNo;
        }

        public int getIter() {
            return iter;
        }

        public void setIter(int iter) {
            this.iter = iter;
        }

        public void setSceNo(int sceNo) {
            this.sceNo = sceNo;
        }

        public int getPathIndex() {
            return pathIndex;
        }

        public void setPathIndex(int pathIndex) {
            this.pathIndex = pathIndex;
        }

        public int getTailIndex() {
            return tailIndex;
        }

        public void setTailIndex(int tailIndex) {
            this.tailIndex = tailIndex;
        }

        public int getLegId() {
            return legId;
        }

        public void setLegId(int legId) {
            this.legId = legId;
        }

        public static HashMap<ScenarioData, Integer> dataStore = new HashMap<>();

        public ScenarioData() {
            super();
        }

        public ScenarioData(int sceNo, int iter, int pathIndex, int tailIndex, int legId) {
            super();
            this.sceNo = sceNo;
            this.iter = iter;
            this.pathIndex = pathIndex;
            this.tailIndex = tailIndex;
            this.legId = legId;
        }

        public static void addData(int sNo, int iter, int pIndex, int tIndex, int legId, int duration) {
            ScenarioData sd = new ScenarioData(sNo, iter, pIndex, tIndex, legId);
            dataStore.put(sd, duration);
        }

        public static void printData() {
            logger.debug(" Prints the scenario data: ");
            logger.debug("-----");
            for (Map.Entry<ScenarioData, Integer> entry : dataStore.entrySet()) {
                ScenarioData key = entry.getKey();
                Integer value = entry.getValue();
                logger.debug("iteration: " + key.iter);
                logger.debug("scenario number: " + key.sceNo);
                logger.debug("path index: " + key.pathIndex);
                logger.debug("tail index: " + key.tailIndex);
                logger.debug("leg id: " + key.legId);
                logger.debug("value: " + value);
                logger.debug("-----");
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + iter;
            result = prime * result + legId;
            result = prime * result + pathIndex;
            result = prime * result + sceNo;
            result = prime * result + tailIndex;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ScenarioData other = (ScenarioData) obj;
            if (iter != other.iter)
                return false;
            if (legId != other.legId)
                return false;
            if (pathIndex != other.pathIndex)
                return false;
            if (sceNo != other.sceNo)
                return false;
            if (tailIndex != other.tailIndex)
                return false;
            return true;
        }

    }

}
