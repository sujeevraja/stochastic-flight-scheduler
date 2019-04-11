package stochastic.solver;

import stochastic.delay.Scenario;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.output.RescheduleSolution;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.Constants;
import ilog.concert.*;
import ilog.cplex.IloCplex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class NaiveSolver {
    /**
     * This class builds a naive rescheduling solution (i.e. solution to Benders first stage).
     *
     * For each leg with a primary delay, it reschedules the leg by the biggest amount less than or equal to the delay.
     * These delays are then propagated downstream in the original routes.
     */
    private final static Logger logger = LogManager.getLogger(NaiveSolver.class);
    private DataRegistry dataRegistry;
    private double[] expectedDelays;
    private RescheduleSolution finalRescheduleSolution;
    private double solutionTime;

    public NaiveSolver(DataRegistry dataRegistry) {
        this.dataRegistry = dataRegistry;

        final int numLegs = dataRegistry.getLegs().size();
        expectedDelays = new double[numLegs];
        Arrays.fill(expectedDelays, 0.0);
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
        for(Scenario scenario : scenarios) {
            HashMap<Integer, Integer> primaryDelays = scenario.getPrimaryDelays();
            for (Map.Entry<Integer, Integer> entry : primaryDelays.entrySet())
                expectedDelays[entry.getKey()] += scenario.getProbability() * entry.getValue();
        }
    }

    private void solveModel() throws IloException {
        IloCplex cplex = new IloCplex();
        if (!Parameters.isDebugVerbose())
            cplex.setOut(null);

        ArrayList<Leg> legs = dataRegistry.getLegs();
        IloNumVar[] v = new IloNumVar[legs.size()];
        IloNumVar[] x = new IloIntVar[legs.size()];

        // Add objective
        IloLinearNumExpr objExpr = cplex.linearNumExpr();
        for (int j = 0; j < legs.size(); ++j) {
            Leg leg = legs.get(j);
            v[j] = cplex.numVar(0, Double.MAX_VALUE, "v_" + leg.getId());
            objExpr.addTerm(v[j], leg.getDelayCostPerMin());

            String varName = "x_" + legs.get(j).getId();
            x[j] = cplex.numVar(0, Parameters.getFlightRescheduleBound(), IloNumVarType.Int, varName);
            objExpr.addTerm(x[j], leg.getRescheduleCostPerMin());
        }
        cplex.addMinimize(objExpr);

        // Add excess delay constraints
        for (int i = 0; i < legs.size(); ++i) {
            if (expectedDelays[i] <= Constants.EPS)
                continue;

            IloLinearNumExpr expr = cplex.linearNumExpr();
            expr.addTerm(v[i], 1.0);
            expr.addTerm(x[i], 1.0);
            cplex.addGe(expr, expectedDelays[i], "delay_reschedule_link_" + legs.get(i).getId());
        }

        // Add original routing constraints
        for(Tail tail : dataRegistry.getTails()) {
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
                cplex.addLe(cons, (double) rhs, "connect_" + currLeg.getId() + "_" + nextLeg.getId());
            }
        }

        // Add budget constraint
        IloLinearNumExpr budgetExpr = cplex.linearNumExpr();
        for (int j = 0; j < legs.size(); ++j)
            budgetExpr.addTerm(x[j], 1);

        cplex.addLe(budgetExpr, (double) Parameters.getRescheduleTimeBudget(), "reschedule_time_budget");

        if (Parameters.isDebugVerbose())
            cplex.exportModel("logs/naive_model.lp");

        // Solve model and extract solution
        Instant start = Instant.now();
        cplex.solve();
        solutionTime = Duration.between(start, Instant.now()).toMinutes() / 60.0;

        if (Parameters.isDebugVerbose())
            cplex.writeSolution("logs/naive_solution.xml");

        final double cplexObjValue = cplex.getObjValue();
        logger.info("naive model CPLEX objective: " + cplexObjValue);

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
            }
            else
                reschedules[i] = 0;
        }

        logger.info("naive model obj - excess delay: " + (cplexObjValue - excessDelayPenalty));
        logger.info("naive model reschedule cost (for validation): " + rescheduleCost);
        logger.info("naive model solution time (seconds): " + solutionTime);

        finalRescheduleSolution = new RescheduleSolution("naive_model",
                cplexObjValue - excessDelayPenalty, reschedules);
        cplex.end();
    }
}