package com.stochastic.solver;

import com.stochastic.Main;
import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.utility.OptException;
import ilog.concert.*;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SubSolverWrapper {
    /**
     * Class that is used to solve a set packing model with CPLEX using a given
     * set of paths.
     */
    private final static Logger logger = LogManager.getLogger(SubSolverWrapper.class);   
	private static double alphaValue;
	private static double[][] betaValue;
	private static int numThreads = 2;
	
    private static ArrayList<Path> paths;
    private static ArrayList<Leg> legs;
    private static ArrayList<Tail> tails;
    private static ArrayList<Integer> durations;
    private static Integer numScenarios;
	
	private static double[][]  xValues; 	
	private static double uBound;
	private static double prb;
	
    public static void SubSolverWrapperInit(ArrayList<Path> paths, ArrayList<Leg> legs, ArrayList<Tail> tails,
											ArrayList<Integer> durations, double[][] xVal,
											Integer numScenarios) throws OptException
    {
        try 
        {   	
        	SubSolverWrapper.paths = paths;
        	SubSolverWrapper.legs = legs;
        	SubSolverWrapper.tails = tails;
        	SubSolverWrapper.durations = durations;
        	SubSolverWrapper.xValues = xVal;
        	SubSolverWrapper.numScenarios = numScenarios;
        	
        	alphaValue = 0;
        	uBound     = 0;
        	betaValue  = new double[durations.size()][legs.size()];
    	    prb = 1/Main.nSce;        	
        
	    } catch (Exception e) {
	        logger.error(e.getStackTrace());
	        throw new OptException("error at SubSolverWrapperInit");
	    }	        
    }
	
    	
	public synchronized static void calculateAlpha(double[] duals, double prb)
	{
	  try 
		{
			for(int j=0; j < legs.size(); j++)				
				alphaValue = (duals[j]*-legs.get(j).getDepTimeInMins()*prb);
			
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public synchronized static void calculateBeta(double[] duals, double prb) throws IloException
	{
		
		for(int i=0; i < durations.size(); i++)		
			for(int j=0; j < legs.size(); j++)		
				betaValue[i][j] = duals[j]*xValues[i][j]*prb;				
	}
	
	
    public  void buildSubModel()
    {
       try
       {
            ExecutorService exSrv = Executors.newFixedThreadPool(numThreads);
           
            for(int i=0; i< numScenarios; i++)
            {
                Thread.currentThread().sleep(500);

                BuildSubModelThr buildSDThrObj = new BuildSubModelThr();
                buildSDThrObj.setScerNo(i);
                exSrv.execute(buildSDThrObj);           // this calls run() method below            	
            }            	
            
            exSrv.shutdown();
            try
            {
                while (!exSrv.isTerminated()) {
                    Thread.currentThread().sleep(100);
                }
            }
            catch (InterruptedException inEx)
            {
    	        logger.error(inEx.getStackTrace());
    	        throw new OptException("error at buildSubModel");
            }
       }
       catch(Exception e) {
        e.printStackTrace();
      }

    }  
	
	
    class BuildSubModelThr implements Runnable
    {
       public int scerNo;       
       
       public int getScerNo() {
		return scerNo;
       }
       
       public void setScerNo(int scerNo) {
		this.scerNo = scerNo;
       }

	//exSrv.execute(buildSDThrObj) calls brings you here
       public void run()
       {
	       try
	       {	    	
	    	   // beta x + theta >= alpha - BD cut

	    	   SubSolver s = new SubSolver();
	    	   s.SubSolverInit(paths, legs, tails, durations);
	    	   s.constructSecondStage(prb, xValues);
	    	   s.solve();
	    	   uBound += (prb*s.getObjValue());
	    	   calculateAlpha(s.getDuals(), prb);
	    	   calculateBeta(s.getDuals(), prb);
	    	   s.end();	    	   
	       }
	       catch(Exception e) {
	    	 e.printStackTrace();  
   	         logger.error(e.getStackTrace());
   	         try {
				throw new OptException("error at BuildSubModelThr");
			} catch (OptException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}	    	   
     	     System.exit(17);
	       }
       }
    }

	public static double getuBound() {
		return uBound;
	}

	public static void setuBound(double uBound) {
		SubSolverWrapper.uBound = uBound;
	}


	public static double getAlphaValue() {
		return alphaValue;
	}


	public static void setAlphaValue(double alphaValue) {
		SubSolverWrapper.alphaValue = alphaValue;
	}

	public static double[][] getBetaValue() {
		return betaValue;
	}

	public static void setBetaValue(double[][] betaValue) {
		SubSolverWrapper.betaValue = betaValue;
	}	
    
}
