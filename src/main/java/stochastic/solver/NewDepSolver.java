package stochastic.solver;

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
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
            int[] durations = Parameters.getDurations();
            ArrayList<Leg> legs = dataRegistry.getLegs();
            IloIntVar[][] x = new IloIntVar[durations.length][legs.size()];

            ArrayList<Tail> tails = dataRegistry.getTails();
            MasterModelBuilder masterModelBuilder = new MasterModelBuilder(cplex, x, legs, tails);

            masterModelBuilder.buildVariables();

            IloLinearNumExpr objExpr = masterModelBuilder.getObjExpr();
            masterModelBuilder.constructFirstStage();


            // sub models


            // solving

           cplex.addMinimize(objExpr);
           cplex.solve();
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("cplex error solving DEP model");
        }
    }
}
