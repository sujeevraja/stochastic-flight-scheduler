package com.stochastic.solver;

import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.TestDelayGenerator;
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
    private HashMap<Integer, Integer> randomDelays; // random delays of 2nd stage scenario
    private final double eps = 1.0e-5;
    private ArrayList<Path> paths; // subproblem columns

    // cplex variables
    private IloNumVar[] y; // y[i] = 1 if path i is selected, 0 else.
    private IloNumVar[] d; // d[i] >= 0 is the total delay of leg i.
    private IloCplex subCplex;

    private double objValue;
    private double[] duals;
    private double[] duals1;
    private double[] duals2;
    private double[] duals3;


    private int[][] indices;
    private double[][] values;
    private double[] yValues;
    
    

    private IloObjective obj;
    private IloRange[] R1;
    private IloRange[] R2;
    private IloRange[] R3;

    // private static IloNumVar neta; // = cplex.numVar(-Double.POSITIVE_INFINITY, 0, "neta");
    public SubSolver(HashMap<Integer, Integer> randomDelays) {
        this.randomDelays = randomDelays;
    }

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
            R1 = new IloRange[legs.size()]; // leg coverage
            R2 = new IloRange[tails.size()]; // tail coverage
            R3 = new IloRange[legs.size()]; // delay constraints
            duals1 =  new double[legs.size()];
            duals2 =  new double[tails.size()];
            duals3 =  new double[legs.size()];


            IloLinearNumExpr[] legCoverExprs = new IloLinearNumExpr[numLegs];
            IloLinearNumExpr[] tailCoverExprs = new IloLinearNumExpr[numTails];
            for (int i = 0; i < numTails; ++i)
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

                for (int j = 0; j < numDurations; ++j)
                    if (xValues[j][i] >= eps) {
                        delayRHS[i] += durations.get(j);
                        break;
                    }
            }
            subCplex.addMinimize(objExpr);
            objExpr.clear();

            // Create path variables and add them to constraints.
            for (int i = 0; i < paths.size(); ++i) {
                y[i] = subCplex.numVar(0, 1, "y_" + paths.get(i).getIndex());
                // boolVarArray(Data.nD + Data.nT);

                Path path = paths.get(i);
                tailCoverExprs[path.getTail().getIndex()].addTerm(y[i], 1.0);

                ArrayList<Leg> pathLegs = path.getLegs();
                ArrayList<Integer> delayTimes = path.getDelayTimesInMin();

                for (int j = 0; j < pathLegs.size(); ++j) {
                    Leg pathLeg = pathLegs.get(j);
                    legCoverExprs[pathLeg.getIndex()].addTerm(y[i], 1.0);

                    final Integer delayTime = delayTimes.get(j);
                    if (delayTime > 0)
                        delayExprs[pathLeg.getIndex()].addTerm(y[i], delayTime);
                }
            }

            // Add constraints to model.
            for (int i = 0; i < numTails; ++i)
                R2[i] = subCplex.addLe(tailCoverExprs[i], 1.0, "tail_" + i + "_" + tails.get(i).getId());

            for (int i = 0; i < numLegs; ++i) {
                R1[i] = subCplex.addEq(legCoverExprs[i], 1.0, "leg_" + i + "_" + legs.get(i).getId());
                R3[i] = subCplex.addLe(delayExprs[i], delayRHS[i], "delay_" + i + "_" + legs.get(i).getId());
            }

            subCplex.exportModel("sub.lp");

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

    private HashMap<Integer, Integer> getLegDelays(ArrayList<Tail> tails, ArrayList<Leg> legs,
                                                   ArrayList<Integer> durations, double[][] xValues) {
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

    public void solve() {
        try {
//			Master.mastCplex.addMaximize();
            subCplex.solve();
            objValue = subCplex.getObjValue();
            logger.debug("Objective value: " + objValue);
            yValues = new double[paths.size()];
            yValues = subCplex.getValues(y);

            duals1 = subCplex.getDuals(R1);
            duals2 = subCplex.getDuals(R2);
            duals3 = subCplex.getDuals(R3);
        } catch (IloException e) {
            e.printStackTrace();
            System.out.println("Error: SubSolve");
        }
    }

    public void writeLPFile(String fName, int iter)
    {
        try
        {
            subCplex.exportModel(fName + "sub" + iter + ".lp");
        } catch (IloException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.out.println("Error: GetLPFile-Sub");
        }
    }

    public void end() {
        y = null;
        d = null;
        R1 = null;
        R2 = null;
        R3 = null;

        duals = null;
        duals1 = null;
        duals2 = null;
        duals3 = null;
        subCplex.end();
    }

    public double[] getDuals() {
        return duals;
    }


    public void setDuals(double[] duals) {
        this.duals = duals;
    }

	public double[] getDuals1() {
		return duals1;
	}


	public void setDuals1(double[] duals1) {
		this.duals1 = duals1;
	}


	public double[] getDuals2() {
		return duals2;
	}


	public void setDuals2(double[] duals2) {
		this.duals2 = duals2;
	}


	public double[] getDuals3() {
		return duals3;
	}


	public void setDuals3(double[] duals3) {
		this.duals3 = duals3;
	}

    public double getObjValue() {
        return objValue;
    }

    public void setObjValue(double objValue) {
        this.objValue = objValue;
    }
}
