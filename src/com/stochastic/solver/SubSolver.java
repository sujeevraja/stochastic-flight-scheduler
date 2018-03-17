package com.stochastic.solver;

import com.stochastic.controller.Controller;
import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.TestDelayGenerator;
import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Network;
import com.stochastic.network.Path;
import com.stochastic.network.PathEnumerator;
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
    private double probability;
    private final double eps = 1.0e-5;
    private ArrayList<Path> paths; // subproblem columns

    // cplex variables
    private IloNumVar[] y; // y[i] = 1 if path i is selected, 0 else.
    private IloNumVar[] d; // d[i] >= 0 is the total delay of leg i.
    private IloNumVar   v; // d[i] >= 0 is the total delay of leg i.    
    private IloCplex subCplex;

    private double objValue;

    private double[] dualsLeg;
    private double[] dualsTail;
    private double[] dualsDelay;
    private double[] dualsBnd;
    private double   dualsRisk;
    
    private int[][] indices;
    private double[][] values;
    private double[] yValues;
    
    private IloObjective obj;
    private IloRange[] R1;
    private IloRange[] R2;
    private IloRange[] R3;
    private IloRange[] R4;
    private IloRange R5;

    private boolean[] legPresence;
    private boolean[] tailPresence;
    
    private int sceNo;
    
    
    // private static IloNumVar neta; // = cplex.numVar(-Double.POSITIVE_INFINITY, 0, "neta");
    public SubSolver(HashMap<Integer, Integer> randomDelays, double probability, int sNo) {
        this.randomDelays = randomDelays;
        this.probability = probability;
        this.sceNo       = sNo;
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
            // paths = network.enumerateAllPaths();
            
            PathEnumerator pe = new PathEnumerator();
            paths = pe.addPaths(dataRegistry);
            
    		// logger.debug("Tail: " + tails.size() + " legs: " + legs.size() + " durations: " + durations.size());
            // printAllPaths();

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
            R4 = new IloRange[paths.size()]; // delay constraints
            dualsLeg =  new double[legs.size()];
            dualsTail =  new double[tails.size()];
            dualsDelay =  new double[legs.size()];
            dualsBnd =  new double[paths.size()];

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
                d[i] = subCplex.numVar(0, Double.MAX_VALUE, "d_" + legs.get(i).getId());
                // boolVarArray(Data.nD + Data.nT);

                delayRHS[i] = 14.0; // OTP time limit

                objExpr.addTerm(d[i], probability*1.5);
                legCoverExprs[i] = subCplex.linearNumExpr();
                delayExprs[i] = subCplex.linearNumExpr();
                delayExprs[i].addTerm(d[i], -1.0);

                for (int j = 0; j < numDurations; ++j)
                    if (xValues[j][i] >= eps) {
                        delayRHS[i] += durations.get(j);
                        break;
                    }
            }
            
            if(Controller.expExcess)
            {
                v = subCplex.numVar(0, Double.MAX_VALUE, "v");            	
            	objExpr.addTerm(v, probability*Controller.rho);            	
            }

            	
            subCplex.addMinimize(objExpr);
            objExpr.clear();

            // Create path variables and add them to constraints.
            for (int i = 0; i < paths.size(); ++i) {
                y[i] = subCplex.numVar(0, 1, "y_" + paths.get(i).getIndex());
                
                Path path = paths.get(i);
                tailCoverExprs[path.getTail().getIndex()].addTerm(y[i], 1.0);
                tailPresence[path.getTail().getIndex()] = true;
                
                ArrayList<Leg> pathLegs = path.getLegs();
                ArrayList<Integer> delayTimes = path.getDelayTimesInMin();

                for (int j = 0; j < pathLegs.size(); ++j) {
                    Leg pathLeg = pathLegs.get(j);
                    legCoverExprs[pathLeg.getIndex()].addTerm(y[i], 1.0);
                    legPresence[pathLeg.getIndex()] = true;

                    delayExprs[pathLeg.getIndex()].addTerm(y[i], Controller.sceVal[i][sceNo]);

                    // final Integer delayTime = delayTimes.get(j);
                    // if (delayTime > 0) {
                    //     delayExprs[pathLeg.getIndex()].addTerm(y[i], delayTime);
                    //     // logger.debug(" Sub-Leg: " + pathLeg.getId() + " delayTime: " + delayTime);
                    // }

                    // delayExprs[pathLeg.getIndex()].addTerm(y[i], 20);//delayTime);
                }
            }

            // Add constraints to model.
            for (int i = 0; i < numTails; ++i)
            	if(tailPresence[i])            	
            		R2[i] = subCplex.addLe(tailCoverExprs[i], 1.0, "tail_" + i + "_" + tails.get(i).getId());

            for (int i = 0; i < numLegs; ++i) {
            	
            	if(legPresence[i])            	
            		R1[i] = subCplex.addEq(legCoverExprs[i], 1.0, "leg_" + i + "_" + legs.get(i).getId());
                
            	R3[i] = subCplex.addLe(delayExprs[i], delayRHS[i], "delay_" + i + "_" + legs.get(i).getId());
            }

            for (int i = 0; i < paths.size(); ++i) 
                R4[i] = subCplex.addLe(y[i], 1, "yBound_" + i);
            
            if(Controller.expExcess)
            {
                v = subCplex.numVar(0, Double.MAX_VALUE, "v");
                
                double xVal = 0;
                for (int i = 0; i < legs.size(); i++)
                	for (int j = 0; j < durations.size(); j++)
                		xVal += (xValues[j][i]*durations.get(j));          
                
                IloLinearNumExpr riskExpr = subCplex.linearNumExpr();
                
                for (int i = 0; i < numLegs; i++)
                    riskExpr.addTerm(d[i], 1.5);
                
                riskExpr.addTerm(v, -1);                
                
            	R5 = subCplex.addLe(riskExpr, Controller.excessTgt-xVal,"risk");                
                
            }
            
            // subCplex.exportModel("sub.lp");

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

    public void solve() throws OptException {
        try {
//			Master.mastCplex.addMaximize();
        	
			subCplex.setParam(IloCplex.IntParam.RootAlg,
                    IloCplex.Algorithm.Dual);
			subCplex.setParam(IloCplex.BooleanParam.PreInd, false);
        	
            subCplex.solve();
            objValue = subCplex.getObjValue();
            logger.debug("subproblem objective value: " + objValue);
            yValues = new double[paths.size()];
            yValues = subCplex.getValues(y);

            for(int i=0; i<R1.length; i++)
                if(legPresence[i])
                    dualsLeg[i] = subCplex.getDual(R1[i]);

            for(int i=0; i<R2.length; i++)
                if(tailPresence[i])
                    dualsTail[i] = subCplex.getDual(R2[i]);

//            duals2 = subCplex.getDuals(R2);
            dualsDelay = subCplex.getDuals(R3);
            dualsBnd   = subCplex.getDuals(R4);

            if(Controller.expExcess)
                dualsRisk  = subCplex.getDual(R5);

//            add duals and djust the cuts ////

        } catch (IloException e) {
            e.printStackTrace();
            System.out.println("Error: SubSolve");
        }
    }

    public void writeLPFile(String fName, int iter, int sceNo) throws OptException {
        try {
            subCplex.exportModel(fName + "sub_" + iter + "_" + sceNo + ".lp");
        } catch (IloException e) {
            logger.error(e);
            throw new OptException("error writing lp file for subproblem");
        }
    }

    public void end() {
        y = null;
        d = null;
        R1 = null;
        R2 = null;
        R3 = null;
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

	public void setDuals1(double[] duals1) {
		this.dualsLeg = duals1;
	}

	public double[] getDualsTail() {
		return dualsTail;
	}

	public void setDuals2(double[] duals2) {
		this.dualsTail = duals2;
	}

	public double[] getDualsDelay() {
		return dualsDelay;
	}

	public void setDuals3(double[] duals3) {
		this.dualsDelay = duals3;
	}
	
    public double[] getDualsBnd() {
		return dualsBnd;
	}

	public void setDuals4(double[] duals4) {
		this.dualsBnd = duals4;
	}	

	public double getDualsRisk() {
		return dualsRisk;
	}

	public void setDualsRisk(double dualsRisk) {
		this.dualsRisk = dualsRisk;
	}

	public double getObjValue() {
        return objValue;
    }

    public void setObjValue(double objValue) {
        this.objValue = objValue;
    }
}
