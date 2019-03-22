package com.stochastic.solver;

import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.Constants;
import com.stochastic.utility.OptException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ilog.concert.IloException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SubSolverWrapper {
    /**
     * Wrapper class that can be used to solve the second-stage problems in parallel.
     */
    private final static Logger logger = LogManager.getLogger(SubSolverWrapper.class);
    private DataRegistry dataRegistry;
    private int numThreads = 2;
    private int[] reschedules; // planned delays from first stage solution.
    private int iter;
    private double uBound;

    // Values used to consstruct Bender's cut using the sub-problem solution.
    // Benders cut: beta x + theta >= alpha
    private double alpha;
    private double[][] beta;

    public SubSolverWrapper(DataRegistry dataRegistry, int[] reschedules, int iter, double uBound) {
        this.dataRegistry = dataRegistry;
        this.reschedules = reschedules;
        this.iter = iter;
        this.uBound = uBound;
        this.alpha = 0;
        this.beta = new double[Parameters.getNumDurations()][dataRegistry.getLegs().size()];
    }

    private synchronized void calculateAlpha(double[] dualsLegs, double[] dualsTail, double[] dualsDelay,
                                                    double[][] dualsBnd, double dualRisk) {
        ArrayList<Leg> legs = dataRegistry.getLegs();

        logger.debug("initial alpha value: " + alpha);

        for (int j = 0; j < legs.size(); j++)
            if (Math.abs(dualsLegs[j]) >= Constants.EPS)
                alpha += dualsLegs[j]; //*prb);

        for (int j = 0; j < dataRegistry.getTails().size(); j++)
            if (Math.abs(dualsTail[j]) >= Constants.EPS)
                alpha += dualsTail[j]; //*prb);

        for (int j = 0; j < legs.size(); j++)
            if (Math.abs(dualsDelay[j]) >= Constants.EPS)
                alpha += (dualsDelay[j] * 14); //prb*14);

        for (double[] dualBnd : dualsBnd)
            if (dualBnd != null)
                for (double j : dualBnd)
                    if (Math.abs(j) >= Constants.EPS)
                        alpha += j; //*prb);

        if (Parameters.isExpectedExcess())
            if (Math.abs(dualRisk) >= Constants.EPS)
                alpha += (dualRisk * Parameters.getExcessTarget()); //*prb);

        logger.debug("final alpha value: " + alpha);
    }

    private synchronized void calculateBeta(double[] dualsDelay, double dualRisk) {
        int[] durations = Parameters.getDurations();
        ArrayList<Leg> legs = dataRegistry.getLegs();

        for (int i = 0; i < durations.length; i++) {
            for (int j = 0; j < legs.size(); j++) {
                if (Math.abs(dualsDelay[j]) >= Constants.EPS)
                    beta[i][j] += dualsDelay[j] * -durations[i]; // * prb;

                if (Parameters.isExpectedExcess() && Math.abs(dualRisk) >= Constants.EPS)
                    beta[i][j] += dualRisk * durations[i]; // * prb;
            }
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

        private HashMap<Integer, ArrayList<Path>> getInitialPaths(HashMap<Integer, Integer> legDelayMap) {
            HashMap<Integer, ArrayList<Path>> initialPaths = new HashMap<>();

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


                ArrayList<Path> tailPaths = new ArrayList<>();
                tailPaths.add(new Path(tail)); // add empty path
                tailPaths.add(pathWithDelays);
                initialPaths.put(tailId, tailPaths);
            }

            return initialPaths;
        }

        //exSrv.execute(buildSDThrObj) calls brings you here
        public void run() {
            try {
                if (Parameters.isFullEnumeration())
                    solveWithFullEnumeration();
                else
                    solveWithLabeling();
            } catch (IloException ie) {
                logger.error(ie);
                logger.error("CPLEX error solving subproblem");
                System.exit(Constants.ERROR_CODE);
            } catch (OptException oe) {
                logger.error(oe);
                logger.error("algo error solving subproblem");
                System.exit(Constants.ERROR_CODE);
            }
        }

        private void solveWithLabeling() throws IloException, OptException {
            SubSolver ss = new SubSolver(probability);
            HashMap<Integer, Integer> legDelayMap = getTotalDelays();

            // Load on-plan paths with propagated delays.
            HashMap<Integer, ArrayList<Path>> pathsAll = getInitialPaths(legDelayMap);

            // Run the column generation procedure.
            ArrayList<Leg> legs = dataRegistry.getLegs();
            int numLegs = legs.size();
            int[] delays = new int[numLegs];
            for (int i = 0; i < numLegs; ++i)
                delays[i] = legDelayMap.getOrDefault(legs.get(i).getIndex(), 0);

            boolean optimal = false;
            int columnGenIter = 0;
            while (!optimal) {
                // Solve second-stage RMP (Restricted Master Problem)
                ss.constructSecondStage(reschedules, dataRegistry, scenarioNum, iter, pathsAll);

                if (Parameters.isDebugVerbose())
                    ss.writeLPFile("logs/", iter, columnGenIter, this.scenarioNum);

                ss.solve();
                ss.collectDuals();

                if (Parameters.isDebugVerbose())
                    ss.writeCplexSolution("logs/", iter, columnGenIter, this.scenarioNum);

                // Collect paths with negative reduced cost from the labeling algorithm. Optimality is reached when
                // there are no new negative reduced cost paths available for any tail.
                ArrayList<Tail> tails = dataRegistry.getTails();
                double[] tailDuals = ss.getDualsTail();
                optimal = true;

                logger.info("starting labeling procedure...");
                for (int i = 0; i < tails.size(); ++i) {
                    Tail tail = tails.get(i);

                    PricingProblemSolver lpg = new PricingProblemSolver(tail, legs, dataRegistry.getNetwork(),
                            delays, tailDuals[i], ss.getDualsLeg(), ss.getDualsDelay());

                    // Build sink labels for paths that have already been generated and add them to the labeling
                    // path generator.
                    ArrayList<Path> existingPaths = pathsAll.get(tail.getId());
                    lpg.initLabelsForExistingPaths(existingPaths);

                    ArrayList<Path> tailPaths = lpg.generatePathsForTail();
                    if (!tailPaths.isEmpty()) {
                        if (optimal)
                            optimal = false;

                        existingPaths.addAll(tailPaths);
                    }
                }
                logger.info("completed labeling procedure.");

                // Verify optimality using the feasibility of \gamma_f + b_f >= 0 for all flights.
                double[] delayDuals = ss.getDualsDelay();
                for (int i = 0; i < legs.size(); ++i) {
                    if (delayDuals[i] + legs.get(i).getDelayCostPerMin() <= -Constants.EPS) {
                        logger.error("no new paths, but solution not dual feasible");
                        throw new OptException("invalid optimality in second stage branch and price");
                    }
                }

                int numPaths = 0;
                for (Map.Entry<Integer, ArrayList<Path>> entry : pathsAll.entrySet())
                    numPaths += entry.getValue().size();

                logger.debug("number of paths: " + numPaths);
                logger.debug("completed column-gen iteration " + columnGenIter);

                // Re-initialize the SubSolver object to ensure that its data doesn't get stale.
                if (!optimal) {
                    ss.end();
                    ss = new SubSolver(probability);
                }
                ++columnGenIter;
            }

            // Update master problem data
            logger.info("reached sub-problem optimality");
            calculateAlpha(ss.getDualsLeg(), ss.getDualsTail(), ss.getDualsDelay(), ss.getDualsBnd(),
                    ss.getDualsRisk());
            calculateBeta(ss.getDualsDelay(), ss.getDualsRisk());
            uBound += ss.getObjValue();
        }

        private void solveWithFullEnumeration() throws IloException {
            try {
                // Enumerate all paths for each tail.
                HashMap<Integer, Integer> legDelayMap = getTotalDelays();

                ArrayList<Path> allPaths = dataRegistry.getNetwork().enumeratePathsForTails(
                        dataRegistry.getTails(), legDelayMap, dataRegistry.getMaxEndTime());

                // Store paths for each tail separately. Also add empty paths for each tail.
                HashMap<Integer, ArrayList<Path>> tailPathsMap = new HashMap<>();
                for (Tail t : dataRegistry.getTails())
                    tailPathsMap.put(t.getId(), new ArrayList<>(Collections.singletonList(new Path(t))));

                for(Path p : allPaths)
                    tailPathsMap.get(p.getTail().getId()).add(p);

                SubSolver ss = new SubSolver(probability);
                ss.constructSecondStage(reschedules, dataRegistry, scenarioNum, iter, tailPathsMap);

                if (Parameters.isDebugVerbose())
                    ss.writeLPFile("logs/", iter, -1, this.scenarioNum);

                ss.solve();
                ss.collectDuals();

                if (Parameters.isDebugVerbose())
                    ss.writeCplexSolution("logs/", iter, -1, this.scenarioNum);

                calculateAlpha(ss.getDualsLeg(), ss.getDualsTail(), ss.getDualsDelay(), ss.getDualsBnd(),
                        ss.getDualsRisk());
                calculateBeta(ss.getDualsDelay(), ss.getDualsRisk());
                ss.end();

                uBound += ss.getObjValue();
            } catch (OptException oe) {
                logger.error("submodel run for scenario " + scenarioNum + " failed.");
                logger.error(oe);
                System.exit(17);
            }
        }

        /**
         * Return the total delay time in minutes of each leg for the second stage.
         *
         * Total delay is the maximum of rescheduled time from first stage and random delay of second-stage scenario.
         * @return map with leg indices as keys, total delay times as corresponding values.
         */
        private HashMap<Integer, Integer> getTotalDelays() {
            HashMap<Integer, Integer> combinedDelayMap = new HashMap<>();
            for(Leg leg : dataRegistry.getLegs()) {
                int delayTime = 0;
                boolean updated = false;

                if(randomDelays.containsKey(leg.getIndex())) {
                    delayTime = randomDelays.get(leg.getIndex());
                    updated = true;
                }

                if (reschedules[leg.getIndex()] > 0)  {
                    delayTime = Math.max(delayTime, reschedules[leg.getIndex()]);
                    updated = true;
                }

                if(updated)
                    combinedDelayMap.put(leg.getIndex(), delayTime);
            }

            return combinedDelayMap;
        }
    }

    public double getuBound() {
        return uBound;
    }

    public double getAlpha() {
        return alpha;
    }

    public double[][] getBeta() {
        return beta;
    }
}
