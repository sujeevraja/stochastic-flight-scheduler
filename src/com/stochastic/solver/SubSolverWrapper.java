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
    private int[] reschedules; // planned delays from first stage solution.
    private int iter;
    private BendersData bendersData;

    public SubSolverWrapper(DataRegistry dataRegistry, int[] reschedules, int iter, double uBound) {
        this.dataRegistry = dataRegistry;
        this.reschedules = reschedules;
        this.iter = iter;
        this.bendersData = new BendersData(uBound, 0, new double[Parameters.getNumDurations()][dataRegistry.getLegs().size()]);
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
            ExecutorService exSrv = Executors.newFixedThreadPool(Parameters.getNumThreadsForSecondStage());

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
            SubSolver ss = new SubSolver(dataRegistry.getTails(), dataRegistry.getLegs(), reschedules, probability);
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
                ss.constructSecondStage(pathsAll);

                if (Parameters.isDebugVerbose())
                    ss.writeLPFile("logs/", iter, columnGenIter, this.scenarioNum);

                ss.solve();
                ss.collectDuals();
                logger.debug("Iter " + iter + ": subproblem objective value: " + ss.getObjValue());

                if (Parameters.isDebugVerbose())
                    ss.writeCplexSolution("logs/", iter, columnGenIter, this.scenarioNum);

                // Collect paths with negative reduced cost from the labeling algorithm. Optimality is reached when
                // there are no new negative reduced cost paths available for any tail.
                ArrayList<Tail> tails = dataRegistry.getTails();
                double[] tailDuals = ss.getDualsTail();
                optimal = true;

                for (int i = 0; i < tails.size(); ++i) {
                    Tail tail = tails.get(i);

                    PricingProblemSolver lpg = new PricingProblemSolver(tail, legs, dataRegistry.getNetwork(),
                            delays, tailDuals[i], ss.getDualsLeg(), ss.getDualsDelay());

                    // Build sink labels for paths that have already been generated and add them to the labeling
                    // path generator.
                    ArrayList<Path> existingPaths = pathsAll.get(tail.getId());

                    // Note: it is possible for a path already in existingPaths to be generated again and be present
                    // in tailPaths, causing duplicates. However, we found empirically that the number of duplicates
                    // is not big enough to impact CPLEX run-times. So, we ignore duplicate checking here.
                    ArrayList<Path> tailPaths = lpg.generatePathsForTail();
                    if (!tailPaths.isEmpty()) {
                        if (optimal)
                            optimal = false;

                        existingPaths.addAll(tailPaths);
                    }
                }

                // Verify optimality using the feasibility of \pi_f + b_f >= 0 for all flights.
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

                logger.debug("Iter " + iter + ": number of paths: " + numPaths);
                logger.debug("Iter " + iter + ": completed column-gen iteration " + columnGenIter);

                // Cleanup CPLEX continers of the SubSolver object.
                if (!optimal)
                    ss.end();
                ++columnGenIter;
            }

            // Update master problem data
            logger.info( "Iter " + iter + ": reached sub-problem optimality");
            calculateAlpha(ss.getDualsLeg(), ss.getDualsTail(), ss.getDualsDelay(), ss.getDualsBound(),
                    ss.getDualRisk(), probability);
            calculateBeta(ss.getDualsDelay(), ss.getDualRisk(), probability);
            updateUpperBound(ss.getObjValue(), probability);
        }

        private void solveWithFullEnumeration() throws IloException {
            try {
                // Enumerate all paths for each tail.
                HashMap<Integer, Integer> legDelayMap = getTotalDelays();

                ArrayList<Path> allPaths = dataRegistry.getNetwork().enumeratePathsForTails(
                        dataRegistry.getTails(), legDelayMap);

                // Store paths for each tail separately. Also add empty paths for each tail.
                HashMap<Integer, ArrayList<Path>> tailPathsMap = new HashMap<>();
                for (Tail t : dataRegistry.getTails())
                    tailPathsMap.put(t.getId(), new ArrayList<>(Collections.singletonList(new Path(t))));

                for(Path p : allPaths)
                    tailPathsMap.get(p.getTail().getId()).add(p);

                SubSolver ss = new SubSolver(dataRegistry.getTails(), dataRegistry.getLegs(), reschedules, probability);
                ss.constructSecondStage(tailPathsMap);

                if (Parameters.isDebugVerbose())
                    ss.writeLPFile("logs/", iter, -1, this.scenarioNum);

                ss.solve();
                ss.collectDuals();
                logger.debug("Iter " + iter + ": subproblem objective value: " + ss.getObjValue());

                if (Parameters.isDebugVerbose())
                    ss.writeCplexSolution("logs/", iter, -1, this.scenarioNum);

                ss.end();

                calculateAlpha(ss.getDualsLeg(), ss.getDualsTail(), ss.getDualsDelay(), ss.getDualsBound(),
                        ss.getDualRisk(), probability);
                calculateBeta(ss.getDualsDelay(), ss.getDualRisk(), probability);
                updateUpperBound(ss.getObjValue(), probability);
            } catch (OptException oe) {
                logger.error("submodel run for scenario " + scenarioNum + " failed.");
                logger.error(oe);
                System.exit(Constants.ERROR_CODE);
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

        private synchronized void calculateAlpha(double[] dualsLegs, double[] dualsTail, double[] dualsDelay,
                                                 double[][] dualsBnd, double dualRisk, double probability) {
            ArrayList<Leg> legs = dataRegistry.getLegs();

            logger.debug("initial alpha value: " + bendersData.getAlpha());

            double scenAlpha = 0;

            for (int j = 0; j < legs.size(); j++)
                if (Math.abs(dualsLegs[j]) >= Constants.EPS)
                    scenAlpha += dualsLegs[j];

            for (int j = 0; j < dataRegistry.getTails().size(); j++)
                if (Math.abs(dualsTail[j]) >= Constants.EPS)
                    scenAlpha += dualsTail[j];

            for (int j = 0; j < legs.size(); j++)
                if (Math.abs(dualsDelay[j]) >= Constants.EPS)
                    scenAlpha += (dualsDelay[j] * Constants.OTP_TIME_LIMIT_IN_MINUTES);

            for (double[] dualBnd : dualsBnd)
                if (dualBnd != null)
                    for (double j : dualBnd)
                        if (Math.abs(j) >= Constants.EPS)
                            scenAlpha += j;

            if (Parameters.isExpectedExcess())
                if (Math.abs(dualRisk) >= Constants.EPS)
                    scenAlpha += (dualRisk * Parameters.getExcessTarget());

            bendersData.setAlpha(bendersData.getAlpha() + (scenAlpha * probability));
            logger.debug("final alpha value: " + bendersData.getAlpha());
        }

        private synchronized void calculateBeta(double[] dualsDelay, double dualRisk, double probability) {
            int[] durations = Parameters.getDurations();
            ArrayList<Leg> legs = dataRegistry.getLegs();
            double[][] beta = bendersData.getBeta();

            for (int i = 0; i < durations.length; i++) {
                for (int j = 0; j < legs.size(); j++) {
                    if (Math.abs(dualsDelay[j]) >= Constants.EPS)
                        beta[i][j] += (dualsDelay[j] * -durations[i] * probability);

                    if (Parameters.isExpectedExcess() && Math.abs(dualRisk) >= Constants.EPS)
                        beta[i][j] += (dualRisk * durations[i] * probability);
                }
            }
        }

        private synchronized void updateUpperBound(double objValue, double probability) {
            bendersData.setUpperBound(bendersData.getUpperBound() + (objValue * probability));
        }
    }

    public double getuBound() {
        return bendersData.getUpperBound();
    }

    public double getAlpha() {
        return bendersData.getAlpha();
    }

    public double[][] getBeta() {
        return bendersData.getBeta();
    }
}
