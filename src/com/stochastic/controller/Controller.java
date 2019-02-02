package com.stochastic.controller;

import com.stochastic.domain.Leg;
import com.stochastic.Main;
import com.stochastic.dao.EquipmentsDAO;
import com.stochastic.dao.ParametersDAO;
import com.stochastic.dao.ScheduleDAO;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.postopt.SolutionManager;
import com.stochastic.registry.DataRegistry;
import com.stochastic.solver.MasterSolver;
import com.stochastic.solver.SubSolverWrapper;
import com.stochastic.utility.OptException;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.xml.sax.SAXException;

public class Controller {
    /**
     * Class that controls the entire solution process from reading data to writing output.
     */
    private final static Logger logger = LogManager.getLogger(Controller.class);
    private DataRegistry dataRegistry;
    private ArrayList<Integer> scenarioDelays;
    private ArrayList<Double> scenarioProbabilities;
    private String instancePath;
    
    public static ArrayList<Double> delayResults = new ArrayList<Double>(); 
    public static ArrayList<Double> bounds = new ArrayList<Double>();
    
    // parameters for expExcess
    public static boolean expExcess = false;
    public static double rho = 0.9;    
    public static double excessTgt = 40;
    
    public static int[][] sceVal;    
    
    public Controller() {
        dataRegistry = new DataRegistry();
    }

	public final void readData(String instancePath) throws OptException {
        logger.info("Started reading data...");
//        instancePath = getScenarioPath();
        dataRegistry.setNumScenarios(Main.numScenarios); 
        dataRegistry.setScale(3.5);
        dataRegistry.setShape(0.25);

        ArrayList<Integer> durations = new ArrayList<>(Arrays.asList(5, 10, 15, 20, 25, 30));
        dataRegistry.setDurations(durations);

        // Read parameters
        ParametersDAO parametersDAO = new ParametersDAO(instancePath + "\\Parameters.xml");
        dataRegistry.setWindowStart(parametersDAO.getWindowStart());
        dataRegistry.setWindowEnd(parametersDAO.getWindowEnd());
        dataRegistry.setMaxLegDelayInMin(parametersDAO.getMaxFlightDelayInMin());
        logger.info("Completed reading parameter data from Parameters.xml.");

        // Read equipment data
        dataRegistry.setEquipment(new EquipmentsDAO(instancePath + "\\Equipments.xml").getEquipments().get(0));
        logger.info("Completed reading equipment data from Equipments.xml.");

        // Read leg data and remove unnecessary legs
        ArrayList<Leg> legs = new ScheduleDAO(instancePath + "\\Schedule.xml",
                dataRegistry.getMaxLegDelayInMin()).getLegs();
        storeLegs(legs);
        logger.info("Collected leg and tail data from Schedule.xml.");

        StringBuilder tailsStr = new StringBuilder();
        for(Tail tail : dataRegistry.getTails()) {
            tailsStr.append(tail.getId());
            tailsStr.append(" ");
        }
        logger.debug("loaded tails: " + tailsStr.toString());
        logger.info("Completed reading data.");
    }

    public final void solve() throws OptException {
        ArrayList<Leg> legs = dataRegistry.getLegs();
        ArrayList<Tail> tails = dataRegistry.getTails();
        ArrayList<Integer> durations = dataRegistry.getDurations();

        int iter = -1;        
        MasterSolver.MasterSolverInit(legs, tails, durations, dataRegistry.getWindowStart());
        MasterSolver.constructFirstStage();
        // MasterSolver.writeLPFile("master_initial.lp");
        MasterSolver.solve(iter);
        MasterSolver.addColumn();

        double lBound;
        double uBound = Double.MAX_VALUE;

        logger.info("Algorithm starts.");

        // generate random delays for 2nd stage scenarios.
        generateScenarioDelays(dataRegistry.getScale(), dataRegistry.getShape());
//        generateTestDelays();
        logScenarioDelays();

        // sceVal = new int[3][5];
        // Random rand = new Random();
        // for(int i=0; i<3;i++)
        //    for(int j=0; j<5;j++)
        //    	sceVal[i][j] = (i+20) + j; //rand.nextInt(40 - 20 + 1) + 20; // (max - min + 1) + min;  (i+20) + j

        do {
            iter++;
            SubSolverWrapper.SubSolverWrapperInit(dataRegistry, MasterSolver.getxValues(), iter);
            new SubSolverWrapper().solveSequential(scenarioDelays, scenarioProbabilities);
            // new SubSolverWrapper().solveParallel(scenarioDelays, scenarioProbabilities);

            MasterSolver.constructBendersCut(SubSolverWrapper.getAlpha(), SubSolverWrapper.getBeta());

            MasterSolver.writeLPFile("master_" + iter + ".lp");
            MasterSolver.solve(iter);

            lBound = MasterSolver.getObjValue();

            logger.info("XXXX----------LB: " + lBound + " UB: " + uBound + " Iter: " + iter
            		+ " SubSolverWrapper.getuBound(): " + SubSolverWrapper.getuBound());
            
            if(SubSolverWrapper.getuBound() < uBound)
                uBound = SubSolverWrapper.getuBound();

            logger.info("--------------LB: " + lBound + " UB: " + uBound + " Iter: " + iter);
        } while(uBound - lBound > 0.001); // && (System.currentTimeMillis() - Optimizer.stTime)/1000 < Optimizer.runTime); // && iter < 10);

        MasterSolver.printSolution();
        MasterSolver.end();
        bounds.add(lBound);
        bounds.add(uBound);
        
        if(lBound - uBound > 1)
        {
        	System.out.println("DATA PRINTED");
        	SubSolverWrapper.ScenarioData.printData();
        	
        	System.out.println(" xxx: " +  sceVal[100][100]);
        }
        
        logger.info("Algorithm ends.");
    }

    private void generateTestDelays() {
        // scenarioDelays = new ArrayList<>(Collections.singletonList(45));
        // scenarioProbabilities = new ArrayList<>(Collections.singletonList(1.0));

//        scenarioDelays = new ArrayList<>(Arrays.asList(17, 21, 23, 26, 29, 30, 33, 37, 38));
    	scenarioDelays = new ArrayList<>(Arrays.asList(19,21,23,24,25,26,29,30,35,36,38,43,45,46,51,62));    	
//        scenarioProbabilities = new ArrayList<>(Arrays.asList(0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.2));
        scenarioProbabilities = new ArrayList<>(Arrays.asList(0.05,0.1,0.05,0.05,0.05,0.05,0.05,0.05,0.1,0.05,0.05,
        		0.05, 0.1, 0.1, 0.05, 0.05));

        dataRegistry.setNumScenarios(scenarioDelays.size());
        dataRegistry.setMaxLegDelayInMin(Collections.max(scenarioDelays));
    }

    private void generateScenarioDelays(double scale, double shape) {
        // Generates random delays that will be applied to the first flight of each tail's original schedule.
        // Also generates delay probabilities using frequency values.
        // This function changes the number of scenarios as well if delay times repeat.

        int numSamples = dataRegistry.getNumScenarios();
        LogNormalDistribution logNormal = new LogNormalDistribution(scale, shape);

        int[] delayTimes = new int[numSamples];
        for (int i = 0; i < numSamples; ++i)
            delayTimes[i] = (int) Math.round(logNormal.sample());

        Arrays.sort(delayTimes);
        scenarioDelays = new ArrayList<>();
        scenarioProbabilities = new ArrayList<>();

        DecimalFormat df = new DecimalFormat("##.##");
        df.setRoundingMode(RoundingMode.HALF_UP);

        final double baseProbability = 1.0 / numSamples;
        int numCopies = 1;

        scenarioDelays.add(delayTimes[0]);
        int prevDelayTime = delayTimes[0];
        for(int i = 1; i < numSamples; ++i) {
            int delayTime = delayTimes[i];

            if(delayTime != prevDelayTime) {
                final double prob = Double.parseDouble(df.format(numCopies * baseProbability));
                scenarioProbabilities.add(prob); // add probabilities for previous time.
                scenarioDelays.add(delayTime); // add new delay time.
                numCopies = 1;
            } else
                numCopies++;

            prevDelayTime = delayTime;
        }
        scenarioProbabilities.add(numCopies * baseProbability);
        dataRegistry.setNumScenarios(scenarioDelays.size());
        int minMaxDelay = Collections.max(scenarioDelays);
        if(minMaxDelay > dataRegistry.getMaxLegDelayInMin())
            dataRegistry.setMaxLegDelayInMin(minMaxDelay);
    }

    private void logScenarioDelays() {
        logger.info("updated max 2nd stage delay: " + dataRegistry.getMaxLegDelayInMin());
        logger.info("updated number of scenarios: " + scenarioDelays.size());

        StringBuilder delayStr = new StringBuilder();
        StringBuilder probStr = new StringBuilder();
        delayStr.append("scenario delays: ");
        probStr.append("scenario probabilities: ");
        for(int i = 0; i < scenarioDelays.size(); ++i) {
            delayStr.append(scenarioDelays.get(i));
            delayStr.append(" ");
            probStr.append(scenarioProbabilities.get(i));
            probStr.append(" ");
        }

        logger.info(delayStr);
        logger.info(probStr);
    }

    private String getScenarioPath() throws OptException {
        try {
            File xmlFile = new File("config.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            Node scenarioNode = doc.getElementsByTagName("scenarioPath").item(0);
            return scenarioNode.getTextContent();
        } catch (ParserConfigurationException pce) {
            logger.error(pce);
            throw new OptException("Unable to create DocumentBuilder to read config.xml");
        } catch (IOException ioe) {
            logger.error(ioe);
            throw new OptException("Unable to read config.xml");
        } catch (SAXException se) {
            logger.error(se);
            throw new OptException("Possibly ill-formed xml in config.xml");
        }
    }

    private void storeLegs(ArrayList<Leg> inputLegs) {    	
        final ArrayList<Integer> tailIds = dataRegistry.getEquipment().getTailIds();
        final LocalDateTime windowStart = dataRegistry.getWindowStart();
        final LocalDateTime windowEnd = dataRegistry.getWindowEnd();
        ArrayList<Leg> legs = new ArrayList<>();
        HashMap<Integer, ArrayList<Leg>> tailHashMap = new HashMap<>();

        // Cleanup unnecessary legs.
        Integer index = 0;
        for(Leg leg : inputLegs) {
            if(leg.getArrTime().isBefore(windowStart)
                    || leg.getDepTime().isAfter(windowEnd))
                continue;

            Integer tailId = leg.getOrigTailId();
            if(!tailIds.contains(tailId))
                continue;

            leg.setIndex(index);
            ++index;
            legs.add(leg);

            if(tailHashMap.containsKey(tailId))
                tailHashMap.get(tailId).add(leg);
            else {
                ArrayList<Leg> tailLegs = new ArrayList<>();
                tailLegs.add(leg);
                tailHashMap.put(tailId, tailLegs);
            }
        }       

        dataRegistry.setLegs(legs);

        // build tails from schedule
        ArrayList<Tail> tails = new ArrayList<>();
        for(Map.Entry<Integer, ArrayList<Leg>> entry : tailHashMap.entrySet()) {
            ArrayList<Leg> tailLegs = entry.getValue();
            tailLegs.sort(Comparator.comparing(Leg::getDepTime));
            tails.add(new Tail(entry.getKey(), tailLegs));
        }

        tails.sort(Comparator.comparing(Tail::getId));
        for(int i = 0; i < tails.size(); ++i)
            tails.get(i).setIndex(i);

        dataRegistry.setTails(tails);
        
        HashMap<Integer, Path> tailPaths = new HashMap<Integer, Path>();
        for(Map.Entry<Integer, ArrayList<Leg>> entry : tailHashMap.entrySet()) {
            ArrayList<Leg> tailLegs = entry.getValue();
            tailLegs.sort(Comparator.comparing(Leg::getDepTime));

            Path p = new Path(dataRegistry.getTail(entry.getKey()));
            
            for(Leg l:tailLegs)
            	p.addLeg(l, 0);
            
            tailPaths.put(entry.getKey(), p);
        }        
        dataRegistry.setTailHashMap(tailPaths);
    }

    public void generateDelays()
    {
    	SolutionManager.generateDelaysForComparison(Main.numTestScenarios, dataRegistry);
    }
    
    public void processSolution(boolean qualifySolution, double[][] xValues) throws OptException {
        SolutionManager sm = new SolutionManager(instancePath, dataRegistry, scenarioDelays, scenarioProbabilities,	xValues);
        if(qualifySolution)
            sm.compareSolutions(Main.numTestScenarios); 
        sm.writeOutput();
    }
    
    public void writeResults()
    {    	
		try 
		{
    	
			BufferedWriter out = 
				new BufferedWriter(new FileWriter("Output.txt", true)); //new BufferedWriter(new FileWriter("Output.txt"));    	
    
			out.write(Main.path + "," + dataRegistry.getLegs().size() + "," + dataRegistry.getTails().size() + "," + Main.numScenarios + 
					"," + Main.numTestScenarios + "," + Main.tsRuntime + "," + Main.tsRARuntime);

			for(int i=0; i < bounds.size(); i++)
				out.write("," + bounds.get(i));		
			
			for(int i=0; i < delayResults.size(); i++)
				out.write("," + delayResults.get(i));			
			
			out.write("\n");	
			out.close();    		

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}       

    }
    
}
