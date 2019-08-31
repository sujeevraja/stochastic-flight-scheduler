package stochastic.solver;

import ilog.concert.*;
import ilog.cplex.IloCplex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.delay.Scenario;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.main.ModelStats;
import stochastic.output.RescheduleSolution;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.Constants;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;

import static java.lang.Math.max;

public class NaiveSolver {
    /**
     * This class builds a naive rescheduling solution (i.e. solution to Benders first stage).
     * <p>
     * For each leg with a primary delay, it reschedules the leg by the biggest amount less than or equal to the delay.
     * These delays are then propagated downstream in the original routes.
     */
    private final static Logger logger = LogManager.getLogger(NaiveSolver.class);
    private DataRegistry dataRegistry;
    private ArrayList<Leg> legs;
    private double[] expectedDelays;
    private ModelStats modelStats;
    private RescheduleSolution finalRescheduleSolution;
    private double solutionTime;

    // CPLEX containers
    private IloCplex cplex;
    private IloNumVar[] x; // x[i] is a decision variable for reschedule amount of leg i.
    private IloNumVar[] v; // v[i] is a decision variable for delay amount of leg i.

    public NaiveSolver(DataRegistry dataRegistry) {
        this.dataRegistry = dataRegistry;
        this.legs = dataRegistry.getLegs();

        final int numLegs = dataRegistry.getLegs().size();
        expectedDelays = new double[numLegs];
        Arrays.fill(expectedDelays, 0.0);
    }

    public ModelStats getModelStats() {
        return modelStats;
    }

    public RescheduleSolution getFinalRescheduleSolution() {
        return finalRescheduleSolution;
    }

    public double getSolutionTime() {
        return solutionTime;
    }

    public void solve() throws IloException {
        buildAveragePrimaryDelays();
        solveModel();
    }

    private void buildAveragePrimaryDelays() {
        Scenario[] scenarios = dataRegistry.getDelayScenarios();
        for (Scenario scenario : scenarios) {
            int[] primaryDelays = scenario.getPrimaryDelays();
            for (int i = 0; i < primaryDelays.length; ++i)
                if (primaryDelays[i] > 0)
                    expectedDelays[i] += scenario.getProbability() * primaryDelays[i];
        }
    }

    private void solveModel() throws IloException {
        cplex = new IloCplex();
        cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, Constants.CPLEX_MIP_GAP);
        if (!Parameters.isDebugVerbose())
            cplex.setOut(null);

        ArrayList<Leg> legs = dataRegistry.getLegs();
        x = new IloIntVar[legs.size()];
        v = new IloIntVar[legs.size()];

        addObjective();
        for (Tail tail : dataRegistry.getTails()) {
            ArrayList<Leg> tailLegs = tail.getOrigSchedule();
            if (tailLegs.size() > 1)
                addOriginalRoutingConstraints(tailLegs);
        }
        addBudgetConstraint();

        // Solve model and extract solution
        if (Parameters.isDebugVerbose())
            cplex.exportModel("logs/naive_model.lp");

        Instant start = Instant.now();
        cplex.solve();
        solutionTime = Duration.between(start, Instant.now()).toMinutes() / 60.0;

        if (Parameters.isDebugVerbose())
            cplex.writeSolution("logs/naive_solution.xml");

        storeSolution();
        cplex.clearModel();
        cplex.endModel();
        cplex.end();
    }

    private void addObjective() throws IloException {
        IloLinearNumExpr objExpr = cplex.linearNumExpr();
        for (int j = 0; j < legs.size(); ++j) {
            Leg leg = legs.get(j);
            v[j] = cplex.numVar(0, Double.MAX_VALUE);
            if (Parameters.isSetCplexNames())
                v[j].setName("v_" + leg.getId());
            objExpr.addTerm(v[j], leg.getDelayCostPerMin());

            x[j] = cplex.numVar(0, Parameters.getFlightRescheduleBound(), IloNumVarType.Int);
            if (Parameters.isSetCplexNames())
                x[j].setName("x_" + legs.get(j).getId());
            objExpr.addTerm(x[j], leg.getRescheduleCostPerMin());
        }
        cplex.addMinimize(objExpr);
    }

    private void addOriginalRoutingConstraints(ArrayList<Leg> originalRoute) throws IloException {
        double propagatedDelay = 0;
        for (int i = 0; i < originalRoute.size(); ++i) {
            Leg currLeg = originalRoute.get(i);
            addPropagatedDelayConstraint(currLeg, propagatedDelay);

            if (i < originalRoute.size() - 1) {
                Leg nextLeg = originalRoute.get(i + 1);
                int slack = (int) (nextLeg.getDepTime() - currLeg.getArrTime());
                slack -= currLeg.getTurnTimeInMin();
                addConnectivityConstraint(currLeg, nextLeg, slack);
                propagatedDelay += max(expectedDelays[i] - slack, 0);
            }
        }
    }

    private void addBudgetConstraint() throws IloException {
        IloLinearNumExpr budgetExpr = cplex.linearNumExpr();
        for (int j = 0; j < legs.size(); ++j)
            budgetExpr.addTerm(x[j], 1);

        IloRange cons = cplex.addLe(budgetExpr, dataRegistry.getRescheduleTimeBudget());
        if (Parameters.isSetCplexNames())
            cons.setName("reschedule_time_budget");
    }

    /**
     * Adds constraints of the form x_i <= slack_{i,j} + x_j for each flight connection (i,j) in
     * the original routing.
     */
    private void addConnectivityConstraint(
            Leg currLeg, Leg nextLeg, int slack) throws IloException {
        IloLinearNumExpr expr = cplex.linearNumExpr();
        expr.addTerm(x[currLeg.getIndex()], 1);
        expr.addTerm(x[nextLeg.getIndex()], -1);
        IloRange cons = cplex.addLe(expr, slack);
        if (Parameters.isSetCplexNames())
            cons.setName("connect_" + currLeg.getId() + "_" + nextLeg.getId());
    }

    /**
     * Adds constraints of the form p_f - x_f <= v_f for each flight f where
     *   p_f is the propagated delay based on average primary delays in the original routing.
     *   x_f is the reschedule time.
     *   v_f is the excess delay time to protect time connectivity.
     */
    private void addPropagatedDelayConstraint(Leg leg, double propagatedDelay) throws IloException {
        int legIndex = leg.getIndex();
        IloLinearNumExpr linkExpr = cplex.linearNumExpr();
        linkExpr.addTerm(v[legIndex], 1.0);
        linkExpr.addTerm(x[legIndex], 1.0);
        IloRange linkCons = cplex.addGe(linkExpr, propagatedDelay);
        if (Parameters.isSetCplexNames())
            linkCons.setName("delay_reschedule_link_" + leg.getId());
    }

    private void storeSolution() throws IloException {
        final double objValue = cplex.getObjValue();
        logger.info("naive model CPLEX objective: " + objValue);
        modelStats = new ModelStats(cplex.getNrows(), cplex.getNcols(), cplex.getNNZs(), objValue);

        double[] vValues = cplex.getValues(v);
        double excessDelayPenalty = 0.0;
        for (int i = 0; i < legs.size(); ++i) {
            if (vValues[i] >= Constants.EPS)
                excessDelayPenalty += (vValues[i] * legs.get(i).getDelayCostPerMin());
        }
        logger.info("naive model excess delay penalty: " + excessDelayPenalty);

        double[] xValues = cplex.getValues(x);
        int[] reschedules = new int[legs.size()];
        double rescheduleCost = 0;

        for (int i = 0; i < reschedules.length; ++i) {
            if (xValues[i] >= Constants.EPS) {
                reschedules[i] = (int) Math.round(xValues[i]);
                rescheduleCost += (reschedules[i] * legs.get(i).getRescheduleCostPerMin());
            } else
                reschedules[i] = 0;
        }

        logger.info("naive model obj - excess delay: " + (objValue - excessDelayPenalty));
        logger.info("naive model reschedule cost (for validation): " + rescheduleCost);
        logger.info("naive model solution time (seconds): " + solutionTime);

        finalRescheduleSolution = new RescheduleSolution("naive",
            objValue - excessDelayPenalty, reschedules);
    }
}
