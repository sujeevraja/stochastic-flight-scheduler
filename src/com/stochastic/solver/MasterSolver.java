package com.stochastic.solver;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.utility.CostUtility;
import com.stochastic.utility.OptException;
import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class MasterSolver {
    /**
     * Class that solves the entire 2-stage stochastic programming problem.
     */
    private final static Logger logger = LogManager.getLogger(MasterSolver.class);
    private final static double eps = 1.0e-5;
    private static ArrayList<Leg> legs;
    private static ArrayList<Tail> tails;
    private static ArrayList<Integer> durations;
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

    public static void MasterSolverInit(ArrayList<Leg> legs, ArrayList<Tail> tails, ArrayList<Integer> durations,
                                        LocalDateTime startTime) throws OptException {
        try {
            MasterSolver.legs = legs;
            MasterSolver.tails = tails;
            MasterSolver.durations = durations;
            MasterSolver.startTime = startTime;

            masterCplex = new IloCplex();
            X = new IloIntVar[MasterSolver.durations.size()][MasterSolver.legs.size()];
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
            xValues = new double[durations.size()][legs.size()];
            for (int i = 0; i < durations.size(); i++)
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

    public static void writeLPFile(String fName) throws OptException {
        try {
            masterCplex.exportModel(fName);
        } catch (IloException e) {
            logger.error(e);
            throw new OptException("error writing LP file");
        }
    }

    public static void constructFirstStage() throws OptException {
        try {
            for (int i = 0; i < durations.size(); i++)
                for (int j = 0; j < legs.size(); j++)
                    X[i][j] = masterCplex.boolVar("X_" + i + "_" + legs.get(j).getId()); // boolVarArray(Data.nD + Data.nT);

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
        for (int i = 0; i < durations.size(); i++)
            for (int j = 0; j < legs.size(); j++)
                cons.addTerm(X[i][j], 0.5 * durations.get(i));

        // cons.addTerm(thetaVar, 0);
        // cons.addTerm(thetaVar, 1);
        obj = masterCplex.addMinimize(cons);
    }

    private static void addDurationCoverConstraints() throws IloException {
        for (int i = 0; i < legs.size(); i++) {
            IloLinearNumExpr cons = masterCplex.linearNumExpr();

            for (int j = 0; j < durations.size(); j++)
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
                for (int j = 0; j < durations.size(); j++) {
                    cons.addTerm(X[j][currLegIndex], durations.get(j));
                    cons.addTerm(X[j][nextLegIndex], -durations.get(j));
                }

                int currArrTime = (int) Duration.between(startTime, currLeg.getArrTime()).toMinutes();
                int turnTime = currLeg.getTurnTimeInMin();
                int nextDeptime = (int) Duration.between(startTime, nextLeg.getDepTime()).toMinutes();
                double rhs = nextDeptime - currArrTime - turnTime;

                IloRange r = masterCplex.addLe(cons, rhs);
                r.setName("connect_" + currLeg.getId() + "_" + nextLeg.getId());
            }
        }
    }

    public static void constructBendersCut(double alphaValue, double[][] betaValue) throws OptException {
        try {
            IloLinearNumExpr cons = masterCplex.linearNumExpr();

            for (int i = 0; i < durations.size(); i++)
                for (int j = 0; j < legs.size(); j++)
                    cons.addTerm(X[i][j], betaValue[i][j]);

            cons.addTerm(thetaVar, 1);

            IloRange r = masterCplex.addGe(cons, alphaValue);
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
            for(int i=0; i< durations.size(); i++)
            	for(int j=0; j< legs.size(); j++)            	
                	if(xValues[i][j] > 0)
                		System.out.println(" xValues: " + " i: " + i + " j: " + j + " : " + X[i][j].getName() + " : " + xValues[i][j] + " , " + durations.get(i));
            
       		System.out.println(" theta: " + theta);            			
            
//            duals1 = subCplex.getDuals(R1);
//            duals2 = subCplex.getDuals(R2);
//            duals3 = subCplex.getDuals(R3);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: SubSolve");
        }
    }
    

    public static double getObjValue() {
        return objValue;
    }

    public static double[][] getxValues() {
        return xValues;
    }
}
