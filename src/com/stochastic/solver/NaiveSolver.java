package com.stochastic.solver;

import com.stochastic.delay.Scenario;
import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.postopt.RescheduleSolution;
import com.stochastic.registry.DataRegistry;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.Constants;
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
        Instant start = Instant.now();
        buildAveragePrimaryDelays();
        solveModel();
        solutionTime = Duration.between(start, Instant.now()).toMinutes() / 60.0;
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
        int[] durations = Parameters.getDurations();
        IloNumVar[] v = new IloNumVar[dataRegistry.getLegs().size()];
        IloIntVar[][] x = new IloIntVar[durations.length][legs.size()];

        // Add objective
        IloLinearNumExpr objExpr = cplex.linearNumExpr();
        for (int j = 0; j < legs.size(); ++j) {
            Leg leg = legs.get(j);
            v[j] = cplex.numVar(0, Double.MAX_VALUE, "v_" + leg.getId());
            objExpr.addTerm(v[j], leg.getDelayCostPerMin());

            for (int i = 0; i < durations.length; ++i) {
                String varName = "x_" + durations[i] + "_" + legs.get(j).getId();
                x[i][j] = cplex.boolVar(varName);
                objExpr.addTerm(x[i][j], durations[i] * leg.getRescheduleCostPerMin());
            }
        }
        cplex.addMinimize(objExpr);

        // Add excess delay constraints
        for (int i = 0; i < legs.size(); ++i) {
            if (expectedDelays[i] <= Constants.EPS)
                continue;

            IloLinearNumExpr expr = cplex.linearNumExpr();
            expr.addTerm(v[i], 1.0);

            for (int j = 0; j < durations.length; ++j)
                expr.addTerm(x[j][i], durations[j]);

            cplex.addGe(expr, expectedDelays[i], "delay_reschedule_link_" + legs.get(i).getId());
        }

        // Add duration cover constraints
        for (int i = 0; i < legs.size(); i++) {
            IloLinearNumExpr cons = cplex.linearNumExpr();

            for (int j = 0; j < durations.length; j++)
                cons.addTerm(x[j][i], 1);

            cplex.addLe(cons, 1, "duration_cover_" + legs.get(i).getId());
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
                for (int j = 0; j < durations.length; j++) {
                    cons.addTerm(x[j][currLegIndex], durations[j]);
                    cons.addTerm(x[j][nextLegIndex], -durations[j]);
                }

                int rhs = (int) Duration.between(currLeg.getArrTime(), nextLeg.getDepTime()).toMinutes();
                rhs -= currLeg.getTurnTimeInMin();
                cplex.addLe(cons, (double) rhs, "connect_" + currLeg.getId() + "_" + nextLeg.getId());
            }
        }

        // Add budget constraint
        IloLinearNumExpr budgetExpr = cplex.linearNumExpr();
        for (int i = 0; i < durations.length; ++i)
            for (int j = 0; j < legs.size(); ++j)
                budgetExpr.addTerm(x[i][j], durations[i]);

        cplex.addLe(budgetExpr, (double) Parameters.getRescheduleTimeBudget(), "reschedule_time_budget");

        if (Parameters.isDebugVerbose())
            cplex.exportModel("logs/naive_model.lp");

        // Solve model and extract solution
        cplex.solve();

        if (Parameters.isDebugVerbose())
            cplex.writeSolution("logs/naive_solution.xml");

        final double cplexObjValue = cplex.getObjValue();
        logger.debug("naive model CPLEX objective: " + cplexObjValue);

        double[] vValues = cplex.getValues(v);
        double excessDelayPenalty = 0.0;
        for (int i = 0; i < legs.size(); ++i) {
            if (vValues[i] >= Constants.EPS)
                excessDelayPenalty += (vValues[i] * legs.get(i).getDelayCostPerMin());
        }
        logger.debug("excess delay penalty: " + excessDelayPenalty);

        double[][] xValues = new double[durations.length][legs.size()];
        int[] reschedules = new int[legs.size()];
        Arrays.fill(reschedules, 0);

        for (int i = 0; i < durations.length; i++) {
            xValues[i] = cplex.getValues(x[i]);
            for (int j = 0; j < legs.size(); ++j)
                if (xValues[i][j] >= Constants.EPS)
                    reschedules[j] = durations[i];
        }

        finalRescheduleSolution = new RescheduleSolution("naive_model",
                cplexObjValue - excessDelayPenalty, reschedules);
        cplex.end();
    }
}