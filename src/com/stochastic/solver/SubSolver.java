package com.stochastic.solver;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.Constants;
import com.stochastic.utility.OptException;
import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SubSolver {
    /**
     * SubSolver solves the second-stage problem of minimizing excess recourse delays using a given set of paths.
     */
    private static final Logger logger = LogManager.getLogger(SubSolver.class);

    private ArrayList<Tail> tails;
    private ArrayList<Leg> legs;
    private int numTails;
    private int numLegs;
    private int[] reschedules;  // reschedules[i] is the first-stage reschedule chosen for legs[i].
    private double probability;
    private boolean solveAsMIP;

    // CPLEX variables
    private IloNumVar[][] y; // y[i][j] = 1 if path j is selected for tails.get(i) is selected, 0 else.
    private IloNumVar[] d; // d[i] >= 0 is the total delay of leg i.
    private IloNumVar v;
    private IloCplex cplex;

    // CPLEX constraints/expressions
    private IloRange[] legCoverConstraints;
    private IloRange[] onePathPerTailConstraints;
    private IloRange[] legDelayLinkConstraints;
    private IloRange[][] boundConstraints;
    private IloRange riskConstraint;

    // Solution info
    private double objValue;
    private double[] dValues;
    private double[][] yValues;
    private double[] dualsTail;
    private double[] dualsLeg;
    private double[] dualsDelay;
    private double[][] dualsBound;
    private double dualRisk;

    SubSolver(ArrayList<Tail> tails, ArrayList<Leg> legs, int[] reschedules, double probability) {
        this.tails = tails;
        this.numTails = tails.size();
        this.legs = legs;
        this.numLegs = legs.size();
        this.reschedules = reschedules;
        this.probability = probability;
        solveAsMIP = false;

        dualsTail = new double[numTails];
        dualsLeg = new double[numLegs];
        dualsDelay = new double[numLegs];
        dualsBound = new double[numTails][];
        dualRisk = 0;
    }

    void setSolveAsMIP() {
        this.solveAsMIP = true;
    }

    void constructSecondStage(HashMap<Integer, ArrayList<Path>> paths) throws OptException {
        try {
            // Create containers to build CPLEX model.
            cplex = new IloCplex();

            if (!Parameters.isDebugVerbose())
                cplex.setOut(null);

            y = new IloNumVar[numTails][];
            for (int i = 0; i < numTails; i++)
                y[i] = new IloNumVar[paths.get(tails.get(i).getId()).size()];

            d = new IloNumVar[numLegs];
            legCoverConstraints = new IloRange[numLegs]; // leg coverage
            onePathPerTailConstraints = new IloRange[numTails]; // tail coverage
            legDelayLinkConstraints = new IloRange[numLegs]; // delay constraints
            boundConstraints = new IloRange[numTails][];

            IloLinearNumExpr[] tailCoverExprs = new IloLinearNumExpr[numTails];
            for (int i = 0; i < numTails; i++) {
                tailCoverExprs[i] = cplex.linearNumExpr();
                boundConstraints[i] = new IloRange[paths.get(tails.get(i).getId()).size()];
            }

            IloLinearNumExpr[] legCoverExprs = new IloLinearNumExpr[numLegs];
            IloLinearNumExpr[] delayExprs = new IloLinearNumExpr[numLegs];
            double[] delayRHS = new double[numLegs];

            // Build objective, initialize leg coverage and delay constraints.
            IloLinearNumExpr objExpr = cplex.linearNumExpr();
            for (int i = 0; i < numLegs; i++) {
                Leg leg = legs.get(i);
                d[i] = cplex.numVar(0, Double.MAX_VALUE, "d_" + leg.getId());

                delayRHS[i] = Constants.OTP_TIME_LIMIT_IN_MINUTES;

                objExpr.addTerm(d[i], leg.getDelayCostPerMin());
                legCoverExprs[i] = cplex.linearNumExpr();
                delayExprs[i] = cplex.linearNumExpr();
                delayExprs[i].addTerm(d[i], -1.0);

                if (reschedules[i] > 0)
                    delayRHS[i] += reschedules[i];
            }

            if (Parameters.isExpectedExcess()) {
                v = cplex.numVar(0, Double.MAX_VALUE, "v");
                objExpr.addTerm(v, probability * Parameters.getRho());
            }

            cplex.addMinimize(objExpr);
            objExpr.clear();

            // Create path variables and add them to constraints.
            for (int i = 0; i < numTails; i++) {
                for (int j = 0; j < y[i].length; j++) {
                    y[i][j] = cplex.numVar(0, Double.MAX_VALUE, "y_" + tails.get(i).getId() + "_" + j);

                    Tail tail = tails.get(i);
                    tailCoverExprs[tail.getIndex()].addTerm(y[i][j], 1.0);

                    ArrayList<Leg> pathLegs = paths.get(tail.getId()).get(j).getLegs(); // path.getLegs();
                    ArrayList<Integer> delayTimes = paths.get(tail.getId()).get(j).getDelayTimesInMin();

                    for (int k = 0; k < pathLegs.size(); ++k) {
                        Leg pathLeg = pathLegs.get(k);
                        legCoverExprs[pathLeg.getIndex()].addTerm(y[i][j], 1.0);

                        Integer delayTime = delayTimes.get(k);
                        if (delayTime > 0)
                            delayExprs[pathLeg.getIndex()].addTerm(y[i][j], delayTime);
                    }
                }
            }

            // Add constraints to model.
            for (int i = 0; i < numTails; ++i)
                onePathPerTailConstraints[i] = cplex.addEq(
                       tailCoverExprs[i], 1.0, "tail_" + i + "_" + tails.get(i).getId());

            for (int i = 0; i < numLegs; ++i) {
                legCoverConstraints[i] = cplex.addEq(
                        legCoverExprs[i], 1.0, "leg_" + i + "_" + legs.get(i).getId());
            }

            for (int i = 0; i < numLegs; ++i) {
                legDelayLinkConstraints[i] = cplex.addLe(
                        delayExprs[i], delayRHS[i], "delay_" + i + "_" + legs.get(i).getId());
            }

            for (int i = 0; i < numTails; i++)
                for (int j = 0; j < y[i].length; j++) {
                    String name = "bound_" + tails.get(i).getId() + "_j";
                    boundConstraints[i][j] = cplex.addLe(y[i][j], 1, name);
                }

            if (Parameters.isExpectedExcess()) {
                double xVal = Arrays.stream(reschedules).sum();
                IloLinearNumExpr riskExpr = cplex.linearNumExpr();

                for (int i = 0; i < numLegs; i++)
                    riskExpr.addTerm(d[i], 1.5);

                riskExpr.addTerm(v, -1);
                riskConstraint = cplex.addLe(riskExpr, Parameters.getExcessTarget() - xVal, "risk");
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
            cplex.setParam(IloCplex.IntParam.RootAlg, IloCplex.Algorithm.Dual);
            cplex.setParam(IloCplex.BooleanParam.PreInd, false);

            if (solveAsMIP) {
                for (int i = 0; i < tails.size(); ++i)
                    cplex.add(cplex.conversion(y[i], IloNumVarType.Int));
            }

            cplex.solve();
            IloCplex.Status status = cplex.getStatus();
            if (status != IloCplex.Status.Optimal) {
                logger.error("sub-problem status: " + status);
                throw new OptException("optimal solution not found for sub-problem");
            }

            objValue = cplex.getObjValue();
        } catch (IloException ie) {
            logger.error(ie);
            throw new OptException("error solving subproblem");
        }
    }

    public void collectSolution() throws IloException {
        dValues = cplex.getValues(d);
        yValues = new double[tails.size()][];
        for (int i = 0; i < tails.size(); ++i)
            yValues[i] = cplex.getValues(y[i]);
    }

    public void collectDuals() throws OptException {
        try {
            for (int i = 0; i < legCoverConstraints.length; i++)
                dualsLeg[i] = cplex.getDual(legCoverConstraints[i]);

            for (int i = 0; i < onePathPerTailConstraints.length; i++)
                dualsTail[i] = cplex.getDual(onePathPerTailConstraints[i]);

            dualsDelay = cplex.getDuals(legDelayLinkConstraints);

            for (int i = 0; i < boundConstraints.length; i++)
                dualsBound[i] = cplex.getDuals(boundConstraints[i]);

            if (Parameters.isExpectedExcess())
                dualRisk = cplex.getDual(riskConstraint);
        } catch (IloException ie) {
            logger.error(ie);
            throw new OptException("error when collecting duals from sub-problem");
        }
    }

    public void writeLPFile(String name) throws IloException {
        cplex.exportModel(name);
    }

    public void writeCplexSolution(String name) throws IloException {
        cplex.writeSolution(name);
    }

    public void end() {
        y = null;
        d = null;
        v = null;

        legCoverConstraints = null;
        onePathPerTailConstraints = null;
        legDelayLinkConstraints = null;
        boundConstraints = null;
        riskConstraint = null;

        cplex.end();
        cplex = null;
    }

    public double getObjValue() {
        return objValue;
    }

    public double[] getdValues() {
        return dValues;
    }

    public double[][] getyValues() {
        return yValues;
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

    public double[][] getDualsBound() {
        return dualsBound;
    }

    public double getDualRisk() {
        return dualRisk;
    }
}
