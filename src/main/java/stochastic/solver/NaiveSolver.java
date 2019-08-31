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
    private double[] expectedDelays;
    private ModelStats modelStats;
    private RescheduleSolution finalRescheduleSolution;
    private double solutionTime;

    public NaiveSolver(DataRegistry dataRegistry) {
        this.dataRegistry = dataRegistry;

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
        IloCplex cplex = new IloCplex();
        cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, Constants.CPLEX_MIP_GAP);
        if (!Parameters.isDebugVerbose())
            cplex.setOut(null);

        ArrayList<Leg> legs = dataRegistry.getLegs();
        IloNumVar[] v = new IloNumVar[legs.size()];
        IloNumVar[] x = new IloIntVar[legs.size()];

        // Add objective
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

        // Add excess delay constraints
        /*
        for (int i = 0; i < legs.size(); ++i) {
            if (expectedDelays[i] <= Constants.EPS)
                continue;

            IloLinearNumExpr expr = cplex.linearNumExpr();
            expr.addTerm(v[i], 1.0);
            expr.addTerm(x[i], 1.0);
            IloRange cons = cplex.addGe(expr, expectedDelays[i]);
            if (Parameters.isSetCplexNames())
                cons.setName("delay_reschedule_link_" + legs.get(i).getId());
        }
         */

        // Add original routing constraints
        for (Tail tail : dataRegistry.getTails()) {
            ArrayList<Leg> tailLegs = tail.getOrigSchedule();
            if (tailLegs.size() <= 1)
                continue;

            double propagatedDelay = 0;
            for (int i = 0; i < tailLegs.size() - 1; ++i) {
                Leg currLeg = tailLegs.get(i);
                Leg nextLeg = tailLegs.get(i + 1);
                int currLegIndex = currLeg.getIndex();
                int nextLegIndex = nextLeg.getIndex();

                int slack = max((int) (nextLeg.getDepTime() - currLeg.getArrTime()) - currLeg.getTurnTimeInMin(), 0);

                // Add constraint to protect turn time in original connections.
                {
                    IloLinearNumExpr expr = cplex.linearNumExpr();
                    expr.addTerm(x[currLegIndex], 1);
                    expr.addTerm(x[nextLegIndex], -1);

                    // int rhs = (int) (nextLeg.getDepTime() - currLeg.getArrTime());
                    // rhs -= currLeg.getTurnTimeInMin();
                    // IloRange cons = cplex.addLe(expr, (double) rhs);
                    IloRange cons = cplex.addLe(expr, slack);
                    if (Parameters.isSetCplexNames())
                        cons.setName("connect_" + currLeg.getId() + "_" + nextLeg.getId());
                }

                // Add constraint to link delays, reschedules and excess delays.
                {
                    IloLinearNumExpr linkExpr = cplex.linearNumExpr();
                    linkExpr.addTerm(v[i], 1.0);
                    linkExpr.addTerm(x[i], 1.0);
                    // IloRange linkCons = cplex.addGe(linkExpr, expectedDelays[i] + propagatedDelay);
                    IloRange linkCons = cplex.addGe(linkExpr, propagatedDelay);
                    if (Parameters.isSetCplexNames())
                        linkCons.setName("delay_reschedule_link_" + legs.get(i).getId());
                    propagatedDelay += max(expectedDelays[i] - slack, 0);
                }
            }

            // Add linking constraint for last leg on route.
            int i = tailLegs.size() - 1;
            IloLinearNumExpr linkExpr = cplex.linearNumExpr();
            linkExpr.addTerm(v[i], 1.0);
            linkExpr.addTerm(x[i], 1.0);
            IloRange linkCons = cplex.addGe(linkExpr, expectedDelays[i] + propagatedDelay);
            if (Parameters.isSetCplexNames())
                linkCons.setName("delay_reschedule_link_" + legs.get(i).getId());
        }

        // Add budget constraint
        IloLinearNumExpr budgetExpr = cplex.linearNumExpr();
        for (int j = 0; j < legs.size(); ++j)
            budgetExpr.addTerm(x[j], 1);

        IloRange cons = cplex.addLe(budgetExpr, (double) dataRegistry.getRescheduleTimeBudget());
        if (Parameters.isSetCplexNames())
            cons.setName("reschedule_time_budget");

        if (Parameters.isDebugVerbose())
            cplex.exportModel("logs/naive_model.lp");

        // Solve model and extract solution
        Instant start = Instant.now();
        cplex.solve();
        solutionTime = Duration.between(start, Instant.now()).toMinutes() / 60.0;

        if (Parameters.isDebugVerbose())
            cplex.writeSolution("logs/naive_solution.xml");

        // store model stats
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
        cplex.clearModel();
        cplex.endModel();
        cplex.end();
    }
}
