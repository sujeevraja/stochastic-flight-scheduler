package com.stochastic.solver;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.utility.CostUtility;
import com.stochastic.utility.OptException;
import ilog.concert.*;
import ilog.cplex.IloCplex;
import java.util.ArrayList;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SecondStageSolver {
    /**
     * Class that is used to solve a set packing model with CPLEX using a given
     * set of paths.
     */
    private final static Logger logger = LogManager.getLogger(SecondStageSolver.class);
    private final static double eps = 1.0e-5;
    private ArrayList<Path> paths;
    private ArrayList<Leg> legs;
    private ArrayList<Tail> tails;

    public SecondStageSolver(ArrayList<Path> paths, ArrayList<Leg> legs, ArrayList<Tail> tails) {
        this.paths = paths;
        this.legs = legs;
        this.tails = tails;
    }

    public void solveWithCPLEX() throws OptException {
        try {
            IloCplex cplex = new IloCplex();

            // Build containers for variables.
            final Integer numPaths = paths.size();
            IloIntVar[] pathVars = new IloIntVar[numPaths];
            IloLinearNumExpr objExpr = cplex.linearNumExpr();

            final Integer numLegs = legs.size();
            IloLinearNumExpr[] legCoverConstraints = new IloLinearNumExpr[numLegs];
            IloIntVar[] cancelVars = new IloIntVar[numLegs];

            final Integer numTails = tails.size();
            IloLinearNumExpr[] tailCoverConstraints = new IloLinearNumExpr[numTails];
            for(int i = 0; i < numTails; ++i)
                tailCoverConstraints[i] = cplex.linearNumExpr();

            // Add leg cancellation variables to objective and constraints.
            for(int i = 0; i < numLegs; ++i) {
                legCoverConstraints[i] = cplex.linearNumExpr();

                final String varName = "y_" + legs.get(i).getId();
                cancelVars[i] = cplex.intVar(0, 1, varName);

                objExpr.addTerm(-1.0 * CostUtility.getLegCancelCost(), cancelVars[i]);
                legCoverConstraints[i].addTerm(1.0, cancelVars[i]);
            }

            // Build path variables, add them to leg/path coverage constraints.
            for(int i = 0; i < numPaths; ++i) {
                Path path = paths.get(i);

                String varName = "x_" + path.getTail().getId() + "_" + Integer.toString(i);
                pathVars[i] = cplex.intVar(0, 1, varName);

                objExpr.addTerm(path.getCost(), pathVars[i]);
                tailCoverConstraints[path.getTail().getIndex()].addTerm(1.0, pathVars[i]);

                for(Leg leg : path.getLegs())
                    legCoverConstraints[leg.getIndex()].addTerm(1.0, pathVars[i]);
            }

            // Build model
            cplex.addMaximize(objExpr);
            for(int i = 0; i < numLegs; ++i)
                cplex.addEq(legCoverConstraints[i], 1.0, "leg_" + legs.get(i).getId());
            for(int i = 0; i < numTails; ++i)
                cplex.addLe(tailCoverConstraints[i], 1.0, "tail_" + tails.get(i).getId());

            // Solve model and print solution
            cplex.exportModel("model.lp");
            cplex.solve();
            logger.info("second stage solution status: " + cplex.getStatus());
            logger.info("second stage objective value: " + cplex.getObjValue());

            double[] cancelVals = cplex.getValues(cancelVars);
            for(int i = 0; i < numLegs; ++i) {
                if(cancelVals[i] > eps)
                    logger.info("cancelled: " + legs.get(i));
            }

            double[] pathVals = cplex.getValues(pathVars);
            for(int i = 0; i < numPaths; ++i) {
                if(pathVals[i] > eps)
                    paths.get(i).print();
            }

        } catch (IloException e) {
            logger.error(e);
            throw new OptException("CPLEX error solving second stage MIP");
        }
    }
}
