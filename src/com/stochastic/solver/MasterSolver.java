package com.stochastic.solver;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.utility.CostUtility;
import com.stochastic.utility.OptException;
import ilog.concert.*;
import ilog.cplex.IloCplex;
import java.util.ArrayList;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class MasterSolver {
    /**
     * Class that is used to solve a set packing model with CPLEX using a given
     * set of paths.
     */
    private final static Logger logger = LogManager.getLogger(MasterSolver.class);
    private final static double eps = 1.0e-5;
    private static ArrayList<Path> paths;
    private static ArrayList<Leg> legs;
    private static ArrayList<Tail> tails;
    private static ArrayList<Integer> durations;  
    
    // cplex variables
    private static IloIntVar[][] X;
    private static IloCplex masterCplex;
	private static IloLPMatrix  mastLp;    
    private static IloNumVar thetaVar;
    private static IloObjective obj;
    
    private static double objValue;    
   
	private static int[][]     indices;
	private static double[][]  values;
	
	private static double[][]  xValues;	
    
//    private static IloNumVar neta; // = cplex.numVar(-Double.POSITIVE_INFINITY, 0, "neta");    
    
    public static void MasterSolverInit(ArrayList<Path> paths, ArrayList<Leg> legs, ArrayList<Tail> tails, ArrayList<Integer> durations) throws OptException {

        try 
        {   	
        	MasterSolver.paths = paths;
        	MasterSolver.legs = legs;
        	MasterSolver.tails = tails;
        	MasterSolver.durations = durations;
	        
	        masterCplex =  new IloCplex();
	        X = new IloIntVar[MasterSolver.durations.size()][MasterSolver.legs.size()];
	        
	    } catch (IloException e) {
	        logger.error(e.getStackTrace());
	        throw new OptException("CPLEX error while adding benders variable");
	    }	        
    }
    
    
	public static void addColumn() throws OptException
	{
        try 
        {		
        	masterCplex.setLinearCoef(obj, thetaVar, 1);
        	
        	/*
        	IloLinearNumExpr objExpr =  (IloLinearNumExpr) masterCplex.getObjective().getExpr();	
	        thetaVar = masterCplex.numVar(-Double.MAX_VALUE, Double.MAX_VALUE,"theta");
	        objExpr.addTerm(thetaVar, 1);

	        masterCplex.addMinimize(objExpr);
	        masterCplex.add(thetaVar);
	        */        
	        
	    } catch (IloException e) {
	        logger.error(e);
	        throw new OptException("CPLEX error while adding benders variable");
	    }
	        
//        masterCplex.addColumn(thetaVar);
	}
	
	
	public static void solve()
	{
		try 
		{	
//			Master.mastCplex.addMaximize();
			MasterSolver.masterCplex.solve();
			MasterSolver.objValue = masterCplex.getObjValue();
			
			MasterSolver.xValues = new double[durations.size()][legs.size()];	 		 
		 
			for (int i = 0; i < durations.size(); i++)   			
				MasterSolver.xValues[i] = MasterSolver.masterCplex.getValues(X[i]);
								
//			MasterSolver.xValues =  Master.mastCplex.getValues(Master.mastLp, 0, Master.mastCplex.getNcols(), IloCplex.IncumbentId);
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("Error: MasterSolve");			
		}		
	}	
	
	public static void writeLPFile(String fName, int iter)
	{
	  try 
		{
			masterCplex.exportModel(fName + "mast" + iter + ".lp");
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("Error: GetLPFile");			
		}
	}
	
    
    public static void constructFirstStage() throws OptException
    {
        try 
        {        
        	for (int i = 0; i < durations.size(); i++)
    			for (int j = 0; j < legs.size(); j++)
    				X[i][j] = masterCplex.boolVar("X_" + i + "_" + legs.get(j).getId()); // boolVarArray(Data.nD + Data.nT);        	
     	
	        thetaVar = masterCplex.numVar(-Double.MAX_VALUE, Double.MAX_VALUE,"theta");
	        
        	IloRange r;
        	IloLinearNumExpr cons;
    		for (int i = 0; i < legs.size(); i++)
    		{
            	cons = masterCplex.linearNumExpr();    			
    			
            	for (int j = 0; j < durations.size(); j++)
    				cons.addTerm(X[j][i],1);
            	
        		r = masterCplex.addLe(cons, 1);
        		r.setName("Cons_" + legs.get(i).getId());
    		}   		
    		
        	cons = masterCplex.linearNumExpr();  
        	
        	for (int i = 0; i < durations.size(); i++)
    			for (int j = 0; j < legs.size(); j++)
    	    		cons.addTerm(X[i][j], 1);
        	
    		cons.addTerm(thetaVar, 0);    		
    		obj = masterCplex.addMinimize(cons);        	
        	
	    } catch (IloException e) {
	        logger.error(e.getStackTrace());
	        throw new OptException("CPLEX error solving first stage MIP");
	    }        
    }
    
    public static void constructBendersCut(double alphaValue, double[][] betaValue) throws OptException 
    {    	
        try 
        {    	
	    	IloLinearNumExpr cons = masterCplex.linearNumExpr();
	    	
	    	for (int i = 0; i < durations.size(); i++)
				for (int j = 0; j < legs.size(); j++)
		    		cons.addTerm(X[i][j], betaValue[i][j]);
	
    		cons.addTerm(thetaVar, 1);
    		
	    	IloRange r = masterCplex.addGe(cons, alphaValue);
    	
	    } catch (IloException e) {
	        logger.error(e.getStackTrace());
	        throw new OptException("CPLEX error solving first stage MIP");
	    }        
    	
    }

	public static double getObjValue() {
		return objValue;
	}

	public static void setObjValue(double objValue) {
		MasterSolver.objValue = objValue;
	}

	public static double[][] getxValues() {
		return xValues;
	}

	public static void setxValues(double[][] xValues) {
		MasterSolver.xValues = xValues;
	}    
    
    
}
