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
    private int budget;

    private IloCplex cplex;
    private IloNumVar[] x; // x[i] = #minutes of reschedule of flight i.

    public MasterModelBuilder(ArrayList<Leg> legs, ArrayList<Tail> tails, int budget, IloCplex cplex) {
        this.legs = legs;
        this.tails = tails;
        this.budget = budget;
        this.cplex = cplex;
        x = new IloIntVar[legs.size()];
    }

    public void buildVariables() throws IloException {
        for (int j = 0; j < legs.size(); j++) {
            String varName = "x_" + legs.get(j).getId();
            x[j] = cplex.numVar(0, Parameters.getFlightRescheduleBound(), IloNumVarType.Int, varName);

        }
    }

    public IloLinearNumExpr getObjExpr() throws IloException {
        // Ensure that reschedule costing is cheaper than delay costing. Otherwise, there is no difference between
        // planning (first stage) and recourse (second stage).

        IloLinearNumExpr cons = cplex.linearNumExpr();
        for (int j = 0; j < legs.size(); j++) {
            cons.addTerm(x[j], legs.get(j).getRescheduleCostPerMin());
        }

        return cons;
    }

    public void constructFirstStage() throws IloException {
        // addDurationCoverConstraints();
        addOriginalRoutingConstraints();
        addBudgetConstraint();
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
                cons.addTerm(x[currLegIndex], 1);
                cons.addTerm(x[nextLegIndex], -1);

                int rhs = (int) Duration.between(currLeg.getArrTime(), nextLeg.getDepTime()).toMinutes();
                rhs -= currLeg.getTurnTimeInMin();
                IloRange r = cplex.addLe(cons, (double) rhs);
                r.setName("connect_" + currLeg.getId() + "_" + nextLeg.getId());
            }
        }
    }

    private void addBudgetConstraint() throws IloException {
        IloLinearNumExpr budgetExpr = cplex.linearNumExpr();
        for (int j = 0; j < legs.size(); ++j)
            budgetExpr.addTerm(x[j], 1);

        IloRange budgetConstraint = cplex.addLe(budgetExpr, budget);
        budgetConstraint.setName("reschedule_time_budget");
    }

    public IloNumVar[] getX() {
        return x;
    }

    public double[] getxValues() throws IloException {
        return cplex.getValues(x);
    }
}
