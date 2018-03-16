package com.stochastic.solver;

import com.stochastic.controller.Controller;
import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
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

public class DepSolver {
    /**
     * Class that is used to solve a set packing model with CPLEX using a given
     * set of paths.
     */
    private final Logger logger = LogManager.getLogger(DepSolver.class);
    private final double eps = 1.0e-5;
    private ArrayList<Path> paths; // subproblem columns
    ArrayList<Tail> tails;
    ArrayList<Leg> legs;
    ArrayList<Integer> durations;
//    int[][] sceVal;
    
    // cplex variables
    private IloIntVar[][] x;    
    private IloNumVar[][] y; // y[i] = 1 if path i is selected, 0 else.
    private IloNumVar[][] d; // d[i] >= 0 is the total delay of leg i.
    private IloCplex subCplex;

    private double objValue;

    // private static IloNumVar neta; // = cplex.numVar(-Double.POSITIVE_INFINITY, 0, "neta");
    public DepSolver() {}
    
    public void printAllPaths()
    {
    	for(Path p: paths)
    	{
    		System.out.println("Tail: " + p.getTail().getId());
    		
        	for(Leg l: p.getLegs())    		
        		System.out.println("Leg: " + l.getId());        		
    	}
    }

    public void constructModel(DataRegistry dataRegistry) throws OptException {
        try {
            tails = dataRegistry.getTails();
            legs = dataRegistry.getLegs();
            durations = dataRegistry.getDurations();

            double[][] xValues = new double[legs.size()][durations.size()];
            
            // get delay data using planned delays from first stage and random delays from second stage.
            HashMap<Integer, Integer> legDelayMap = getLegDelays(tails, legs, durations, xValues);
            Network network = new Network(tails, legs, legDelayMap, dataRegistry.getWindowStart(),
                    dataRegistry.getWindowEnd(), dataRegistry.getMaxLegDelayInMin());

            // Later, the full enumeration algorithm in enumerateAllPaths() will be replaced a labeling algorithm.
             paths = network.enumerateAllPaths();
            //
            
//            PathEnumerator pe = new PathEnumerator(); 
//            paths = pe.addPaths(dataRegistry);            
            	
    		System.out.println("Tail: " + tails.size() + " legs: " + legs.size() + " durations: " + durations.size());    		
            printAllPaths();
            
            // Create containers to build CPLEX model.
            subCplex = new IloCplex();
            final Integer numLegs = legs.size();
            final Integer numTails = tails.size();
            final Integer numDurations = durations.size();
            final Integer maxDelay = dataRegistry.getMaxLegDelayInMin();
            
            x = new IloIntVar[numLegs][durations.size()];            

            for (int i = 0; i < legs.size(); i++)            
            	for (int j = 0; j < durations.size(); j++)
                    x[i][j] = subCplex.boolVar("X_" + i + "_" + legs.get(i).getId()); // boolVarArray(Data.nD + Data.nT);

            IloRange r;
            IloLinearNumExpr cons;
            for (int i = 0; i < legs.size(); i++) {
                cons = subCplex.linearNumExpr();

                for (int j = 0; j < durations.size(); j++)
                    cons.addTerm(x[i][j], 1);

                r = subCplex.addLe(cons, 1);
                r.setName("Cons_" + legs.get(i).getId());
            }
            
            y = new IloNumVar[paths.size()][5];
            d = new IloNumVar[numLegs][5];

            IloLinearNumExpr[][] legCoverExprs = new IloLinearNumExpr[numLegs][5];
            IloLinearNumExpr[][] tailCoverExprs = new IloLinearNumExpr[numTails][5];
            boolean[][] legPresence = new boolean[numLegs][5];
            boolean[][] tailPresence = new boolean[numLegs][5];
            
            for (int j = 0; j < 5; j++)            
            	for (int i = 0; i < numTails; ++i)
            		tailCoverExprs[i][j] = subCplex.linearNumExpr();

            IloLinearNumExpr[][] delayExprs = new IloLinearNumExpr[numLegs][5];
            double[][] delayRHS = new double[numLegs][5];

            // Build objective, initialize leg coverage and delay constraints.
            IloLinearNumExpr objExpr = subCplex.linearNumExpr();
            for (int j = 0; j < 5; j++)             
	            for (int i = 0; i < numLegs; i++) {
	                d[i][j] = subCplex.numVar(0, Double.MAX_VALUE, "d_" + legs.get(i).getId() + "_" + j);
	                // boolVarArray(Data.nD + Data.nT);
	
	                delayRHS[i][j] = 14.0; // OTP time limit
	
	                objExpr.addTerm(d[i][j], 0.20);
	                legCoverExprs[i][j] = subCplex.linearNumExpr();
	                delayExprs[i][j] = subCplex.linearNumExpr();
	                delayExprs[i][j].addTerm(d[i][j], -1.0);
	                
	                for (int j1 = 0; j1 < numDurations; ++j1)
	                    delayExprs[i][j].addTerm(x[i][j1], -durations.get(j1));                
	            }            

            // first-stage obj function
            for (int i = 0; i < legs.size(); i++)
            	for (int j = 0; j < durations.size(); j++)
                	objExpr.addTerm(x[i][j], durations.get(j));          
            
            subCplex.addMinimize(objExpr);
            objExpr.clear();

            // Create path variables and add them to constraints.
            for (int j = 0; j < 5; j++)            
            for (int i = 0; i < paths.size(); ++i) {
                y[i][j] = subCplex.numVar(0, 1, "y_" + paths.get(i).getIndex() + "_" + j);
                // boolVarArray(Data.nD + Data.nT);

                Path path = paths.get(i);
                tailCoverExprs[path.getTail().getIndex()][j].addTerm(y[i][j], 1.0);
                tailPresence[path.getTail().getIndex()][j] = true;
                
                ArrayList<Leg> pathLegs = path.getLegs();
                ArrayList<Integer> delayTimes = path.getDelayTimesInMin();

                for (int j1 = 0; j1 < pathLegs.size(); ++j1) {
                    Leg pathLeg = pathLegs.get(j1);
                    legCoverExprs[pathLeg.getIndex()][j].addTerm(y[i][j], 1.0);
                    legPresence[pathLeg.getIndex()][j] = true;

//                    delayExprs[pathLeg.getIndex()][j].addTerm(y[i][j], Controller.sceVal[i][j]);
                    
                    final Integer delayTime = delayTimes.get(j);
                    if (delayTime > 0)
                    {
                        delayExprs[pathLeg.getIndex()][j].addTerm(y[i][j], delayTime);
                        System.out.println(" DEP-Leg: " + pathLeg.getId() + " delayTime: " + delayTime);                        
                    }

//                        delayExprs[pathLeg.getIndex()].addTerm(y[i], 20);//delayTime);                   
                    
                }                
            }

            // Add constraints to model.
            for (int j = 0; j < 5; j++)            
            	for (int i = 0; i < numTails; ++i)
            		if(tailPresence[i][j])
            			subCplex.addLe(tailCoverExprs[i][j], 1.0, "tail_" + i + "_" + tails.get(i).getId() + "_" + j);

            for (int j = 0; j < 5; j++)
	            for (int i = 0; i < numLegs; ++i) {
	            	
	            	if(legPresence[i][j])
	            		subCplex.addEq(legCoverExprs[i][j], 1.0, "leg_" + i + "_" + legs.get(i).getId() + "_" + j);
	                
	            	subCplex.addLe(delayExprs[i][j], delayRHS[i][j], "delay_" + i + "_" + legs.get(i).getId() + "_" + j);
	            }

            subCplex.exportModel("dep.lp");

            // Clear all constraint expressions to avoid memory leaks.
            for (int j = 0; j < 5; j++)            
            for (int i = 0; i < numLegs; ++i) {
                legCoverExprs[i][j].clear();
                delayExprs[i][j].clear();
            }
            
            for (int j = 0; j < 5; j++)            
            for (int i = 0; i < numTails; ++i)
                tailCoverExprs[i][j].clear();
            
        } catch (IloException e) {
            logger.error(e.getStackTrace());
            throw new OptException("CPLEX error solving first stage MIP");
        }
    }

    
    private HashMap<Integer, Integer> getLegDelays(ArrayList<Tail> tails, ArrayList<Leg> legs,
            ArrayList<Integer> durations, double[][] xValues) {
        // Generate random delays using a delay generator.
        DelayGenerator dgen = new FirstFlightDelayGenerator(tails, 20);
        HashMap<Integer, Integer> randomDelays = dgen.generateDelays();

        // Collect planned delays from first stage solution.
        HashMap<Integer, Integer> plannedDelays = new HashMap<>();

        for (int i = 0; i < durations.size(); ++i) {
            for (int j = 0; j < legs.size(); ++j) {
                if (xValues[j][i] >= eps)
                    plannedDelays.put(legs.get(j).getIndex(), durations.get(i));
            }
        }

        // Combine the delay maps into a single one.
        HashMap<Integer, Integer> combinedDelayMap = new HashMap<>();
        for (Leg leg : legs) {
            int delayTime = 0;
            boolean updated = false;

            if (randomDelays.containsKey(leg.getIndex())) {
                delayTime = randomDelays.get(leg.getIndex());
                updated = true;
            }

            if (plannedDelays.containsKey(leg.getIndex())) {
                delayTime = Math.max(delayTime, plannedDelays.get(leg.getIndex()));
                updated = true;
            }

            if (updated)
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

             // solution value
            double[][] dValues =  new double[legs.size()][5]; // d[i] >= 0 is the total delay of leg i.
            double[][] xValues = new double[legs.size()][durations.size()];
            double[][] yValues = new double[paths.size()][5];
            
            for (int j = 0; j < paths.size(); j++)            
            	yValues[j] = subCplex.getValues(y[j]);

        	for (int j = 0; j < paths.size(); j++)
        		dValues[j] = subCplex.getValues(d[j]);
            
            for(int i=0; i< legs.size(); i++)
            	xValues[i] = subCplex.getValues(x[i]);
            
            for(int i=0; i< legs.size(); i++)
                for(int j=0; j< durations.size(); j++)            	
                	if(xValues[i][j] > 0)
                		System.out.println(" i: " + i + " j: " + j + " xValues[i][j]: " + xValues[i][j] + " , " + durations.get(j));
            
            for(int p=0; p< paths.size(); p++)
            	for (int j = 0; j < 5; j++)            	
            	if(yValues[p][j] > 0)            	
            		System.out.println(" p: " + p + " yValues[p]: " + yValues[p][j]);

            for(int p=0; p< legs.size(); p++)
            	for (int j = 0; j < 5; j++)            	
            		if(dValues[p][j] > 0)            	
            			System.out.println(" l: " + p + " dValues[p]: " + dValues[p][j]);
            
//            duals1 = subCplex.getDuals(R1);
//            duals2 = subCplex.getDuals(R2);
//            duals3 = subCplex.getDuals(R3);
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
        
        subCplex.end();
    }

    public double getObjValue() {
        return objValue;
    }

    public void setObjValue(double objValue) {
        this.objValue = objValue;
    }
}
