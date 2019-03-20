package com.stochastic.solver;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.solver.SubSolverWrapper.ScenarioData;
import com.stochastic.utility.Constants;
import com.stochastic.utility.OptException;
import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SubSolver {
    /**
     * Class that is used to solve a set packing model with CPLEX using a given
     * set of paths.
     */
    private final Logger logger = LogManager.getLogger(SubSolver.class);
    private HashMap<Integer, Integer> randomDelays; // random delays of 2nd stage scenario
    private double probability;
    //    private ArrayList<Path> paths; // subproblem columns
    private HashMap<Integer, ArrayList<Path>> paths; // subproblem columns    
    private double objValue;

    private double[] dualsLeg;
    private double[] dualsTail;
    private double[] dualsDelay;
    private double[][] dualsBnd;
    private double dualsRisk;

    private int[][] indices;
    private double[][] values;
//    private double[] yValues;

    // CPLEX variables
    private IloNumVar[][] y; // y[i][j] = 1 if path j is selected for tail i is selected, 0 else.
    private IloNumVar[] d; // d[i] >= 0 is the total delay of leg i.
    private IloNumVar v; // d[i] >= 0 is the total delay of leg i.
    private IloCplex subCplex;

    // CPLEX constraints/expressions
    private IloObjective obj;
    private IloRange[] legCoverConstraints;
    private IloRange[] onePathPerTailConstraints;
    private IloRange[] legDelayLinkConstraints;
    private IloRange[][] R4;
    private IloRange R5;

    private boolean[] legPresence;
    private boolean[] tailPresence;

    // private static IloNumVar neta; // = cplex.numVar(-Double.POSITIVE_INFINITY, 0, "neta");
    public SubSolver(HashMap<Integer, Integer> randomDelays, double probability) {
        this.randomDelays = randomDelays;
        this.probability = probability;
    }

    public void constructSecondStage(double[][] xValues, DataRegistry dataRegistry, int sceNo, int iter,
                                     HashMap<Integer, ArrayList<Path>> p) throws OptException {
        try {
            ArrayList<Tail> tails = dataRegistry.getTails();
            ArrayList<Leg> legs = dataRegistry.getLegs();
            ArrayList<Integer> durations = Parameters.getDurations();

            paths = p;

            logger.info("number of tails: " + tails.size());
            logger.info("number of legs: " + legs.size());
            logger.info("number of durations: " + durations.size());
            int numPaths = 0;
            for (Map.Entry<Integer, ArrayList<Path>> entry : p.entrySet())
                numPaths += entry.getValue().size();
            logger.info("number of paths given to second stage: " + numPaths);

            // Create containers to build CPLEX model.
            subCplex = new IloCplex();
            final Integer numLegs = legs.size();
            final Integer numTails = tails.size();
            final Integer numDurations = durations.size();

            y = new IloNumVar[tails.size()][];

            for (int i = 0; i < tails.size(); i++)
                y[i] = new IloNumVar[p.get(tails.get(i).getId()).size()];

//            for(int i=0; i< tails.size(); i++)
//                for(int j=0; j< y[i].length; j++)
//                	y[i][j].setName("y_" + tails.get(i).getId() + "_" + j);

            d = new IloNumVar[numLegs];
            legCoverConstraints = new IloRange[legs.size()]; // leg coverage
            onePathPerTailConstraints = new IloRange[tails.size()]; // tail coverage
            legDelayLinkConstraints = new IloRange[legs.size()]; // delay constraints

//            for(int i=0; i< tails.size(); i++)
//                for(int j=0; j< y[i].length; j++)            
            R4 = new IloRange[tails.size()][]; // delay constraints
            dualsBnd = new double[tails.size()][]; // delay constraints

            for (int i = 0; i < tails.size(); i++) {
                R4[i] = new IloRange[p.get(tails.get(i).getId()).size()];
                dualsBnd[i] = new double[p.get(tails.get(i).getId()).size()]; // delay constraints            	
            }

            dualsLeg = new double[legs.size()];
            dualsTail = new double[tails.size()];
            dualsDelay = new double[legs.size()];
//            dualsBnd = new double[paths.size()];

            IloLinearNumExpr[] legCoverExprs = new IloLinearNumExpr[numLegs];
            IloLinearNumExpr[] tailCoverExprs = new IloLinearNumExpr[numTails];
            legPresence = new boolean[numLegs];
            tailPresence = new boolean[numLegs];

            for (int i = 0; i < numTails; ++i)
                tailCoverExprs[i] = subCplex.linearNumExpr();

            IloLinearNumExpr[] delayExprs = new IloLinearNumExpr[numLegs];
            double[] delayRHS = new double[numLegs];

            // Build objective, initialize leg coverage and delay constraints.
            IloLinearNumExpr objExpr = subCplex.linearNumExpr();
            for (int i = 0; i < numLegs; i++) {
                Leg leg = legs.get(i);
                d[i] = subCplex.numVar(0, Double.MAX_VALUE, "d_" + leg.getId());

                delayRHS[i] = 14.0; // OTP time limit

                objExpr.addTerm(d[i], leg.getDelayCostPerMin());
                // objExpr.addTerm(d[i], leg.getBlockTimeInMin());
                // objExpr.addTerm(d[i], 1.0);
                // objExpr.addTerm(d[i], probability*1.5);
                legCoverExprs[i] = subCplex.linearNumExpr();
                delayExprs[i] = subCplex.linearNumExpr();
                delayExprs[i].addTerm(d[i], -1.0);

                for (int j = 0; j < numDurations; ++j)
                    if (xValues[j][i] >= Constants.EPS) {
                        delayRHS[i] += durations.get(j);
                        break;
                    }
            }

            if (Parameters.isExpectedExcess()) {
                v = subCplex.numVar(0, Double.MAX_VALUE, "v");
                objExpr.addTerm(v, probability * Parameters.getRho());
            }

            subCplex.addMinimize(objExpr);
            objExpr.clear();

            // Create path variables and add them to constraints.
            for (int i = 0; i < tails.size(); i++) {
                for (int j = 0; j < y[i].length; j++) {
                    y[i][j] = subCplex.numVar(0, 1, "y_" + tails.get(i).getId() + "_" + j);

                    Tail tail = tails.get(i);
                    tailCoverExprs[tail.getIndex()].addTerm(y[i][j], 1.0);
                    tailPresence[tail.getIndex()] = true;

                    ArrayList<Leg> pathLegs = paths.get(tail.getId()).get(j).getLegs(); // path.getLegs();
                    ArrayList<Integer> delayTimes = paths.get(tail.getId()).get(j).getDelayTimesInMin();

                    for (int k = 0; k < pathLegs.size(); ++k) {
                        Leg pathLeg = pathLegs.get(k);
                        legCoverExprs[pathLeg.getIndex()].addTerm(y[i][j], 1.0);
                        legPresence[pathLeg.getIndex()] = true;

                        Integer delayTime = delayTimes.get(k);

                        if (delayTime > 0) {
                            SubSolverWrapper.ScenarioData sd = new SubSolverWrapper.ScenarioData();
                            sd.setSceNo(sceNo);
                            sd.setIter(iter);
                            sd.setPathIndex(paths.get(tail.getId()).get(j).getIndex());
                            sd.setTailIndex(tail.getIndex());
                            sd.setLegId(pathLeg.getIndex());

                            delayExprs[pathLeg.getIndex()].addTerm(y[i][j], delayTime);
                            ScenarioData.addData(
                                    sceNo,
                                    iter,
                                    paths.get(tail.getId()).get(j).getIndex(),
                                    tail.getIndex(),
                                    pathLeg.getIndex(),
                                    delayTime);
                        }
                    }
                }
            }

            // Add constraints to model.
            for (int i = 0; i < numTails; ++i)
                if (tailPresence[i])
                    onePathPerTailConstraints[i] = subCplex.addEq(
                           tailCoverExprs[i], 1.0, "tail_" + i + "_" + tails.get(i).getId());
                    // onePathPerTailConstraints[i] = subCplex.addLe(
                    //        tailCoverExprs[i], 1.0, "tail_" + i + "_" + tails.get(i).getId());

            for (int i = 0; i < numLegs; ++i) {
                if (legPresence[i])
                    legCoverConstraints[i] = subCplex.addEq(
                            legCoverExprs[i], 1.0, "leg_" + i + "_" + legs.get(i).getId());
            }

            for (int i = 0; i < numLegs; ++i) {
                legDelayLinkConstraints[i] = subCplex.addLe(
                        delayExprs[i], delayRHS[i], "delay_" + i + "_" + legs.get(i).getId());
            }

            for (int i = 0; i < tails.size(); i++)
                for (int j = 0; j < y[i].length; j++)
                    R4[i][j] = subCplex.addLe(y[i][j], 1, "yBound_" + i + "_" + j);

//            for (int i = 0; i < paths.size(); ++i)
//                R4[i] = subCplex.addLe(y[i], 1, "yBound_" + i);

            if (Parameters.isExpectedExcess()) {
                double xVal = 0;
                for (int i = 0; i < legs.size(); i++)
                    for (int j = 0; j < durations.size(); j++)
                        xVal += (xValues[j][i] * durations.get(j));

                IloLinearNumExpr riskExpr = subCplex.linearNumExpr();

                for (int i = 0; i < numLegs; i++)
                    riskExpr.addTerm(d[i], 1.5);

                riskExpr.addTerm(v, -1);

                R5 = subCplex.addLe(riskExpr, Parameters.getExcessTarget() - xVal, "risk");
            }

            // Clear all constraint expressions to avoid memory leaks.
            for (int i = 0; i < numLegs; ++i) {
                legCoverExprs[i].clear();
                delayExprs[i].clear();
            }

            for (int i = 0; i < numTails; ++i)
                tailCoverExprs[i].clear();

        } catch (IloException e) {
            logger.error(e.getStackTrace());
            throw new OptException("CPLEX error solving first stage MIP");
        }
    }

    public void solve() throws OptException {
        try {
            subCplex.setParam(IloCplex.IntParam.RootAlg, IloCplex.Algorithm.Dual);
            subCplex.setParam(IloCplex.BooleanParam.PreInd, false);

            subCplex.solve();

            IloCplex.Status status = subCplex.getStatus();
            if (status != IloCplex.Status.Optimal) {
                logger.error("sub-problem status: " + status);
                throw new OptException("optimal solution not found for sub-problem");
            }
            objValue = subCplex.getObjValue();
            logger.debug("subproblem objective value: " + objValue);

//            yValues = new double[paths.size()];
//            yValues = subCplex.getValues(y);
        } catch (IloException ie) {
            logger.error(ie);
            throw new OptException("error solving subproblem");
        }
    }

    public void collectDuals() throws OptException {
        try {
            for (int i = 0; i < legCoverConstraints.length; i++)
                if (legPresence[i])
                    dualsLeg[i] = subCplex.getDual(legCoverConstraints[i]);

            for (int i = 0; i < onePathPerTailConstraints.length; i++)
                if (tailPresence[i])
                    dualsTail[i] = subCplex.getDual(onePathPerTailConstraints[i]);

            dualsDelay = subCplex.getDuals(legDelayLinkConstraints);

            for (int i = 0; i < R4.length; i++)
                dualsBnd[i] = subCplex.getDuals(R4[i]);

            logger.debug(" legDelayLinkConstraints: " + legDelayLinkConstraints.length);
            logger.debug(" dualsBnd: " + dualsBnd.length);

            if (Parameters.isExpectedExcess())
                dualsRisk = subCplex.getDual(R5);

        } catch (IloException ie) {
            logger.error(ie);
            throw new OptException("error when collecting duals from sub-problem");
        }
    }

    public void writeLPFile(String fName, int iter, int wcnt, int sceNo) throws IloException {
        String modelName = fName + "sub_" + iter + "_scen_" + sceNo;

        if (wcnt >= 0)
            modelName += "_labelingIter_" + wcnt;
        else
            modelName += "_fullEnum";
        modelName += ".lp";

        subCplex.exportModel(modelName);
    }

    public void writeSolution(String fName, int iter, int wcnt, int sceNo) throws IloException {
        String slnName = fName + "sub_" + iter + "_scen_" + sceNo;

        if (wcnt >= 0)
            slnName += "_labelingIter_" + wcnt;
        else
            slnName += "_fullEnum";
        slnName += ".xml";

        subCplex.writeSolution(slnName);
    }

    public void end() {
        y = null;
        d = null;
        legCoverConstraints = null;
        onePathPerTailConstraints = null;
        legDelayLinkConstraints = null;
        R4 = null;
        R5 = null;
        dualsLeg = null;
        dualsTail = null;
        dualsDelay = null;
        subCplex.end();
    }

    public double[] getDualsLeg() {
        return dualsLeg;
    }

    public double[] getDualsTail() {
        return dualsTail;
    }

    public double[] getDualsDelay() {
        return dualsDelay;
    }

    public double[][] getDualsBnd() {

        logger.debug(" xxx-dualsBnd: " + dualsBnd.length);
        return dualsBnd;
    }

    public double getDualsRisk() {
        return dualsRisk;
    }

    public double getObjValue() {
        return objValue;
    }
}
