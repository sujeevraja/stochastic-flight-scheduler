package stochastic.solver;

import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Path;
import stochastic.output.DelaySolution;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.Constants;
import stochastic.utility.OptException;
import ilog.concert.IloException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class SubSolverRunnable implements Runnable {
    private final static Logger logger = LogManager.getLogger(SubSolverWrapper.class);
    private DataRegistry dataRegistry;
    private int iter;
    private int scenarioNum;
    private double probability;
    private int[] reschedules;
    private double thetaValue;
    private int[] randomDelays;
    private PathCache pathCache;

    private String filePrefix;
    private BendersData bendersData;

    private boolean solveForQuality = false;
    private DelaySolution delaySolution; // used only when checking Benders solution quality

    public SubSolverRunnable(DataRegistry dataRegistry, int iter, int scenarioNum, double probability,
                             int[] reschedules, double thetaValue, int[] randomDelays, PathCache pathCache) {
        this.dataRegistry = dataRegistry;
        this.iter = iter;
        this.scenarioNum = scenarioNum;
        this.probability = probability;
        this.reschedules = reschedules;
        this.thetaValue = thetaValue;
        this.randomDelays = randomDelays;
        this.pathCache = pathCache;
        this.filePrefix = null;
    }

    public void setFilePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
    }

    void setBendersData(BendersData bendersData) {
        this.bendersData = bendersData;
    }

    public void setSolveForQuality(boolean solveForQuality) {
        this.solveForQuality = solveForQuality;
    }

    public DelaySolution getDelaySolution() {
        return delaySolution;
    }

    //exSrv.execute(buildSDThrObj) calls brings you here
    public void run() {
        try {
            if (Parameters.isFullEnumeration())
                solveWithFullEnumeration();
            else
                solveWithLabeling();
            logger.info("solved scenario " + scenarioNum);
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

    private void solveWithFullEnumeration() throws IloException {
        try {
            // Enumerate all paths for each tail.
            ArrayList<Path> allPaths = dataRegistry.getNetwork().enumeratePathsForTails(
                    dataRegistry.getTails(), randomDelays);

            // Store paths for each tail separately. Also add empty paths for each tail.
            HashMap<Integer, ArrayList<Path>> tailPathsMap = SolverUtility.getPathsForFullEnum(allPaths,
                    dataRegistry.getTails());

            SubSolver ss = new SubSolver(scenarioNum, dataRegistry.getTails(), dataRegistry.getLegs(), reschedules);
            if (solveForQuality)
                ss.setSolveAsMIP();
            ss.constructSecondStage(tailPathsMap);

            String name = "dummy";
            if (Parameters.isDebugVerbose()) {
                StringBuilder nameBuilder = new StringBuilder();
                nameBuilder.append("logs/");
                if (solveForQuality) {
                    nameBuilder.append("qual");
                    if (filePrefix != null) {
                        nameBuilder.append("_");
                        nameBuilder.append(filePrefix);
                        nameBuilder.append("_");
                    }
                } else {
                    nameBuilder.append("sub_benders_");
                    nameBuilder.append(iter);
                }
                nameBuilder.append("_scen_");
                nameBuilder.append(scenarioNum);
                nameBuilder.append("_fullEnum");
                name = nameBuilder.toString();
                ss.writeLPFile(name + ".lp");
            }

            ss.solve();
            if (Parameters.isDebugVerbose()) {
                ss.writeCplexSolution(name + ".xml");
            }

            if (solveForQuality) {
                ss.collectSolution();
                buildDelaySolution(ss, randomDelays, tailPathsMap);
            } else {
                ss.collectDuals();
                double scenAlpha = calculateAlpha(ss.getDualsLeg(), ss.getDualsTail(), ss.getDualsDelay(),
                        ss.getDualsBound(), ss.getDualRisk());

                final int cutNum = Parameters.isBendersMultiCut() ? scenarioNum : 0;
                updateAlpha(cutNum, scenAlpha);
                updateBeta(cutNum, ss.getDualsDelay(), ss.getDualRisk());
                updateUpperBound(ss.getObjValue());
            }

            logger.info("Total number of paths: " + allPaths.size());
            logger.info("Iter " + iter + ": subproblem objective value: " + ss.getObjValue());
            ss.end();
        } catch (OptException oe) {
            logger.error("submodel run for scenario " + scenarioNum + " failed.");
            logger.error(oe);
            System.exit(Constants.ERROR_CODE);
        }
    }

    private void solveWithLabeling() throws IloException, OptException {
        SubSolver ss = new SubSolver(scenarioNum, dataRegistry.getTails(), dataRegistry.getLegs(), reschedules);

        // Load on-plan paths with propagated delays.
        HashMap<Integer, ArrayList<Path>> pathsAll = pathCache.getCachedPaths();

        // Run the column generation procedure.
        ArrayList<Leg> legs = dataRegistry.getLegs();

        boolean optimal = false;
        int columnGenIter = 0;

        Dual updatedDual = getInitialDual();
        boolean cutUpdated = false;

        double[] xValues = new double[reschedules.length];
        for (int i = 0; i < reschedules.length; ++i)
            xValues[i] = reschedules[i];

        while (!optimal) {
            // Solve second-stage RMP (Restricted Master Problem)
            ss.constructSecondStage(pathsAll);

            String name = "dummy";
            if (Parameters.isDebugVerbose()) {
                StringBuilder nameBuilder = new StringBuilder();
                nameBuilder.append("logs/");
                if (solveForQuality) {
                    nameBuilder.append("qual");
                } else {
                    nameBuilder.append("sub_benders_");
                    nameBuilder.append(iter);
                }
                nameBuilder.append("_scen_");
                nameBuilder.append(scenarioNum);
                nameBuilder.append("_labelingIter_");
                nameBuilder.append(columnGenIter);
                name = nameBuilder.toString();
                ss.writeLPFile(name + ".lp");
            }

            ss.solve();
            ss.collectDuals();
            Dual infeasibleDual = new Dual(ss.getDualsLeg().clone(), ss.getDualsTail().clone(), ss.getDualsDelay().clone());

            if (Parameters.isDebugVerbose())
                ss.writeCplexSolution(name + ".xml");

            // Collect paths with negative reduced cost from the labeling algorithm. Optimality is reached when
            // there are no new negative reduced cost paths available for any tail.
            ArrayList<Tail> tails = dataRegistry.getTails();
            double[] tailDuals = ss.getDualsTail();
            optimal = true;
            double lambda = 0.0;

            for (int i = 0; i < tails.size(); ++i) {
                Tail tail = tails.get(i);

                PricingProblemSolver lpg = new PricingProblemSolver(tail, legs, dataRegistry.getNetwork(),
                        randomDelays, tailDuals[i], ss.getDualsLeg(), ss.getDualsDelay());

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
                    if (lambda <= 1.0 - Constants.EPS) {
                        for (Path tailPath : tailPaths) {
                            final double infeasDualPathSlack = infeasibleDual.getPathSlack(tailPath);
                            final double feasibleDualPathSlack = updatedDual.getPathSlack(tailPath);
                            double lambdaPath = (-infeasDualPathSlack / (feasibleDualPathSlack - infeasDualPathSlack));
                            lambda = Math.max(lambda, lambdaPath);
                        }
                    }
                }
            }

            if (!solveForQuality) {
                // update lambda using reduced costs of d_f variables.
                for (Leg leg : dataRegistry.getLegs()) {
                    final double infeasibleDualLegSlack = infeasibleDual.getLegSlack(leg);
                    if (infeasibleDualLegSlack <= -Constants.EPS) {
                        final double feasibleDualLegSlack = updatedDual.getLegSlack(leg);
                        double lambdaLeg = (-infeasibleDualLegSlack / (feasibleDualLegSlack - infeasibleDualLegSlack));
                        lambda = Math.max(lambda, lambdaLeg);
                    }
                }
            }

            logger.debug("Iter " + iter + ": scenario " + scenarioNum + ": lambda: " + lambda);

            // Verify optimality using the feasibility of \pi_f + b_f >= 0 for all flights.
            double[] delayDuals = ss.getDualsDelay();
            for (int i = 0; i < legs.size(); ++i) {
                if (delayDuals[i] + legs.get(i).getDelayCostPerMin() <= -Constants.EPS) {
                    logger.error("no new paths, but solution not dual feasible");
                    throw new OptException("invalid optimality in second stage branch and price");
                }
            }

            if (!solveForQuality) {
                // updatedDual = updatedDual.getFeasibleDual(infeasibleDual, pathsAll, dataRegistry.getLegs());
                updatedDual = updatedDual.getFeasibleDual(infeasibleDual, lambda);
                BendersCut cutFromDual = updatedDual.getBendersCut(ss.getDualsBound(), probability);
                if (cutFromDual.separates(xValues, thetaValue)) {
                    logger.debug("Iter " + iter + ": scenario " + scenarioNum + ": separating cut found, stopping column gen");
                    bendersData.setCut(scenarioNum, cutFromDual);
                    cutUpdated = true;
                    break;
                }
            }

            // Cleanup CPLEX continers of the SubSolver object.
            if (!optimal)
                ss.end();
            ++columnGenIter;
        }

        int numPaths = 0;
        for (Map.Entry<Integer, ArrayList<Path>> entry : pathsAll.entrySet())
            numPaths += entry.getValue().size();

        logger.info("Total number of paths: " + numPaths);
        logger.info("Iter " + iter + ": subproblem objective value: " + ss.getObjValue());

        if (solveForQuality) {
            // Solve problem with all columns as MIP to collect objective value.
            ss.setSolveAsMIP();
            ss.constructSecondStage(pathsAll);
            ss.solve();

            if (Parameters.isDebugVerbose()) {
                String name = "logs/qual_";
                if (filePrefix != null)
                    name += filePrefix +"_";
                name += iter + "_sub_labeling_mip";
                ss.writeLPFile(name + ".lp");
                ss.writeCplexSolution(name + ".xml");
            }

            ss.collectSolution();
            buildDelaySolution(ss, randomDelays, pathsAll);
            ss.end();
        } else {
            if (!cutUpdated) {
                // Update master problem data
                double scenAlpha = calculateAlpha(ss.getDualsLeg(), ss.getDualsTail(), ss.getDualsDelay(),
                        ss.getDualsBound(), ss.getDualRisk());

                final int cutNum = Parameters.isBendersMultiCut() ? scenarioNum : 0;
                updateAlpha(cutNum, scenAlpha);
                updateBeta(cutNum, ss.getDualsDelay(), ss.getDualRisk());
            }

            updateUpperBound(ss.getObjValue());

            // cache best paths for each tail
            ss.collectSolution();
            pathCache.addPaths(getBestPaths(ss.getyValues(), pathsAll));
        }
    }

    private Dual getInitialDual() {
        double[] dualsTail = new double[dataRegistry.getTails().size()];
        Arrays.fill(dualsTail, 0);

        ArrayList<Leg> legs = dataRegistry.getLegs();
        double[] dualsLeg = new double[legs.size()];
        double[] dualsDelay = new double[legs.size()];

        Arrays.fill(dualsLeg, 0.0);
        for (int i = 0; i < legs.size(); ++i)
            dualsDelay[i] = -legs.get(i).getDelayCostPerMin();

        Dual dual = new Dual(dualsLeg, dualsTail, dualsDelay);
        return dual;
    }

    private void buildDelaySolution(SubSolver ss, int[] primaryDelays, HashMap<Integer, ArrayList<Path>> tailPaths) {
        // find selected paths for each tail
        ArrayList<Tail> tails = dataRegistry.getTails();
        ArrayList<Path> selectedPaths = new ArrayList<>();
        double[][] yValues = ss.getyValues();

        for (int i = 0; i < tails.size(); ++i) {
            Tail tail = tails.get(i);
            ArrayList<Path> generatedPaths = tailPaths.getOrDefault(tail.getId(), null);
            if (generatedPaths == null)
                continue;

            double[] yValuesForTail = yValues[i];
            for (int j = 0; j < yValuesForTail.length; ++j) {
                if (yValuesForTail[j] >= Constants.EPS) {
                    selectedPaths.add(generatedPaths.get(j));
                    break;
                }
            }
        }

        // collect total second-stage delay for each tail
        int[] totalDelays = new int[dataRegistry.getLegs().size()];
        int[] propagatedDelays = new int[dataRegistry.getLegs().size()];
        Arrays.fill(totalDelays, 0);
        Arrays.fill(propagatedDelays, 0);

        for (Path path : selectedPaths) {
            ArrayList<Leg> pathLegs = path.getLegs();
            if (pathLegs.isEmpty())
                continue;

            ArrayList<Integer> pathDelays = path.getDelayTimesInMin();
            for (int i = 0; i < pathLegs.size(); ++i) {
                int index = pathLegs.get(i).getIndex();
                totalDelays[index] = pathDelays.get(i);
                propagatedDelays[index] = totalDelays[index] - primaryDelays[index];
            }
        }

        delaySolution = new DelaySolution(ss.getObjValue(), primaryDelays, totalDelays, propagatedDelays,
                ss.getdValues());
    }

    private HashMap<Integer, Path> getBestPaths(double[][] yValues, HashMap<Integer, ArrayList<Path>> allPaths) {
        HashMap<Integer, Path> bestPaths = new HashMap<>();

        for (Tail tail : dataRegistry.getTails()) {
            ArrayList<Path> pathsForTail = allPaths.getOrDefault(tail.getId(), null);
            if (pathsForTail == null)
                continue;

            double bestVal = 0.0;
            Path bestPath = null;

            double[] yValuesForTail = yValues[tail.getIndex()];
            for (int i =0; i < yValuesForTail.length; ++i) {
                if (yValuesForTail[i] >= bestVal + Constants.EPS) {
                    bestVal = yValuesForTail[i];
                    bestPath = pathsForTail.get(i);
                }
            }

            if (bestPath != null)
                bestPaths.put(tail.getId(), bestPath);
        }

        return bestPaths;
    }

    private double calculateAlpha(double[] dualsLegs, double[] dualsTail, double[] dualsDelay, double[][] dualsBnd,
                                  double dualRisk) {
        ArrayList<Leg> legs = dataRegistry.getLegs();

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

        return scenAlpha;
    }

    private synchronized void updateAlpha(int cutNum, double scenAlpha) {
        BendersCut aggregatedCut = bendersData.getCut(cutNum);
        aggregatedCut.setAlpha(aggregatedCut.getAlpha() + (scenAlpha * probability));
    }

    private synchronized void updateBeta(int cutNum, double[] dualsDelay, double dualRisk) {
        ArrayList<Leg> legs = dataRegistry.getLegs();
        double[] beta = bendersData.getCut(cutNum).getBeta();

        for (int j = 0; j < legs.size(); j++) {
            if (Math.abs(dualsDelay[j]) >= Constants.EPS)
                beta[j] += (-dualsDelay[j] * probability);

            if (Parameters.isExpectedExcess() && Math.abs(dualRisk) >= Constants.EPS)
                beta[j] += (dualRisk * probability);
        }
    }

    private synchronized void updateUpperBound(double objValue) {
        bendersData.setUpperBound(bendersData.getUpperBound() + (objValue * probability));
    }
}


