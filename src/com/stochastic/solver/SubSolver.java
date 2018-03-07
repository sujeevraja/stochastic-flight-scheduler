package com.stochastic.solver;

import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Network;
import com.stochastic.network.Path;
import com.stochastic.registry.DataRegistry;
import com.stochastic.utility.OptException;
import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SubSolver {
    /**
     * Class that is used to solve a set packing model with CPLEX using a given
     * set of paths.
     */
    private final Logger logger = LogManager.getLogger(SubSolver.class);
    private final double eps = 1.0e-5;
    private ArrayList<Path> paths; // subproblem columns

    // cplex variables
    private IloNumVar[] y; // y[i] = 1 if path i is selected, 0 else.
    private IloNumVar[] d; // d[i] >= 0 is the total delay of leg i.
    private IloCplex subCplex;
    private IloRange[] R;

    private double objValue;
    private double[] duals;

    private int[][] indices;
    private double[][] values;
    private double[] yValues;

    // private static IloNumVar neta; // = cplex.numVar(-Double.POSITIVE_INFINITY, 0, "neta");
    public SubSolver() {}

    public void constructSecondStage(double[][] xValues, DataRegistry dataRegistry) throws OptException {
        try {
            ArrayList<Tail> tails = dataRegistry.getTails();
            ArrayList<Leg> legs = dataRegistry.getLegs();
            ArrayList<Integer> durations = dataRegistry.getDurations();

            // get delay data using planned delays from first stage and random delays from second stage.
            HashMap<Integer, Integer> legDelayMap = getLegDelays(tails, legs, durations, xValues);

            Network network = new Network(tails, legs, legDelayMap, dataRegistry.getWindowStart(),
                    dataRegistry.getWindowEnd(), dataRegistry.getMaxLegDelayInMin());

            // Later, the full enumeration algorithm in enumerateAllPaths() will be replaced a labeling algorithm.
            paths = network.enumerateAllPaths();

            // Create containers to build CPLEX model.
            subCplex = new IloCplex();
            final Integer numLegs = legs.size();
            final Integer numTails = tails.size();
            final Integer numDurations = durations.size();
            final Integer maxDelay = dataRegistry.getMaxLegDelayInMin();

            y = new IloNumVar[paths.size()];
            d = new IloNumVar[numLegs];

            IloLinearNumExpr[] legCoverExprs = new IloLinearNumExpr[numLegs];
            IloLinearNumExpr[] tailCoverExprs = new IloLinearNumExpr[numTails];
            for(int i = 0; i < numTails; ++i)
                tailCoverExprs[i] = subCplex.linearNumExpr();

            IloLinearNumExpr[] delayExprs = new IloLinearNumExpr[numLegs];
            double[] delayRHS = new double[numLegs];

            // Build objective, initialize leg coverage and delay constraints.
            IloLinearNumExpr objExpr = subCplex.linearNumExpr();
            for (int i = 0; i < numLegs; i++) {
                d[i] = subCplex.numVar(0, maxDelay, "d_" + legs.get(i).getId());
                // boolVarArray(Data.nD + Data.nT);

                delayRHS[i] = 14.0; // OTP time limit

                objExpr.addTerm(d[i], 1);
                legCoverExprs[i] = subCplex.linearNumExpr();
                delayExprs[i] = subCplex.linearNumExpr();
                delayExprs[i].addTerm(d[i], -1.0);

                for(int j = 0; j < numDurations; ++j)
                    if(xValues[j][i] >= eps) {
                        delayRHS[i] += durations.get(j);
                        break;
                    }
            }
            subCplex.addMinimize(objExpr);
            objExpr.clear();

            // Create path variables and add them to constraints.
            for(int i = 0; i < paths.size(); ++i) {
                y[i] = subCplex.numVar(0, 1, "y_" + paths.get(i).getIndex());
                // boolVarArray(Data.nD + Data.nT);

                Path path = paths.get(i);
                tailCoverExprs[path.getTail().getIndex()].addTerm(y[i], 1.0);

                ArrayList<Leg> pathLegs = path.getLegs();
                ArrayList<Integer> delayTimes = path.getDelayTimesInMin();

                for(int j = 0; j < pathLegs.size(); ++j) {
                    Leg pathLeg = pathLegs.get(j);
                    legCoverExprs[pathLeg.getIndex()].addTerm(y[i], 1.0);

                    final Integer delayTime = delayTimes.get(j);
                    if(delayTime > 0)
                        delayExprs[pathLeg.getIndex()].addTerm(y[i], delayTime);
                }
            }

            // Add constraints to model.
            for(int i = 0; i < numTails; ++i)
                subCplex.addLe(tailCoverExprs[i], 1.0, "tail_" + i + "_" + tails.get(i).getId());

            R = new IloRange[numLegs];
            for(int i = 0;  i < numLegs; ++i) {
                subCplex.addEq(legCoverExprs[i], 1.0, "leg_" + i + "_" + legs.get(i).getId());

                R[i] = subCplex.addLe(delayExprs[i], delayRHS[i], "delay_" + i + "_" + legs.get(i).getId());
            }

            subCplex.exportModel("sub.lp");

            // Clear all constraint expressions to avoid memory leaks.
            for(int i = 0; i < numLegs; ++i) {
                legCoverExprs[i].clear();
                delayExprs[i].clear();
            }
            for(int i = 0; i < numTails; ++i)
                tailCoverExprs[i].clear();
        } catch (IloException e) {
            logger.error(e.getStackTrace());
            throw new OptException("CPLEX error solving first stage MIP");
        }
    }

    private HashMap<Integer, Integer> getLegDelays(ArrayList<Tail> tails, ArrayList<Leg> legs,
                                                   ArrayList<Integer> durations, double[][] xValues) {
        // Generate random delays using a delay generator.
        DelayGenerator dgen = new FirstFlightDelayGenerator(tails);
        HashMap<Integer, Integer> randomDelays = dgen.generateDelays();

        // Collect planned delays from first stage solution.
        HashMap<Integer, Integer> plannedDelays = new HashMap<>();
        for(int i = 0; i < durations.size(); ++i) {
            for(int j = 0; j < legs.size(); ++j) {
                if(xValues[i][j] >= eps)
                    plannedDelays.put(legs.get(j).getIndex(), durations.get(i));
            }
        }

        // Combine the delay maps into a single one.
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

    public void solve() {
        try {
//			Master.mastCplex.addMaximize();
            subCplex.solve();
            objValue = subCplex.getObjValue();
            logger.debug("Objective value: " + objValue);
            yValues = new double[paths.size()];
            yValues = subCplex.getValues(y);
            duals = subCplex.getDuals(R);
        } catch (IloException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.out.println("Error: SubSolve");
        }
    }

    public void writeLPFile(String fName, int iter) {
        try {
            subCplex.exportModel(fName + "sub" + iter + ".lp");
        } catch (IloException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.out.println("Error: GetLPFile-Sub");
        }
    }


    public void solve(boolean isMIP) throws OptException {
        try {
            subCplex.setParam(IloCplex.IntParam.RootAlg,
                    IloCplex.Algorithm.Dual);
            subCplex.setParam(IloCplex.BooleanParam.PreInd, false);

            subCplex.solve();
            objValue = subCplex.getObjValue();
            logger.debug("Objective value: " + objValue);

            if (!isMIP)
                duals = subCplex.getDuals(R);

        } catch (IloException e) {
            logger.error(e);
            throw new OptException("Error solving subproblem");
        }
    }

    public void end() {
        y = null;
        d = null;
        R = null;
        duals = null;
        subCplex.end();
    }

    public double[] getDuals() {
        return duals;
    }


    public void setDuals(double[] duals) {
        this.duals = duals;
    }


    public double getObjValue() {
        return objValue;
    }


    public void setObjValue(double objValue) {
        this.objValue = objValue;
    }
}
