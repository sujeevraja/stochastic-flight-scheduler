package stochastic.solver;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.model.SubModelBuilder;
import stochastic.network.Path;
import stochastic.registry.Parameters;
import stochastic.utility.OptException;

import java.util.ArrayList;
import java.util.HashMap;

public class SubSolver {
    /**
     * SubSolver solves the second-stage problem of minimizing excess recourse delays using a given set of paths.
     */
    private static final Logger logger = LogManager.getLogger(SubSolver.class);
    private int scenarioNum;
    private ArrayList<Tail> tails;
    private ArrayList<Leg> legs;
    private int[] reschedules;  // reschedules[i] is the first-stage reschedule chosen for legs[i].
    private boolean solveAsMIP;

    private IloCplex cplex;
    private SubModelBuilder subModelBuilder;

    // Solution info
    private double objValue;
    private double[] dValues;
    private double[][] yValues;
    private double[] dualsTail;
    private double[] dualsLeg;
    private double[] dualsDelay;
    private double[][] dualsBound;
    private double dualRisk;

    SubSolver(int scenarioNum, ArrayList<Tail> tails, ArrayList<Leg> legs, int[] reschedules) {
        this.scenarioNum = scenarioNum;
        this.tails = tails;
        this.legs = legs;
        this.reschedules = reschedules;
        solveAsMIP = false;
    }

    void setSolveAsMIP() {
        this.solveAsMIP = true;
    }

    void constructSecondStage(HashMap<Integer, ArrayList<Path>> paths) throws OptException {
        try {
            cplex = new IloCplex();
            if (!Parameters.isDebugVerbose())
                cplex.setOut(null);

            subModelBuilder = new SubModelBuilder(scenarioNum, legs, tails, paths, cplex);

            IloLinearNumExpr objExpr = cplex.linearNumExpr();
            subModelBuilder.buildObjective(objExpr, null);
            cplex.addMinimize(objExpr);
            objExpr.clear();

            subModelBuilder.addPathVarsToConstraints();
            subModelBuilder.updateModelWithRescheduleValues(reschedules);
            subModelBuilder.addConstraintsToModel();
        } catch (IloException e) {
            logger.error(e.getStackTrace());
            throw new OptException("CPLEX error solving first stage MIP");
        }
    }

    public void solve() throws OptException {
        try {
            cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Dual);
            cplex.setParam(IloCplex.Param.Preprocessing.Presolve, false);

            if (solveAsMIP)
                subModelBuilder.changePathVarsToInts();

            cplex.solve();
            IloCplex.Status status = cplex.getStatus();
            if (status != IloCplex.Status.Optimal) {
                logger.error("sub-problem status: " + status);
                throw new OptException("optimal solution not found for sub-problem");
            }

            objValue = cplex.getObjValue();
        } catch (IloException ie) {
            logger.error(ie);
            throw new OptException("error solving subproblem");
        }
    }

    void collectSolution() throws IloException {
        dValues = subModelBuilder.getdValues();
        yValues = subModelBuilder.getyValues();
    }

    void collectDuals() throws OptException {
        try {
            dualsLeg = subModelBuilder.getDualsLeg();
            dualsTail = subModelBuilder.getDualsTail();
            dualsDelay = subModelBuilder.getDualsDelay();
            dualsBound = subModelBuilder.getDualsBound();
            if (Parameters.isExpectedExcess())
                dualRisk = subModelBuilder.getDualRisk();
        } catch (IloException ie) {
            logger.error(ie);
            throw new OptException("error when collecting duals from sub-problem");
        }
    }

    void writeLPFile(String name) throws IloException {
        cplex.exportModel(name);
    }

    void writeCplexSolution(String name) throws IloException {
        cplex.writeSolution(name);
    }

    void end() throws IloException {
        subModelBuilder = null;
        cplex.clearModel();
        cplex.endModel();
        cplex.end();
    }

    double getObjValue() {
        return objValue;
    }

    double[] getdValues() {
        return dValues;
    }

    double[][] getyValues() {
        return yValues;
    }

    double[] getDualsLeg() {
        return dualsLeg;
    }

    double[] getDualsTail() {
        return dualsTail;
    }

    double[] getDualsDelay() {
        return dualsDelay;
    }

    double[][] getDualsBound() {
        return dualsBound;
    }

    double getDualRisk() {
        return dualRisk;
    }
}
