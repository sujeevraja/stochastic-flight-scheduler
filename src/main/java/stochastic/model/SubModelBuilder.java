package stochastic.model;

import ilog.concert.*;
import ilog.cplex.IloCplex;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Path;
import stochastic.registry.Parameters;
import stochastic.utility.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class SubModelBuilder {
    private ArrayList<Leg> legs;
    private int numLegs;
    private ArrayList<Tail> tails;
    private int numTails;
    private HashMap<Integer, ArrayList<Path>> paths;

    private IloCplex cplex;
    private IloNumVar[][] y; // y[i][j] = 1 if path j is selected for tails.get(i) is selected, 0 else.
    private IloNumVar[] d; // d[i] >= 0 is the total delay of leg i.
    private IloNumVar v;

    private IloRange[] legCoverConstraints;
    private IloRange[] onePathPerTailConstraints;
    private IloRange[] legDelayLinkConstraints;
    private IloRange[][] boundConstraints;
    private IloRange riskConstraint;

    private IloLinearNumExpr[] tailCoverExprs;
    private IloLinearNumExpr[] legCoverExprs;
    private IloLinearNumExpr[] delayExprs;
    private double[] delayRHS;

    public SubModelBuilder(ArrayList<Leg> legs, ArrayList<Tail> tails, HashMap<Integer, ArrayList<Path>> paths,
                           IloCplex cplex) throws IloException {
        this.legs = legs;
        numLegs = legs.size();
        this.tails = tails;
        numTails = tails.size();
        this.paths = paths;
        this.cplex = cplex;

        // initiailize containers
        y = new IloNumVar[numTails][];
        for (int i = 0; i < numTails; i++)
            y[i] = new IloNumVar[paths.get(tails.get(i).getId()).size()];

        d = new IloNumVar[numLegs];

        tailCoverExprs = new IloLinearNumExpr[numTails];
        legCoverExprs = new IloLinearNumExpr[numLegs];
        delayExprs = new IloLinearNumExpr[numLegs];
        delayRHS = new double[numLegs];
        Arrays.fill(delayRHS, Constants.OTP_TIME_LIMIT_IN_MINUTES);

        legCoverConstraints = new IloRange[numLegs]; // leg coverage
        onePathPerTailConstraints = new IloRange[numTails]; // tail coverage
        legDelayLinkConstraints = new IloRange[numLegs]; // delay constraints
        boundConstraints = new IloRange[numTails][];
        for (int i = 0; i < numTails; i++) {
            tailCoverExprs[i] = cplex.linearNumExpr();
            boundConstraints[i] = new IloRange[paths.get(tails.get(i).getId()).size()];
        }
    }

    public void buildObjective(IloLinearNumExpr objExpr, Double probability) throws IloException {
        for (int i = 0; i < numLegs; i++) {
            Leg leg = legs.get(i);
            d[i] = cplex.numVar(0, Double.MAX_VALUE, "d_" + leg.getId());

            legCoverExprs[i] = cplex.linearNumExpr();
            delayExprs[i] = cplex.linearNumExpr();
            delayExprs[i].addTerm(d[i], -1.0);

            if (probability != null)
                objExpr.addTerm(d[i], probability * leg.getDelayCostPerMin());
            else
                objExpr.addTerm(d[i], leg.getDelayCostPerMin());
        }

        if (Parameters.isExpectedExcess()) {
            v = cplex.numVar(0, Double.MAX_VALUE, "v");
            if (probability != null)
                objExpr.addTerm(v, probability * Parameters.getRho());
            else
                objExpr.addTerm(v, Parameters.getRho());
        }
    }

    public void addPathVarsToConstraints() throws IloException {
        for (int i = 0; i < numTails; i++) {
            for (int j = 0; j < y[i].length; j++) {
                y[i][j] = cplex.numVar(0, Double.MAX_VALUE, "y_" + tails.get(i).getId() + "_" + j);

                Tail tail = tails.get(i);
                tailCoverExprs[tail.getIndex()].addTerm(y[i][j], 1.0);

                ArrayList<Leg> pathLegs = paths.get(tail.getId()).get(j).getLegs();
                ArrayList<Integer> delayTimes = paths.get(tail.getId()).get(j).getDelayTimesInMin();

                for (int k = 0; k < pathLegs.size(); ++k) {
                    Leg pathLeg = pathLegs.get(k);
                    legCoverExprs[pathLeg.getIndex()].addTerm(y[i][j], 1.0);

                    Integer delayTime = delayTimes.get(k);
                    if (delayTime > 0)
                        delayExprs[pathLeg.getIndex()].addTerm(y[i][j], delayTime);
                }
            }
        }
    }

    public void updateModelWithRescheduleValues(int[] reschedules) throws IloException {
        for (int i = 0; i < numLegs; i++)
            if (reschedules[i] > 0)
                delayRHS[i] += reschedules[i];

        if (Parameters.isExpectedExcess()) {
            double xVal = 0;
            for (int i = 0; i < numLegs; ++i)
                if (reschedules[i] > 0)
                    xVal += reschedules[i] * legs.get(i).getRescheduleCostPerMin();

            IloLinearNumExpr riskExpr = cplex.linearNumExpr();
            for (int i = 0; i < numLegs; i++)
                riskExpr.addTerm(d[i], legs.get(i).getDelayCostPerMin());

            riskExpr.addTerm(v, -1);
            riskConstraint = cplex.addLe(riskExpr, Parameters.getExcessTarget() - xVal, "risk");
        }
    }

    public void updateModelWithFirstStageVars(IloIntVar[][] x) throws IloException {
        int[] durations = Parameters.getDurations();
        for (int i = 0; i < x.length; ++i)
            for (int j = 0; j < numLegs; ++j)
                delayExprs[j].addTerm(x[i][j], -durations[i]);

        if (Parameters.isExpectedExcess()) {
            IloLinearNumExpr riskExpr = cplex.linearNumExpr();
            for (int i = 0; i < x.length; ++i)
                for (int j = 0; j < x[i].length; ++j)
                    riskExpr.addTerm(x[i][j],  durations[i] * legs.get(j).getRescheduleCostPerMin());

            for (int i = 0; i < numLegs; i++)
                riskExpr.addTerm(d[i], legs.get(i).getDelayCostPerMin());

            riskExpr.addTerm(v, -1);
            riskConstraint = cplex.addLe(riskExpr, Parameters.getExcessTarget(), "risk");
        }
    }

    public void addConstraintsToModel() throws IloException {
        for (int i = 0; i < numTails; ++i)
            onePathPerTailConstraints[i] = cplex.addEq(
                    tailCoverExprs[i], 1.0, "tail_" + i + "_" + tails.get(i).getId());

        for (int i = 0; i < numLegs; ++i) {
            legCoverConstraints[i] = cplex.addEq(
                    legCoverExprs[i], 1.0, "leg_" + i + "_" + legs.get(i).getId());
        }

        for (int i = 0; i < numLegs; ++i) {
            legDelayLinkConstraints[i] = cplex.addLe(
                    delayExprs[i], delayRHS[i], "delay_" + i + "_" + legs.get(i).getId());
        }

        for (int i = 0; i < numTails; i++) {
            for (int j = 0; j < y[i].length; j++) {
                String name = "bound_" + tails.get(i).getId() + "_j";
                boundConstraints[i][j] = cplex.addLe(y[i][j], 1, name);
            }
        }
    }

    public void changePathVarsToInts() throws IloException {
        for (int i = 0; i < tails.size(); ++i)
            cplex.add(cplex.conversion(y[i], IloNumVarType.Int));
    }

    public double[] getdValues() throws IloException {
        return cplex.getValues(d);
    }

    public double[][] getyValues() throws IloException {
        double[][] yValues = new double[tails.size()][];
        for (int i = 0; i < tails.size(); ++i)
            yValues[i] = cplex.getValues(y[i]);

        return yValues;
    }

    public double[] getDualsLeg() throws IloException {
        return cplex.getDuals(legCoverConstraints);
    }

    public double[] getDualsTail() throws IloException {
        return cplex.getDuals(onePathPerTailConstraints);
    }

    public double[] getDualsDelay() throws IloException {
        return cplex.getDuals(legDelayLinkConstraints);
    }

    public double[][] getDualsBound() throws IloException {
        double[][] dualsBound = new double[numTails][];
        for (int i = 0; i < boundConstraints.length; i++)
            dualsBound[i] = cplex.getDuals(boundConstraints[i]);

        return dualsBound;
    }

    public double getDualRisk() throws IloException {
        return cplex.getDual(riskConstraint);
    }
}
