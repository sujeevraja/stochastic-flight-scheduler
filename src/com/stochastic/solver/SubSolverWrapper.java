package com.stochastic.solver;

import com.stochastic.controller.Controller;
import com.stochastic.delay.DelayGenerator;
import com.stochastic.delay.FirstFlightDelayGenerator;
import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.registry.DataRegistry;
import com.stochastic.utility.OptException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SubSolverWrapper {
    /**
     * Wrapper class that can be used to solve the second-stage problems in parallel.
     */
    private final static Logger logger = LogManager.getLogger(SubSolverWrapper.class);
    private static DataRegistry dataRegistry;
    private static double alpha;
    private static int iter;
    private static double[][] beta;
    private static int numThreads = 2;
    private static HashMap<Integer, HashMap<Integer, ArrayList<Path>>> hmPaths = new HashMap<Integer, HashMap<Integer, ArrayList<Path>>>(); 
     
    
    private static double[][] xValues;
    private static double uBound;
        
    public static void SubSolverWrapperInit(DataRegistry dataRegistry, double[][] xValues, int iter)
            throws OptException {
        try {
            SubSolverWrapper.dataRegistry = dataRegistry;
            SubSolverWrapper.xValues = xValues;
            SubSolverWrapper.iter = iter;

            alpha = 0;
            uBound = MasterSolver.getFSObjValue();
            beta = new double[dataRegistry.getDurations().size()][dataRegistry.getLegs().size()];
        } catch (Exception e) {
            logger.error(e.getStackTrace());
            throw new OptException("error at SubSolverWrapperInit");
        }
    }

    private synchronized static void calculateAlpha(double[] dualsLegs, double[] dualsTail, double[] dualsDelay, double[][] dualsBnd, double dualRisk) {
        ArrayList<Leg> legs = dataRegistry.getLegs();
        
        logger.debug("initial alpha value: " + alpha);
        
        for (int j = 0; j < legs.size(); j++)
        {
//            System.out.println(" j: " + j + " duals1[j]: " + duals1[j]); // + " prb: " + prb);
            alpha += (dualsLegs[j]); //*prb);            
        }

        for (int j = 0; j < dataRegistry.getTails().size(); j++)
        {
//            System.out.println(" j: " + j + " duals2[j]: " + duals2[j]); // + " prb: " + prb);        	
            alpha += (dualsTail[j]); //*prb);        	
        }

        for (int j = 0; j < legs.size(); j++)
        {
//            System.out.println(" j: " + j + " duals3[j]: " + duals3[j]); // + " prb: " + prb);        	
            alpha += (dualsDelay[j]*14); //prb*14);        	
        }
        
        for (int i = 0; i < dualsBnd.length; i++)
            for (int j = 0; j < dualsBnd[i].length; j++)        	
	        {
	//            System.out.println(" j: " + j + " duals4[j]: " + duals4[j]); // + " prb: " + prb);        	
	            alpha += (dualsBnd[i][j]); //*prb);       	
	        }

        if(Controller.expExcess)
        	alpha += (dualRisk*Controller.excessTgt); //*prb);        
        
        logger.debug("final alpha value: " + alpha);
    }

    private synchronized static void calculateBeta(double[] dualsDelay, double dualRisk) {
        ArrayList<Integer> durations = dataRegistry.getDurations();
        ArrayList<Leg> legs = dataRegistry.getLegs();

        for (int i = 0; i < durations.size(); i++)
            for (int j = 0; j < legs.size(); j++)
            {
                beta[i][j] += dualsDelay[j] * -durations.get(i); // * prb;
//                System.out.println(" i: " + i + " j: " + j + " b: " + beta[i][j]
//                		+ " d: " + dualsDelay[j] + " d: " +  durations.get(i) + " prb: "); // + prb);
                
                if(Controller.expExcess)                
                	beta[i][j] += dualRisk*durations.get(i); // * prb;                
            }
    }

    public void solveSequential(ArrayList<Integer> scenarioDelays, ArrayList<Double> probabilities) {
        final int numScenarios = dataRegistry.getNumScenarios();
        for (int i = 0; i < numScenarios; i++) {
            DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), scenarioDelays.get(i));
            HashMap<Integer, Integer> legDelays = dgen.generateDelays();
            SubSolverRunnable subSolverRunnable = new SubSolverRunnable(i, legDelays, probabilities.get(i));
            subSolverRunnable.run();
            logger.info("Solved scenario " + i + " numScenarios: " + numScenarios);
        }
    }

    public void solveParallel(ArrayList<Integer> scenarioDelays, ArrayList<Double> probabilities) throws OptException {
        try {
            ExecutorService exSrv = Executors.newFixedThreadPool(numThreads);
            
            for (int i = 0; i < dataRegistry.getNumScenarios(); i++) {
                // Thread.sleep(500);

                DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getTails(), scenarioDelays.get(i));
                HashMap<Integer, Integer> legDelays = dgen.generateDelays();

                SubSolverRunnable subSolverRunnable = new SubSolverRunnable(i, legDelays, probabilities.get(i));
                exSrv.execute(subSolverRunnable); // this calls run() method below
                logger.info("Solved scenario " + i);
            }

            exSrv.shutdown();
            while (!exSrv.isTerminated())
                Thread.sleep(100);
        } catch (InterruptedException ie) {
            logger.error(ie.getStackTrace());
            throw new OptException("error at buildSubModel");
        }
    }

    class SubSolverRunnable implements Runnable {
        private int scenarioNum;
        private HashMap<Integer, Integer> randomDelays;
        private double probability;

        SubSolverRunnable(int scenarioNum, HashMap<Integer, Integer> randomDelays, double probability) {
            this.scenarioNum = scenarioNum;
            this.randomDelays = randomDelays;
            this.probability = probability;
        }
        
        public void updatePaths(Tail t, ArrayList<Path> arrT)
        {
        	HashMap<Integer, ArrayList<Path>> pAll;                        	
        	
        	if(hmPaths.containsKey(this.scenarioNum))          
        	{
        		pAll = hmPaths.get(this.scenarioNum);
        		ArrayList<Path> arrPath;
        		
        		if(pAll.containsKey(t.getId()))
        			arrPath = pAll.get(t.getId());
        		else
        			arrPath = new ArrayList<Path>();
        		
        		arrPath.addAll(arrT);                        			
        	}
        	else
        	{
        		pAll = new HashMap<Integer, ArrayList<Path>>();
        		
        		ArrayList<Path> arrPath = new ArrayList<Path>();
        		arrPath.addAll(arrT);
        		
        		pAll.put(t.getId(), arrPath);
        		hmPaths.put(this.scenarioNum, pAll);
        	}      	
        }

        //exSrv.execute(buildSDThrObj) calls brings you here
        public void run() {
            try 
            {            	
            	HashMap<Integer, ArrayList<Path>> pathsAll = new HashMap<Integer, ArrayList<Path>>();
            	
            	if(hmPaths.containsKey(this.scenarioNum))            		
            		pathsAll = hmPaths.get(this.scenarioNum);            	
            	            	
            	//initial paths
            	for (Map.Entry<Integer, Path> entry : dataRegistry.getTailHashMap().entrySet()) {
            	    int key = entry.getKey();
            	    
            	    if(pathsAll.containsKey(key))
            	    {
            	    	ArrayList<Path> lList = pathsAll.get(key);
            	    	lList.add(entry.getValue());            	    	
            	    }
            	    else
            	    {
            	    	ArrayList<Path> p = new ArrayList<Path>();
            	    	p.add(entry.getValue());
            	    	pathsAll.put(key,p);            	    	
            	    }           	    	
            	}            	
            	
//            	have the paths and run it under a loop    
                SubSolver s1 = new SubSolver(randomDelays, probability);            	
                HashMap<Integer, Integer> legDelayMap = s1.getLegDelays(dataRegistry.getLegs(), dataRegistry.getDurations(), xValues);
                
                boolean solveAgain = true;
                double uBoundValue = 0;
                
                double[] dualsLeg;
                double[] dualsTail;
                double[] dualsDelay;                
                
//            	HashMap<Integer, ArrayList<Path>> itrPathsAll = new HashMap<Integer, ArrayList<Path>>();
                int wCnt  = -1;
                while(solveAgain)
                {
                	  wCnt++;
//                    if(solveAgain)
//                    {
                	
                        // beta x + theta >= alpha - Benders cut
                        SubSolver s = new SubSolver(randomDelays, probability);
                        s.constructSecondStage(xValues, dataRegistry, scenarioNum, iter, pathsAll);
                        s.solve();
                        s.collectDuals();
                        s.writeLPFile("", iter, wCnt, this.scenarioNum);
                        uBoundValue = s.getObjValue();
                        calculateAlpha(s.getDualsLeg(), s.getDualsTail(), s.getDualsDelay(), s.getDualsBnd(), s.getDualsRisk());
                        calculateBeta(s.getDualsDelay(), s.getDualsRisk());
                        
                        dualsLeg   = s.getDualsLeg();
                        dualsTail  = s.getDualsTail();
                        dualsDelay = s.getDualsDelay();                
                        
                        s.end();                   	
//                    }
//                    else
//                        uBound += uBoundValue; // from last iteration
                    
//                    if(wCnt > 2)
//                    	System.exit(0);
                        
                	boolean pathAdded =  false;
                	int index = 0;
                    for(Tail t: dataRegistry.getTails())
                    {                    			
                        ArrayList<Path> arrT = new LabelingAlgorithm().getPaths(dataRegistry, dataRegistry.getTails(), legDelayMap, t, 
                        		dualsLeg, dualsTail[index], dualsDelay, pathsAll.get(t.getId()));
                        
                        if(arrT.size() > 0) // add the paths to the master list
                        {
//                            updatePaths(t, arrT); dont add the paths since the list changes everytime based on the new xValue                       	
                           	pathAdded = true; 
                           	
                            System.out.println(wCnt + " Label-Start: " + t.getId());                           	
                           	for(Path p:arrT)
                           		Path.displayPath(p);
                        
                           	System.out.println(wCnt + " Label-End: " + t.getId());                           	
                        }
                        
                        System.out.println(wCnt + " pathsAll-size: " + pathsAll.get(t.getId()).size());
                        
                    	ArrayList<Path> paths = new ArrayList<Path>();
                    	paths = pathsAll.get(t.getId());                      
                        paths.addAll(arrT);
                        index++;
                        
                        System.out.println(wCnt + " PathsAll-Start: " + t.getId());                           	
                       	for(Path p:paths)
                       		Path.displayPath(p);
                        System.out.println(wCnt + " PathsAll-End: " + t.getId());                        
                    }   
                	
                    if(!pathAdded)
                    	solveAgain = false;               	
                }
                
                uBound += uBoundValue; // from last iteration               
                
            } catch (OptException oe) {
                logger.error("submodel run for scenario " + scenarioNum + " failed.");
                logger.error(oe);
                System.exit(17);
            }
        }
    }

    public static double getuBound() {
        return uBound;
    }

    public static double getAlpha() {
        return alpha;
    }

    public static double[][] getBeta() {
        return beta;
    }
    
    public static class ScenarioData{
    	int sceNo;
    	int iter;
    	int pathIndex;
    	int tailIndex;
    	int legId;   	
    	
    	public int getSceNo() {
			return sceNo;
		}    	

		public int getIter() {
			return iter;
		}

		public void setIter(int iter) {
			this.iter = iter;
		}
		
		public void setSceNo(int sceNo) {
			this.sceNo = sceNo;
		}

		public int getPathIndex() {
			return pathIndex;
		}

		public void setPathIndex(int pathIndex) {
			this.pathIndex = pathIndex;
		}

		public int getTailIndex() {
			return tailIndex;
		}

		public void setTailIndex(int tailIndex) {
			this.tailIndex = tailIndex;
		}

		public int getLegId() {
			return legId;
		}

		public void setLegId(int legId) {
			this.legId = legId;
		}

		public static HashMap<ScenarioData,Integer> dataStore = new HashMap<ScenarioData,Integer>();    	
    	   	
    	public ScenarioData() {
			super();
		}

    	public ScenarioData(int sceNo, int iter, int pathIndex, int tailIndex, int legId) {
			super();
			this.sceNo = sceNo;
			this.iter  = iter;
			this.pathIndex = pathIndex;
			this.tailIndex = tailIndex;
			this.legId = legId;
		}

		public static void addData(int sNo, int iter, int pIndex, int tIndex, int legId, int duration)
    	{
    		ScenarioData sd = new ScenarioData(sNo, iter, pIndex, tIndex, legId);
    		dataStore.put(sd, duration);
    	}  
		
		public static void printData()
		{
			System.out.println(" Prints the scenario data: ");
			
			for (Map.Entry<ScenarioData, Integer> entry : dataStore.entrySet()) {
				ScenarioData key = entry.getKey();
			    Integer value = entry.getValue();
			    System.out.println(key.sceNo +","+ key.iter + "," + key.pathIndex + "," + key.tailIndex + ","+key.legId + ","+ value);
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + iter;
			result = prime * result + legId;
			result = prime * result + pathIndex;
			result = prime * result + sceNo;
			result = prime * result + tailIndex;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ScenarioData other = (ScenarioData) obj;
			if (iter != other.iter)
				return false;
			if (legId != other.legId)
				return false;
			if (pathIndex != other.pathIndex)
				return false;
			if (sceNo != other.sceNo)
				return false;
			if (tailIndex != other.tailIndex)
				return false;
			return true;
		}	
		
    }
    
}
