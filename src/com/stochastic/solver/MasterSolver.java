package com.stochastic.solver;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.Constants;
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
    private int numScenarios;

    // CPLEX variables
    private IloCplex cplex;
    private IloIntVar[][] x; // x[i][j] = 1 if durations[i] selected for leg[j] reschedule, 0 otherwise.
    private IloObjective obj;

    private IloNumVar[] thetas;

    private double objValue;
    private double[][] xValues;
    private int[] reschedules; // reschedules[i] is the selected reschedule duration for legs[i].
    private double rescheduleCost; // this is \sum_({p,f} c_f g_p x_{pf} and will be used for the Benders upper bound.
    private double[] thetaValues;

    private int numBendersCuts = 0; // Benders cut counter

    MasterSolver(ArrayList<Leg> legs, ArrayList<Tail> tails, int[] durations, int numScenarios) throws IloException {
        this.legs = legs;
        this.tails = tails;
        this.durations = durations;
        this.numScenarios = numScenarios;
        this.reschedules = new int[legs.size()];

        cplex = new IloCplex();
        if (!Parameters.isDebugVerbose())
            cplex.setOut(null);

        x = new IloIntVar[durations.length][legs.size()];
    }

    void addTheta() throws IloException {
        if (Parameters.isBendersMultiCut()) {
            thetas = new IloNumVar[numScenarios];
            for (int i = 0; i < numScenarios; ++i)
                thetas[i] = cplex.numVar(-Double.MAX_VALUE, Double.MAX_VALUE, "theta_" + i);
        } else {
            thetas = new IloNumVar[1];
            thetas[0] = cplex.numVar(-Double.MAX_VALUE, Double.MAX_VALUE, "theta");
        }
        for (IloNumVar theta : thetas)
            cplex.setLinearCoef(obj, theta, 1);
    }

    public void solve(int iter) throws IloException {
        cplex.solve();
        objValue = cplex.getObjValue();

        logger.debug("master objective: " + objValue);
        xValues = new double[durations.length][legs.size()];
        Arrays.fill(reschedules, 0);

        rescheduleCost = 0;
        for (int i = 0; i < durations.length; i++) {
            xValues[i] = cplex.getValues(x[i]);
            for (int j = 0; j < legs.size(); ++j)
                if (xValues[i][j] >= Constants.EPS) {
                    reschedules[j] = durations[i];
                    rescheduleCost += legs.get(j).getRescheduleCostPerMin() * durations[i];
                }
        }

        if(iter > 0)
            thetaValues = cplex.getValues(thetas);
    }
    
    double getRescheduleCost() {
        return rescheduleCost;
    }

    void writeLPFile(String fName) throws IloException {
        cplex.exportModel(fName);
    }

    void writeCPLEXSolution(String fName) throws IloException {
        cplex.writeSolution(fName);
    }

    void constructFirstStage() throws IloException {
        for (int i = 0; i < durations.length; i++)
            for (int j = 0; j < legs.size(); j++) {
                String varName = "x_" + durations[i] + "_" + legs.get(j).getId();
                x[i][j] = cplex.boolVar(varName);
            }

        addObjective();
        addDurationCoverConstraints();
        addOriginalRoutingConstraints();
        addBudgetConstraint();
    }

    private void addObjective() throws IloException {
        // Ensure that reschedule costing is cheaper than delay costing. Otherwise, there is no difference between
        // planning (first stage) and recourse (second stage).

        IloLinearNumExpr cons = cplex.linearNumExpr();
        for (int i = 0; i < durations.length; i++)
            for (int j = 0; j < legs.size(); j++)
                cons.addTerm(x[i][j], durations[i] * legs.get(j).getRescheduleCostPerMin());

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

    private void addBudgetConstraint() throws IloException {
        IloLinearNumExpr budgetExpr = cplex.linearNumExpr();
        for (int i = 0; i < durations.length; ++i)
            for (int j = 0; j < legs.size(); ++j)
                budgetExpr.addTerm(x[i][j], durations[i]);

        IloRange budgetConstraint = cplex.addLe(budgetExpr, (double) Parameters.getRescheduleTimeBudget());
        budgetConstraint.setName("reschedule_time_budget");
    }

    void addBendersCut(BendersCut cutData, int thetaIndex) throws IloException {
        IloLinearNumExpr cons = cplex.linearNumExpr();

        // TODO remove rounding in this function
        double[][] beta = cutData.getBeta();
        for (int i = 0; i < durations.length; i++)
            for (int j = 0; j < legs.size(); j++)
                if (Math.abs(beta[i][j]) >= Constants.EPS)
                    cons.addTerm(x[i][j], beta[i][j]);

        cons.addTerm(thetas[thetaIndex], 1);

        double alpha = cutData.getAlpha();
        double rhs = Math.abs(alpha) >= Constants.EPS ? alpha : 0.0;
        IloRange r = cplex.addGe(cons, rhs);
        r.setName("benders_cut_" + numBendersCuts);
        ++numBendersCuts;
    }

    double getObjValue() {
        return objValue;
    }

    double[][] getxValues() {
        return xValues;
    }

    int[] getReschedules() {
        return reschedules;
    }

    double[] getThetaValues() {
        return thetaValues;
    }

    void end() {
        // CPLEX variables
        x = null;
        thetas = null;
        obj = null;
        cplex.end();
    }

    int getNumBendersCuts() {
        return numBendersCuts;
    }
}
