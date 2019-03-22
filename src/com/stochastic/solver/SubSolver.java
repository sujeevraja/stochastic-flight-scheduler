package com.stochastic.solver;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.registry.DataRegistry;
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
     * Class that is used to solve a set packing model with CPLEX using a given
     * set of paths.
     */
    private final Logger logger = LogManager.getLogger(SubSolver.class);
    private double probability;
    private double objValue;

    private double[] dualsLeg;
    private double[] dualsTail;
    private double[] dualsDelay;
    private double[][] dualsBnd;
    private double dualsRisk;

    private int[][] indices;
    private double[][] values;

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
    private IloRange[][] boundConstraints;
    private IloRange riskConstraint;

    public SubSolver(double probability) {
        this.probability = probability;
    }

    public void constructSecondStage(int[] reschedules, DataRegistry dataRegistry, int sceNo, int iter,
                                     HashMap<Integer, ArrayList<Path>> paths) throws OptException {
        try {
            ArrayList<Tail> tails = dataRegistry.getTails();
            ArrayList<Leg> legs = dataRegistry.getLegs();
            int[] durations = Parameters.getDurations();

            // Create containers to build CPLEX model.
            subCplex = new IloCplex();

            if (!Parameters.isDebugVerbose())
                subCplex.setOut(null);

            final int numLegs = legs.size();
            final int numTails = tails.size();
            final int numDurations = durations.length;

            y = new IloNumVar[tails.size()][];

            for (int i = 0; i < tails.size(); i++)
                y[i] = new IloNumVar[paths.get(tails.get(i).getId()).size()];

            d = new IloNumVar[numLegs];
            legCoverConstraints = new IloRange[legs.size()]; // leg coverage
            onePathPerTailConstraints = new IloRange[tails.size()]; // tail coverage
            legDelayLinkConstraints = new IloRange[legs.size()]; // delay constraints

            boundConstraints = new IloRange[tails.size()][];
            dualsBnd = new double[tails.size()][];

            for (int i = 0; i < tails.size(); i++) {
                boundConstraints[i] = new IloRange[paths.get(tails.get(i).getId()).size()];
                dualsBnd[i] = new double[paths.get(tails.get(i).getId()).size()]; // delay constraints
            }

            dualsLeg = new double[legs.size()];
            dualsTail = new double[tails.size()];
            dualsDelay = new double[legs.size()];

            IloLinearNumExpr[] legCoverExprs = new IloLinearNumExpr[numLegs];
            IloLinearNumExpr[] tailCoverExprs = new IloLinearNumExpr[numTails];

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
                legCoverExprs[i] = subCplex.linearNumExpr();
                delayExprs[i] = subCplex.linearNumExpr();
                delayExprs[i].addTerm(d[i], -1.0);

                if (reschedules[i] > 0)
                    delayRHS[i] += reschedules[i];
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
                    y[i][j] = subCplex.numVar(0, Double.MAX_VALUE, "y_" + tails.get(i).getId() + "_" + j);

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
                onePathPerTailConstraints[i] = subCplex.addEq(
                       tailCoverExprs[i], 1.0, "tail_" + i + "_" + tails.get(i).getId());

            for (int i = 0; i < numLegs; ++i) {
                legCoverConstraints[i] = subCplex.addEq(
                        legCoverExprs[i], 1.0, "leg_" + i + "_" + legs.get(i).getId());
            }

            for (int i = 0; i < numLegs; ++i) {
                legDelayLinkConstraints[i] = subCplex.addLe(
                        delayExprs[i], delayRHS[i], "delay_" + i + "_" + legs.get(i).getId());
            }

            for (int i = 0; i < tails.size(); i++)
                for (int j = 0; j < y[i].length; j++) {
                    String name = "bound_" + tails.get(i).getId() + "_j";
                    boundConstraints[i][j] = subCplex.addLe(y[i][j], 1, name);
                }

            if (Parameters.isExpectedExcess()) {
                double xVal = Arrays.stream(reschedules).sum();
                IloLinearNumExpr riskExpr = subCplex.linearNumExpr();

                for (int i = 0; i < numLegs; i++)
                    riskExpr.addTerm(d[i], 1.5);

                riskExpr.addTerm(v, -1);
                riskConstraint = subCplex.addLe(riskExpr, Parameters.getExcessTarget() - xVal, "risk");
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
        } catch (IloException ie) {
            logger.error(ie);
            throw new OptException("error solving subproblem");
        }
    }

    public void collectDuals() throws OptException {
        try {
            for (int i = 0; i < legCoverConstraints.length; i++)
                dualsLeg[i] = subCplex.getDual(legCoverConstraints[i]);

            for (int i = 0; i < onePathPerTailConstraints.length; i++)
                dualsTail[i] = subCplex.getDual(onePathPerTailConstraints[i]);

            dualsDelay = subCplex.getDuals(legDelayLinkConstraints);

            for (int i = 0; i < boundConstraints.length; i++)
                dualsBnd[i] = subCplex.getDuals(boundConstraints[i]);

            logger.debug(" legDelayLinkConstraints: " + legDelayLinkConstraints.length);
            logger.debug(" dualsBnd: " + dualsBnd.length);

            if (Parameters.isExpectedExcess())
                dualsRisk = subCplex.getDual(riskConstraint);

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

    public void writeCplexSolution(String fName, int iter, int wcnt, int sceNo) throws IloException {
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
        boundConstraints = null;
        riskConstraint = null;
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
