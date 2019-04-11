package stochastic.solver;

import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.model.MasterModelBuilder;
import stochastic.registry.Parameters;
import stochastic.utility.Constants;
import ilog.concert.*;
import ilog.cplex.IloCplex;

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
    private int numScenarios;

    // CPLEX variables
    private IloCplex cplex;
    private IloObjective obj;
    private IloNumVar[] thetas;
    private MasterModelBuilder masterModelBuilder;

    private double objValue;
    private double[] xValues;
    private int[] reschedules; // reschedules[i] is the selected reschedule duration for legs[i].
    private double rescheduleCost; // this is \sum_({p,f} c_f g_p x_{pf} and will be used for the Benders upper bound.
    private double[] thetaValues;

    MasterSolver(ArrayList<Leg> legs, ArrayList<Tail> tails, int numScenarios) throws IloException {
        this.legs = legs;
        this.numScenarios = numScenarios;

        cplex = new IloCplex();
        if (!Parameters.isDebugVerbose())
            cplex.setOut(null);

        masterModelBuilder = new MasterModelBuilder(legs, tails, cplex);
    }

    void constructFirstStage() throws IloException {
        masterModelBuilder.buildVariables();
        obj = cplex.addMinimize(masterModelBuilder.getObjExpr());
        masterModelBuilder.constructFirstStage();
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

    void setInitialSolution(double rescheduleCost, int[] reschedules) {
        this.rescheduleCost = rescheduleCost;
        this.reschedules = reschedules.clone();
        xValues = Arrays.stream(reschedules).asDoubleStream().toArray();
        thetaValues = null;
    }

    void initInitialSolution() {
        rescheduleCost = 0;
        reschedules = new int[legs.size()];
        Arrays.fill(reschedules, 0);
        xValues = Arrays.stream(reschedules).asDoubleStream().toArray();
        thetaValues = null;
    }

    public void solve() throws IloException {
        cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0.001);
        cplex.solve();
        objValue = cplex.getObjValue();
        logger.info("master objective: " + objValue);
        xValues = masterModelBuilder.getxValues();
        Arrays.fill(reschedules, 0);

        rescheduleCost = 0;
        for (int j = 0; j < legs.size(); ++j)
            if (xValues[j] >= Constants.EPS) {
                reschedules[j] = (int) Math.round(xValues[j]);
                rescheduleCost += legs.get(j).getRescheduleCostPerMin() * reschedules[j];
            }

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

    void addBendersCut(BendersCut cutData, int thetaIndex, int cutIndex) throws IloException {
        IloLinearNumExpr cons = cplex.linearNumExpr();

        double[] beta = cutData.getBeta();
        IloNumVar[] x = masterModelBuilder.getX();

        for (int j = 0; j < legs.size(); j++)
            if (Math.abs(beta[j]) >= Constants.EPS)
                cons.addTerm(x[j], beta[j]);

        cons.addTerm(thetas[thetaIndex], 1);

        double alpha = cutData.getAlpha();
        double rhs = Math.abs(alpha) >= Constants.EPS ? alpha : 0.0;
        IloRange r = cplex.addGe(cons, rhs);
        r.setName("benders_cut_" + cutIndex);
    }

    double getObjValue() {
        return objValue;
    }

    double[] getxValues() {
        return xValues;
    }

    int[] getReschedules() {
        return reschedules;
    }

    double[] getThetaValues() {
        return thetaValues;
    }

    void end() {
        cplex.end();
    }
}
