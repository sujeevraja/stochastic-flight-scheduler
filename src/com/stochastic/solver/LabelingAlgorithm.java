package com.stochastic.solver;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.registry.DataRegistry;

public class LabelingAlgorithm {
	
	public class NodeInfo{
		private double Pi;
		private int    i;
		private double sumDelayDual;
		private double sumflightDual;		
		
		NodeInfo(double pi, int i, double sumDelayDual, double sumflightDual) {
			super();
			Pi = pi;
			this.i = i;
			this.sumDelayDual = sumDelayDual;
			this.sumflightDual = sumflightDual;
		}

		public double getPi() {
			return Pi;
		}
		public void setPi(double pi) {
			Pi = pi;
		}
		public int getI() {
			return i;
		}
		public void setI(int i) {
			this.i = i;
		}
		double getSumDelayDual() {
			return sumDelayDual;
		}
		public void setSumDelayDual(double sumDelayDual) {
			this.sumDelayDual = sumDelayDual;
		}
		double getSumflightDual() {
			return sumflightDual;
		}
		public void setSumflightDual(double sumflightDual) {
			this.sumflightDual = sumflightDual;
		}
	}

	// there should not be any class variables as these methods are called by individual
    private ArrayList<Path> paths; // subproblem columns
    private double[] flightDual;
    private double assignDual;
    private double[] delayDual;
    
    private HashMap<Integer, ArrayList<NodeInfo>> I = new HashMap<Integer, ArrayList<NodeInfo>>();
    private HashMap<Integer, Double> M = new HashMap<Integer, Double>();    
//    private HashMap<Integer, ArrayList<NodeInfo>> M;
    private HashMap<Integer, ArrayList<Integer>> adjacencyList = new HashMap<Integer, ArrayList<Integer>>(); // keys and values are indices of leg list.    
        
    
    // labeling algorithm    
    public ArrayList<Path> getPaths(DataRegistry dataRegistry, ArrayList<Tail> tails, HashMap<Integer, Integer> legDelayMap, Tail tail,
    		double[] fDual, double aDual, double[] dDual, ArrayList<Path> existingPaths)
    {
    	flightDual = fDual;
        assignDual = aDual;
        delayDual  = dDual;
    	
    	ArrayList<Leg> legs = dataRegistry.getLegs();
		adjacencyList = dataRegistry.getNetwork().getAdjacencyList();
        
        // initialize // add the index
        int i=0;
		ArrayList<Integer> stLegs =  new ArrayList<>();

		for(Leg l: legs) {
           if(l.getDepPort().equals(tail.getSourcePort()))
        	stLegs.add(i);
           i++;
        }

		int chooseIndex = getStFlight(stLegs); // populate niList

    	// find the legs which can start in the morning    	
        // chooseIndex = findStart();

    	while(chooseIndex >= 0)
    	{
    		//chooseJindex
			findNextLeg(chooseIndex, dataRegistry);
        	chooseIndex = findStart();    		
    	}
    	
        // find the last leg
		LocalDateTime temp = null;
		int lastFlIndex = -1;
		for (Map.Entry<Integer, ArrayList<NodeInfo>> entry : I.entrySet()) {

			if(temp == null)
			{
				temp = legs.get(entry.getKey()).getArrTime();
				lastFlIndex = entry.getKey();
			}
			else if(temp.isBefore(legs.get(entry.getKey()).getArrTime()))
			{
				temp = legs.get(entry.getKey()).getArrTime();
				lastFlIndex = entry.getKey();
			}
		}

		Path p = new Path(tail);
		int beforeIndex = lastFlIndex;
		while(beforeIndex >= 0)
		{			
			p.addLeg(legs.get(beforeIndex), legDelayMap.get(beforeIndex));
			int niIndex = findMinimum(beforeIndex);

			if(niIndex >= 0)
				beforeIndex = I.get(beforeIndex).get(niIndex).getI();
		}

		ArrayList<Path> paths = new ArrayList<>();
	
		for(Path p1 : existingPaths)
		    if (p.equals(p1))
				return new ArrayList<>();
				
		paths.add(p);
		return paths;
    }   

    private void findNextLeg(int iIndex, DataRegistry dataRegistry)
    {
    	int index     = -1;
    	int niIndex   = -1;
    	double minRci = 100000;
    	
    	int minIndex = findMinimum(iIndex);    	
    	ArrayList<Leg> legs = dataRegistry.getLegs();    	
    	
    	Leg lg = legs.get(iIndex);

    	if(!adjacencyList.containsKey(iIndex))
    		return;
    	
    	for(Integer jIndex: adjacencyList.get(iIndex))
    	{
   	    	if(minRci > flightDual[jIndex] + delayDual[jIndex]) //flightDual[i] + delayDual[i])
   	    	{
	    		minRci = flightDual[jIndex] + delayDual[jIndex]; //flightDual[i] + delayDual[i];
	    		index  = jIndex;
   	    	} 		
    	}    	

    	NodeInfo ni = new NodeInfo(flightDual[index] + delayDual[index],iIndex,I.get(iIndex).get(minIndex).getSumflightDual() +flightDual[index],
    							I.get(iIndex).get(minIndex).getSumDelayDual() + delayDual[index]);    	
    	
    	ArrayList<NodeInfo> niList = new ArrayList<NodeInfo>();
    	
    	if(I.get(index) != null)
    		niList = I.get(index); 
   	
    	niList.add(ni);    	
    	I.put(index, niList);    	
    }
    
    private int findMinimum(int iIndex)
    {
	    ArrayList<NodeInfo> niList = I.get(iIndex);
	    
	    int nIndex  = -1;
	    int cnt = 0;
	    double minRci = 100000;
	    int i = 0;
	    for(NodeInfo ni : niList)
	    {    	    	
	    	if(minRci > ni.getSumflightDual() + ni.getSumDelayDual()) //flightDual[i] + delayDual[i])
	    	{
	    		minRci = ni.getSumflightDual() + ni.getSumDelayDual(); //flightDual[i] + delayDual[i];
	    		nIndex = i;
	    	}
	    	i++;
	    }
	    
	    return nIndex;
    }
    
    private int findStart()
    {
    	int iIndex = -1;
    	double minRci = 100000;
    	int nIndex = 0;
    	
    	for (Map.Entry<Integer, ArrayList<NodeInfo>> entry : I.entrySet()) {
    		
    	    int i = entry.getKey();
    	    if(M.get(i) != null) // already evaluated
    	    	continue;
    	    
    	    ArrayList<NodeInfo> niList = entry.getValue();
    	    
    	    nIndex  = 0;
    	    int cnt = 0;
    	    for(NodeInfo ni : niList)
    	    {    	    	
    	    	if(minRci > flightDual[i] + delayDual[i]) //ni.getSumflightDual() + ni.getSumDelayDual()) //flightDual[i] + delayDual[i])
    	    	{
    	    		minRci = flightDual[i] + delayDual[i]; //ni.getSumflightDual() + ni.getSumDelayDual(); //flightDual[i] + delayDual[i];
    	    		iIndex = i;
    	    	}
    	    	
    	    }   	    
    	}

    	M.put(iIndex, minRci);
    	return iIndex;
    }
    
    private int getStFlight(ArrayList<Integer> stLegsI)
    {
    	int index = -1;
    	double minValue  = 10000;
    	for(int i=0; i<stLegsI.size(); i++)
    	{
    		double netDual = flightDual[i] + assignDual + delayDual[i];
    		
    		if(netDual < minValue)
    		{
    			minValue = netDual;
    			index = i;
    		}
    	}    	
    	
    	NodeInfo ni = new NodeInfo(-1,-1,flightDual[index],delayDual[index]);    	
    	ArrayList<NodeInfo> niList = new ArrayList<NodeInfo>();
    	niList.add(ni);
    	
    	I.put(index, niList);

    	return index;
    }
}
