package stochastic.model;

import ilog.concert.*;
import ilog.cplex.IloCplex;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Path;
import stochastic.registry.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class SubModelBuilder {
    private String prefix;
    private ArrayList<Leg> legs;
    private int numLegs;
    private ArrayList<Tail> tails;
    private int numTails;
    private HashMap<Integer, ArrayList<Path>> paths;

    private IloCplex cplex;
    private IloNumVar[][] y; // y[i][j] = 1 if path j is selected for tails.get(i) is selected, 0 else.
    private IloNumVar[] z; // z[i] >= 0 is the total delay of leg i.
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

    public SubModelBuilder(int scenarioNum, ArrayList<Leg> legs, ArrayList<Tail> tails,
                           HashMap<Integer, ArrayList<Path>> paths, IloCplex cplex)
        throws IloException {
        prefix = "s" + scenarioNum + "_";
        this.legs = legs;
        numLegs = legs.size();
        this.tails = tails;
        numTails = tails.size();
        this.paths = paths;
        this.cplex = cplex;

        // initialize containers
        y = new IloNumVar[numTails][];
        for (int i = 0; i < numTails; i++)
            y[i] = new IloNumVar[paths.get(tails.get(i).getId()).size()];

        z = new IloNumVar[numLegs];

        tailCoverExprs = new IloLinearNumExpr[numTails];
        legCoverExprs = new IloLinearNumExpr[numLegs];
        delayExprs = new IloLinearNumExpr[numLegs];
        delayRHS = new double[numLegs];
        Arrays.fill(delayRHS, 0.0);

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
            z[i] = cplex.numVar(0, Double.MAX_VALUE);
            if (Parameters.isSetCplexNames())
                z[i].setName(prefix + "d_" + leg.getId());

            legCoverExprs[i] = cplex.linearNumExpr();
            delayExprs[i] = cplex.linearNumExpr();
            delayExprs[i].addTerm(z[i], -1.0);

            if (probability != null)
                objExpr.addTerm(z[i], probability * leg.getDelayCostPerMin());
            else
                objExpr.addTerm(z[i], leg.getDelayCostPerMin());
        }

        if (Parameters.isExpectedExcess()) {
            v = cplex.numVar(0, Double.MAX_VALUE);
            if (Parameters.isSetCplexNames())
                v.setName(prefix + "v");
            if (probability != null)
                objExpr.addTerm(v, probability * Parameters.getRiskAversion());
            else
                objExpr.addTerm(v, Parameters.getRiskAversion());
        }
    }

    public void addPathVarsToConstraints() throws IloException {
        for (int i = 0; i < numTails; i++) {
            for (int j = 0; j < y[i].length; j++) {
                y[i][j] = cplex.numVar(0, Double.MAX_VALUE);
                if (Parameters.isSetCplexNames())
                    y[i][j].setName(prefix + "y_" + tails.get(i).getId() + "_" + j);

                Tail tail = tails.get(i);
                tailCoverExprs[tail.getIndex()].addTerm(y[i][j], 1.0);

                Path path = paths.get(tail.getId()).get(j);
                ArrayList<Leg> pathLegs = path.getLegs();
                ArrayList<Integer> propagatedDelays = path.getPropagatedDelays();

                for (int k = 0; k < pathLegs.size(); ++k) {
                    Leg pathLeg = pathLegs.get(k);
                    legCoverExprs[pathLeg.getIndex()].addTerm(y[i][j], 1.0);

                    Integer propagatedDelay = propagatedDelays.get(k);
                    if (propagatedDelay > 0)
                        delayExprs[pathLeg.getIndex()].addTerm(y[i][j], propagatedDelay);
                }
            }
        }
    }

    /**
     * Updates the second stage model using fixed values of the first stage variables (x[i][j]).
     *
     * @param reschedules first-stage reschedule solution value for each leg
     * @throws IloException if cplex causes an issue
     */
    public void updateModelWithRescheduleValues(int[] reschedules) throws IloException {
        for (int i = 0; i < numLegs; i++)
            if (reschedules[i] > 0)
                delayRHS[i] += reschedules[i];

        if (Parameters.isExpectedExcess()) {
            double rhs = Parameters.getExcessTarget();
            IloLinearNumExpr riskExpr = cplex.linearNumExpr();

            for (int i = 0; i < numLegs; ++i) {
                riskExpr.addTerm(z[i], legs.get(i).getDelayCostPerMin());
                if (reschedules[i] > 0)
                    rhs -= reschedules[i] * legs.get(i).getRescheduleCostPerMin();
            }

            riskExpr.addTerm(v, -1);
            riskConstraint = cplex.addLe(riskExpr, rhs);
            if (Parameters.isSetCplexNames())
                riskConstraint.setName(prefix + "risk");
        }
    }

    /**
     * This function adds CPLEX first stage variables to second stage model constraints.
     *
     * @param x CPLEX variables for reschedules to select for each leg
     * @throws IloException if cplex causes an issue
     */
    public void updateModelWithFirstStageVars(IloNumVar[] x) throws IloException {
        for (int i = 0; i < x.length; ++i)
            delayExprs[i].addTerm(x[i], -1);

        if (Parameters.isExpectedExcess()) {
            IloLinearNumExpr riskExpr = cplex.linearNumExpr();
            for (int i = 0; i < x.length; ++i)
                riskExpr.addTerm(x[i], legs.get(i).getRescheduleCostPerMin());

            for (int i = 0; i < numLegs; i++)
                riskExpr.addTerm(z[i], legs.get(i).getDelayCostPerMin());

            riskExpr.addTerm(v, -1);
            riskConstraint = cplex.addLe(riskExpr, Parameters.getExcessTarget());
            if (Parameters.isSetCplexNames())
                riskConstraint.setName(prefix + "risk");
        }
    }

    public void addConstraintsToModel() throws IloException {
        for (int i = 0; i < numTails; ++i) {
            onePathPerTailConstraints[i] = cplex.addEq(tailCoverExprs[i], 1.0);
            if (Parameters.isSetCplexNames())
                onePathPerTailConstraints[i].setName(
                    prefix + "tail_" + i + "_" + tails.get(i).getId());
        }

        for (int i = 0; i < numLegs; ++i) {
            legCoverConstraints[i] = cplex.addEq(legCoverExprs[i], 1.0);
            if (Parameters.isSetCplexNames())
                legCoverConstraints[i].setName(prefix + "leg_" + i + "_" + legs.get(i).getId());
        }

        for (int i = 0; i < numLegs; ++i) {
            legDelayLinkConstraints[i] = cplex.addLe(delayExprs[i], delayRHS[i]);
            if (Parameters.isSetCplexNames())
                legDelayLinkConstraints[i].setName(
                    prefix + "delay_" + i + "_" + legs.get(i).getId());
        }

        for (int i = 0; i < numTails; i++) {
            for (int j = 0; j < y[i].length; j++) {
                boundConstraints[i][j] = cplex.addLe(y[i][j], 1);
                if (Parameters.isSetCplexNames())
                    boundConstraints[i][j].setName(
                        prefix + "bound_" + tails.get(i).getId() + "_" + j);
            }
        }
    }

    public void changePathVarsToInts() throws IloException {
        for (int i = 0; i < tails.size(); ++i)
            cplex.add(cplex.conversion(y[i], IloNumVarType.Int));
    }

    public double[] getzValues() throws IloException {
        return cplex.getValues(z);
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

    public void clearCplexObjects() {
        for (int i = 0; i < y.length; ++i) {
            Arrays.fill(y[i], null);
            y[i] = null;
        }
        y = null;

        Arrays.fill(z, null);
        z = null;

        v = null;

        Arrays.fill(legCoverConstraints, null);
        legCoverConstraints = null;

        Arrays.fill(onePathPerTailConstraints, null);
        onePathPerTailConstraints = null;

        Arrays.fill(legDelayLinkConstraints, null);
        legDelayLinkConstraints = null;

        for (int i = 0; i < boundConstraints.length; ++i) {
            Arrays.fill(boundConstraints[i], null);
            boundConstraints[i] = null;
        }
        boundConstraints = null;

        riskConstraint = null;

        Arrays.fill(tailCoverExprs, null);
        tailCoverExprs = null;

        Arrays.fill(legCoverExprs, null);
        legCoverExprs = null;

        Arrays.fill(delayExprs, null);
        delayExprs = null;
    }
}
