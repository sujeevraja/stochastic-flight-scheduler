package stochastic.model;

import ilog.concert.*;
import ilog.cplex.IloCplex;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.registry.Parameters;

import java.time.Duration;
import java.util.ArrayList;

public class MasterModelBuilder {
    private ArrayList<Leg> legs;
    private ArrayList<Tail> tails;
    private int[] durations;

    private IloCplex cplex;
    private IloIntVar[][] x; // x[i][j] = 1 if durations[i] selected for leg[j] reschedule, 0 otherwise.

    public MasterModelBuilder(ArrayList<Leg> legs, ArrayList<Tail> tails, IloCplex cplex) {
        this.legs = legs;
        this.tails = tails;
        this.durations = Parameters.getDurations();

        this.cplex = cplex;
        x = new IloIntVar[durations.length][legs.size()];
    }

    public void buildVariables() throws IloException {
        for (int i = 0; i < durations.length; i++) {
            for (int j = 0; j < legs.size(); j++) {
                String varName = "x_" + durations[i] + "_" + legs.get(j).getId();
                x[i][j] = cplex.boolVar(varName);
            }
        }
    }

    public IloLinearNumExpr getObjExpr() throws IloException {
        // Ensure that reschedule costing is cheaper than delay costing. Otherwise, there is no difference between
        // planning (first stage) and recourse (second stage).

        IloLinearNumExpr cons = cplex.linearNumExpr();
        for (int i = 0; i < durations.length; i++) {
            for (int j = 0; j < legs.size(); j++) {
                cons.addTerm(x[i][j], durations[i] * legs.get(j).getRescheduleCostPerMin());
            }
        }

        return cons;
    }

    public void constructFirstStage() throws IloException {
        addDurationCoverConstraints();
        addOriginalRoutingConstraints();
        addBudgetConstraint();
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

    public IloIntVar[][] getX() {
        return x;
    }

    public double[][] getxValues() throws IloException {
        double[][] xValues = new double[durations.length][];
        for (int i = 0; i < durations.length; i++) {
            xValues[i] = cplex.getValues(x[i]);
        }
        return xValues;
    }
}
