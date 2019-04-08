package stochastic.solver;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.model.MasterModelBuilder;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.utility.OptException;

import java.util.ArrayList;

public class NewDepSolver {
    private final static Logger logger = LogManager.getLogger(MasterSolver.class);

    public NewDepSolver() {}

    public void solve(DataRegistry dataRegistry) throws OptException {
        try {
            IloCplex cplex = new IloCplex();
            if (!Parameters.isDebugVerbose())
                cplex.setOut(null);

            // master model
            ArrayList<Leg> legs = dataRegistry.getLegs();
            ArrayList<Tail> tails = dataRegistry.getTails();
            MasterModelBuilder masterModelBuilder = new MasterModelBuilder(legs, tails, cplex);

            masterModelBuilder.buildVariables();

            IloLinearNumExpr objExpr = masterModelBuilder.getObjExpr();
            masterModelBuilder.constructFirstStage();


            // sub models


            // solving
           cplex.addMinimize(objExpr);
           cplex.solve();
           double obj = cplex.getObjValue();
           logger.info("DEP objective: " + obj);
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("cplex error solving DEP model");
        }
    }
}
