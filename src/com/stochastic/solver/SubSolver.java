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

public class SubSolver {
    /**
     * Class that is used to solve a set packing model with CPLEX using a given
     * set of paths.
     */
    private final Logger logger = LogManager.getLogger(SubSolver.class);
    private final double eps = 1.0e-5;
    private ArrayList<Path> paths;
    private ArrayList<Leg> legs;
    private ArrayList<Tail> tails;
    private ArrayList<Integer> durations;  
    
    // cplex variables
    private IloNumVar[] Y;
    private IloNumVar[] D;    
    private IloCplex subCplex;
    private IloObjective obj;
    private IloRange[] R;    
    
    private double   objValue;
    private double[] duals;    
   
	private int[][]     indices;
	private double[][]  values;
	private double[]  yValues; 
    
//    private static IloNumVar neta; // = cplex.numVar(-Double.POSITIVE_INFINITY, 0, "neta");    
    
    public void SubSolverInit(ArrayList<Path> paths, ArrayList<Leg> legs, ArrayList<Tail> tails, ArrayList<Integer> durations) throws OptException {

        try 
        {   	
        	this.paths = paths;
        	this.legs = legs;
        	this.tails = tails;
        	this.durations = durations;
	        
	        subCplex =  new IloCplex();
	        Y = new IloNumVar[paths.size()];
	        D = new IloNumVar[legs.size()];
	        R = new IloRange[legs.size()];	        
	        
	    } catch (IloException e) {
	        logger.error(e.getStackTrace());
	        throw new OptException("CPLEX error while initiating sub-problem");
	    }	        
    }

	
	public void solve()
	{
		try 
		{	
//			Master.mastCplex.addMaximize();
			subCplex.solve();
			objValue = subCplex.getObjValue();
			
			yValues = new double[paths.size()];			
			yValues = subCplex.getValues(Y);
			
			duals = subCplex.getDuals(R);			
								
		} catch (IloException e) {
			// TODO Auto-generated catch block
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
	
    
    public void constructSecondStage(double prb, double[][] xValues) throws OptException
    {
        try 
        {        
        	for (int i = 0; i < paths.size(); i++)
   				Y[i] = subCplex.numVar(0,1,"Y_" + i + "_" + paths.get(i).getId()); // boolVarArray(Data.nD + Data.nT);     	

        	for (int i = 0; i < legs.size(); i++)
   				D[i] = subCplex.numVar(0,1000,"D_" + i + "_" + legs.get(i).getId()); // boolVarArray(Data.nD + Data.nT);     	
        	
        	IloRange r;
        	IloLinearNumExpr cons;
    		for (int i = 0; i < legs.size(); i++)
    		{
            	cons = subCplex.linearNumExpr();    			
    			
            	for (int j = 0; j < paths.size(); j++)
            		if(paths.get(j).getLegs().contains(legs.get(i)))
            			cons.addTerm(Y[j],1);
            	
        		r = subCplex.addEq(cons, 1);
        		r.setName("Cons1_" + legs.get(i).getId());
    		}   		
    		
        	cons = subCplex.linearNumExpr();
    		for (int i = 0; i < tails.size(); i++)
    		{
            	cons = subCplex.linearNumExpr();    			
    			
            	for (int j = 0; j < paths.size(); j++)
            		if(paths.get(j).getTail().equals(tails.get(i)))
            			cons.addTerm(Y[j],1);
            	
        		r = subCplex.addLe(cons, 1);
        		r.setName("Cons2_" + legs.get(i).getId());
    		}    		
    		   		
        	cons = subCplex.linearNumExpr();        	
    		for (int i = 0; i < legs.size(); i++)
    		{
            	cons = subCplex.linearNumExpr();    			
    			
    			cons.addTerm(D[i],1);
    			
    			System.out.println(" paths-size(): " + paths.size());
    			
            	for (int j = 0; j < paths.size(); j++)
            		if(paths.get(j).getLegs().contains(legs.get(i)))
            		{
            			int index = paths.get(j).getLegs().indexOf(legs.get(i));
            			int depTimeInMins = legs.get(i).getDepTimeInMins() + paths.get(j).getDelayTimesInMin().get(index);
            			cons.addTerm(Y[j],-depTimeInMins);            			
            		}

            	
            	double xVal = 0;
            	
        		for (int j = 0; j < durations.size(); j++)
        			xVal += xValues[j][i];
            	
        		R[i] = subCplex.addGe(cons, -legs.get(i).getDepTimeInMins() -xVal);
        		R[i].setName("Cons1_" + legs.get(i).getId());
    		}   		
        	
    		obj = subCplex.addMinimize(cons);     		
        	
	    } catch (IloException e) {
	        logger.error(e.getStackTrace());
	        throw new OptException("CPLEX error solving first stage MIP");
	    }        
    }
    
    
	public void solve(boolean isMIP)
	{
		try
		{
			subCplex.setParam(IloCplex.IntParam.RootAlg,
                    IloCplex.Algorithm.Dual);
			subCplex.setParam(IloCplex.BooleanParam.PreInd, false);

			subCplex.solve();
			objValue   = subCplex.getObjValue();

			if(!isMIP)
				duals = subCplex.getDuals(R);			

		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("Error: MasterSolve");
		}
	}
	
	public void end()
	{
	    Y = null;
	    D = null;    

	    obj = null;
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
