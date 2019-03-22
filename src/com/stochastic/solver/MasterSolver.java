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
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class MasterSolver {
    /**
     * Class that solves the first-stage model (i.e. the Benders master problem).
     */
    private final static Logger logger = LogManager.getLogger(MasterSolver.class);
    private ArrayList<Leg> legs;
    private ArrayList<Tail> tails;
    private int[] durations;

    // CPLEX variables
    private IloCplex cplex;
    private IloIntVar[][] x; // x[i][j] = 1 if durations[i] selected for leg[j] reschedule, 0 otherwise.
    private IloNumVar thetaVar;
    private IloObjective obj;

    private double objValue;
    private double[][] xValues;
    private double thetaValue;
    private int[] reschedules; // reschedules[i] is the selected reschedule duration for legs[i].

    private int cutCounter = 0; // Benders cut counter

    public MasterSolver(ArrayList<Leg> legs, ArrayList<Tail> tails, int[] durations) throws IloException {
        this.legs = legs;
        this.tails = tails;
        this.durations = durations;
        this.reschedules = new int[legs.size()];

        cplex = new IloCplex();
        if (!Parameters.isDebugVerbose())
            cplex.setOut(null);

        x = new IloIntVar[durations.length][legs.size()];
    }

    public void addColumn() throws IloException {
        cplex.setLinearCoef(obj, thetaVar, 1);
    }

    public void solve(int iter) throws IloException {
        cplex.solve();
        objValue = cplex.getObjValue();

        logger.debug("master objective: " + objValue);
        xValues = new double[durations.length][legs.size()];
        Arrays.fill(reschedules, 0);

        for (int i = 0; i < durations.length; i++) {
            xValues[i] = cplex.getValues(x[i]);
            for (int j = 0; j < legs.size(); ++j)
                if (xValues[i][j] >= Constants.EPS)
                    reschedules[j] = durations[i];
        }

        if(iter > -1)
            thetaValue =  cplex.getValue(thetaVar);
    }
    
    public double getFirstStageObjValue()
    {
    	return objValue - thetaValue;
    }

    public void writeLPFile(String fName) throws IloException {
        cplex.exportModel(fName);
    }

    public void writeSolution(String fName) throws IloException {
        cplex.writeSolution(fName);
    }

    public void constructFirstStage() throws IloException {
        for (int i = 0; i < durations.length; i++)
            for (int j = 0; j < legs.size(); j++) {
                String varName = "X_" + durations[i] + "_" + legs.get(j).getId();
                x[i][j] = cplex.boolVar(varName);
            }

        thetaVar = cplex.numVar(-Double.MAX_VALUE, Double.MAX_VALUE, "thetaValue");

        addObjective();
        addDurationCoverConstraints();
        addOriginalRoutingConstraints();
    }

    private void addObjective() throws IloException {
        // multiplied delay times by 0.5 as it should be cheaper to reschedule flights in the first stage
        // rather than delaying them in the second stage. Otherwise, there is no difference between planning
        // (first stage) and recourse (second stage).

        IloLinearNumExpr cons = cplex.linearNumExpr();
        for (int i = 0; i < durations.length; i++)
            for (int j = 0; j < legs.size(); j++)
                cons.addTerm(x[i][j], durations[i] * legs.get(i).getRescheduleCostPerMin());

        // cons.addTerm(thetaVar, 0);
        // cons.addTerm(thetaVar, 1);
        obj = cplex.addMinimize(cons);
    }

    private void addDurationCoverConstraints() throws IloException {
        for (int i = 0; i < legs.size(); i++) {
            IloLinearNumExpr cons = cplex.linearNumExpr();

            for (int j = 0; j < durations.length; j++)
                cons.addTerm(x[j][i], 1);

            IloRange r = cplex.addLe(cons, 1);
            r.setName("duration_cover_" + legs.get(i).getId());
        }
    }

    private void addOriginalRoutingConstraints() throws IloException {
        for(Tail tail : tails) {
            ArrayList<Leg> tailLegs = tail.getOrigSchedule();
            if(tailLegs.size() <= 1)
                continue;

            for(int i = 0; i < tailLegs.size() - 1; ++i) {
                Leg currLeg = tailLegs.get(i);
                Leg nextLeg = tailLegs.get(i + 1);
                int currLegIndex = currLeg.getIndex();
                int nextLegIndex = nextLeg.getIndex();

                IloLinearNumExpr cons = cplex.linearNumExpr();
                for (int j = 0; j < durations.length; j++) {
                    cons.addTerm(x[j][currLegIndex], durations[j]);
                    cons.addTerm(x[j][nextLegIndex], -durations[j]);
                }

                int rhs = (int) Duration.between(currLeg.getArrTime(), nextLeg.getDepTime()).toMinutes();
                rhs -= currLeg.getTurnTimeInMin();
                IloRange r = cplex.addLe(cons, (double) rhs);
                r.setName("connect_" + currLeg.getId() + "_" + nextLeg.getId());
            }
        }
    }

    public void constructBendersCut(double alphaValue, double[][] betaValue) throws OptException {
        try {
            IloLinearNumExpr cons = cplex.linearNumExpr();

            for (int i = 0; i < durations.length; i++)
                for (int j = 0; j < legs.size(); j++)
                    if (Math.abs(betaValue[i][j]) >= Constants.EPS)
                        cons.addTerm(x[i][j], Utility.roundUp(betaValue[i][j], 3));

            cons.addTerm(thetaVar, 1);

            double rhs = Math.abs(alphaValue) >= Constants.EPS ? alphaValue : 0.0;
            IloRange r = cplex.addGe(cons, Utility.roundDown(rhs, 3));
            r.setName("benders_cut_" + cutCounter);
            ++cutCounter;

        } catch (IloException e) {
            logger.error(e.getStackTrace());
            throw new OptException("CPLEX error solving first stage MIP");
        }
    }
    
    
    public void printSolution() {
        for(int i=0; i< durations.length; i++)
            for(int j=0; j< legs.size(); j++)
                if(xValues[i][j] > 0)
                    logger.debug(" xValues: " + " i: " + i + " j: " + j + " : " + x[i][j].getName() + " : "
                            + xValues[i][j] + " , " + durations[i]);

        logger.debug(" thetaValue: " + thetaValue);
    }
    

    public double getObjValue() {
        return objValue;
    }

    public int[] getReschedules() {
        return reschedules;
    }

    public void end() {
        // CPLEX variables
        x = null;
        thetaVar = null;
        obj      = null;        
        cplex.end();
    }
}
