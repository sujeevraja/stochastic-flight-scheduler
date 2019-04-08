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
    private int[] durations;
    private int numScenarios;

    // CPLEX variables
    private IloCplex cplex;
    private IloObjective obj;
    private IloNumVar[] thetas;
    private MasterModelBuilder masterModelBuilder;

    private double objValue;
    private double[][] xValues;
    private int[] reschedules; // reschedules[i] is the selected reschedule duration for legs[i].
    private double rescheduleCost; // this is \sum_({p,f} c_f g_p x_{pf} and will be used for the Benders upper bound.
    private double[] thetaValues;

    private int numBendersCuts = 0; // Benders cut counter

    MasterSolver(ArrayList<Leg> legs, ArrayList<Tail> tails, int[] durations, int numScenarios) throws IloException {
        this.legs = legs;
        this.durations = durations;
        this.numScenarios = numScenarios;
        this.reschedules = new int[legs.size()];

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

    public void solve(int iter) throws IloException {
        cplex.solve();
        objValue = cplex.getObjValue();
        logger.info("master objective: " + objValue);
        xValues = masterModelBuilder.getxValues();
        Arrays.fill(reschedules, 0);

        rescheduleCost = 0;
        for (int i = 0; i < durations.length; i++) {
            for (int j = 0; j < legs.size(); ++j)
                if (xValues[i][j] >= Constants.EPS) {
                    reschedules[j] = durations[i];
                    rescheduleCost += legs.get(j).getRescheduleCostPerMin() * durations[i];
                }
        }

        if(iter > 0)
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

    void addBendersCut(BendersCut cutData, int thetaIndex) throws IloException {
        IloLinearNumExpr cons = cplex.linearNumExpr();

        double[][] beta = cutData.getBeta();
        IloIntVar[][] x = masterModelBuilder.getX();

        for (int i = 0; i < durations.length; i++)
            for (int j = 0; j < legs.size(); j++)
                if (Math.abs(beta[i][j]) >= Constants.EPS)
                    cons.addTerm(x[i][j], beta[i][j]);

        cons.addTerm(thetas[thetaIndex], 1);

        double alpha = cutData.getAlpha();
        double rhs = Math.abs(alpha) >= Constants.EPS ? alpha : 0.0;
        IloRange r = cplex.addGe(cons, rhs);
        r.setName("benders_cut_" + numBendersCuts);
        ++numBendersCuts;
    }

    double getObjValue() {
        return objValue;
    }

    double[][] getxValues() {
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

    int getNumBendersCuts() {
        return numBendersCuts;
    }
}
