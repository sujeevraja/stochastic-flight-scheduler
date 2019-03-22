package com.stochastic.solver;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.Constants;
import com.stochastic.utility.OptException;
import com.stochastic.utility.Utility;
import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class MasterSolver {
    /**
     * Class that solves the entire 2-stage stochastic programming problem.
     */
    private final static Logger logger = LogManager.getLogger(MasterSolver.class);
    private static ArrayList<Leg> legs;
    private static ArrayList<Tail> tails;
    private static int[] durations;
    private static LocalDateTime startTime;

    // cplex variables
    private static IloIntVar[][] X;
    private static IloCplex masterCplex;
    private static IloLPMatrix mastLp;
    private static IloNumVar thetaVar;
    private static IloObjective obj;

    private static double objValue;

    private static int[][] indices;
    private static double[][] values;
    private static double[][] xValues;
    private static double     theta;
    private static int cutcounter = 0;

//    private static IloNumVar neta; // = cplex.numVar(-Double.POSITIVE_INFINITY, 0, "neta");    

    public static void MasterSolverInit(ArrayList<Leg> legs, ArrayList<Tail> tails, int[] durations)
            throws OptException {
        try {
            MasterSolver.legs = legs;
            MasterSolver.tails = tails;
            MasterSolver.durations = durations;

            masterCplex = new IloCplex();
            if (!Parameters.isDebugVerbose())
                masterCplex.setOut(null);

            X = new IloIntVar[MasterSolver.durations.length][MasterSolver.legs.size()];
        } catch (IloException e) {
            logger.error(e.getStackTrace());
            throw new OptException("CPLEX error while adding benders variable");
        }
    }

    public static void addColumn() throws OptException {
        try {
            masterCplex.setLinearCoef(obj, thetaVar, 1);
        } catch (IloException e) {
            logger.error(e);
            throw new OptException("CPLEX error while adding benders variable");
        }
    }

    public static void solve(int iter) throws OptException {
        try {
            // Master.mastCplex.addMaximize();
            masterCplex.solve();
            objValue = masterCplex.getObjValue();

            logger.debug("master objective: " + objValue);
            xValues = new double[durations.length][legs.size()];
            for (int i = 0; i < durations.length; i++)
                xValues[i] = MasterSolver.masterCplex.getValues(X[i]);
            
            if(iter > -1)
            	theta =  MasterSolver.masterCplex.getValue(thetaVar);

            // MasterSolver.xValues =  Master.mastCplex.getValues(Master.mastLp, 0, Master.mastCplex.getNcols(), IloCplex.IncumbentId);
        } catch (IloException e) {
            logger.error(e);
            throw new OptException("error solving master problem");
        }
    }
    
    public static double getFSObjValue()
    {
    	return objValue - theta;
    }

    public static void writeLPFile(String fName) throws IloException {
        masterCplex.exportModel(fName);
    }

    public static void writeSolution(String fName) throws IloException {
        masterCplex.writeSolution(fName);
    }

    public static void constructFirstStage() throws OptException {
        try {
            for (int i = 0; i < durations.length; i++)
                for (int j = 0; j < legs.size(); j++) {
                    String varName = "X_" + durations[i] + "_" + legs.get(j).getId();
                    X[i][j] = masterCplex.boolVar(varName);
                }

            thetaVar = masterCplex.numVar(-Double.MAX_VALUE, Double.MAX_VALUE, "theta");

            addObjective();
            addDurationCoverConstraints();
            addOriginalRoutingConstraints();
        } catch (IloException e) {
            logger.error(e.getStackTrace());
            throw new OptException("CPLEX error solving first stage MIP");
        }
    }

    private static void addObjective() throws IloException {
        // multiplied delay times by 0.5 as it should be cheaper to reschedule flights in the first stage
        // rather than delaying them in the second stage. Otherwise, there is no difference between planning
        // (first stage) and recourse (second stage).

        IloLinearNumExpr cons = masterCplex.linearNumExpr();
        for (int i = 0; i < durations.length; i++)
            for (int j = 0; j < legs.size(); j++)
                cons.addTerm(X[i][j], durations[i] * legs.get(i).getRescheduleCostPerMin());

        // cons.addTerm(thetaVar, 0);
        // cons.addTerm(thetaVar, 1);
        obj = masterCplex.addMinimize(cons);
    }

    private static void addDurationCoverConstraints() throws IloException {
        for (int i = 0; i < legs.size(); i++) {
            IloLinearNumExpr cons = masterCplex.linearNumExpr();

            for (int j = 0; j < durations.length; j++)
                cons.addTerm(X[j][i], 1);

            IloRange r = masterCplex.addLe(cons, 1);
            r.setName("duration_cover_" + legs.get(i).getId());
        }
    }

    private static void addOriginalRoutingConstraints() throws IloException {
        for(Tail tail : tails) {
            ArrayList<Leg> tailLegs = tail.getOrigSchedule();
            if(tailLegs.size() <= 1)
                continue;

            for(int i = 0; i < tailLegs.size() - 1; ++i) {
                Leg currLeg = tailLegs.get(i);
                Leg nextLeg = tailLegs.get(i + 1);
                int currLegIndex = currLeg.getIndex();
                int nextLegIndex = nextLeg.getIndex();

                IloLinearNumExpr cons = masterCplex.linearNumExpr();
                for (int j = 0; j < durations.length; j++) {
                    cons.addTerm(X[j][currLegIndex], durations[j]);
                    cons.addTerm(X[j][nextLegIndex], -durations[j]);
                }

                int rhs = (int) Duration.between(currLeg.getArrTime(), nextLeg.getDepTime()).toMinutes();
                rhs -= currLeg.getTurnTimeInMin();
                IloRange r = masterCplex.addLe(cons, (double) rhs);
                r.setName("connect_" + currLeg.getId() + "_" + nextLeg.getId());
            }
        }
    }

    public static void constructBendersCut(double alphaValue, double[][] betaValue) throws OptException {
        try {
            IloLinearNumExpr cons = masterCplex.linearNumExpr();

            for (int i = 0; i < durations.length; i++)
                for (int j = 0; j < legs.size(); j++)
                    if (Math.abs(betaValue[i][j]) >= Constants.EPS)
                        cons.addTerm(X[i][j], Utility.roundUp(betaValue[i][j], 3));

            cons.addTerm(thetaVar, 1);

            double rhs = Math.abs(alphaValue) >= Constants.EPS ? alphaValue : 0.0;
            IloRange r = masterCplex.addGe(cons, Utility.roundDown(rhs, 3));
            r.setName("benders_cut_" + cutcounter);
            ++cutcounter;

        } catch (IloException e) {
            logger.error(e.getStackTrace());
            throw new OptException("CPLEX error solving first stage MIP");
        }
    }
    
    
    public static void printSolution() {
        try {
             // solution value           
            for(int i=0; i< durations.length; i++)
            	for(int j=0; j< legs.size(); j++)            	
                	if(xValues[i][j] > 0)
                		logger.debug(" xValues: " + " i: " + i + " j: " + j + " : " + X[i][j].getName() + " : "
                                + xValues[i][j] + " , " + durations[i]);
            
       		logger.debug(" theta: " + theta);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Error: SubSolve");
        }
    }
    

    public static double getObjValue() {
        return objValue;
    }

    public static double[][] getxValues() {
        return xValues;
    }
    
    public static void end()
    {
//        legs.clear();
//        tails.clear();
//        durations.clear();
                // cplex variables
        X        = null;
        thetaVar = null;
        obj      = null;        
        mastLp   = null;        
        masterCplex.end();    	
    }
    
}
