package com.stochastic.solver;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Path;
import com.stochastic.utility.OptException;
import java.util.ArrayList;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Solver {
    /**
     * Class that is used to solve a set packing model with CPLEX using a given
     * set of paths.
     */
    private final static Logger logger = LogManager.getLogger(Solver.class);
    private final static double eps = 1.0e-5;
    private static ArrayList<Path> paths;
    private static ArrayList<Leg> legs;
    private static ArrayList<Tail> tails;
    private static ArrayList<Integer> durations;
    private static Integer numScenarios;
    
	private static double lb = 0;
	private static double ub = 0;
    
    public static void init(ArrayList<Path> paths, ArrayList<Leg> legs, ArrayList<Tail> tails,
                            ArrayList<Integer> durations, Integer numScenarios) {
    	Solver.paths = paths;
    	Solver.legs = legs;
    	Solver.tails = tails;
    	Solver.durations = durations;
    	Solver.numScenarios = numScenarios;
    }    
    
    public static void algorithm() throws OptException {
        MasterSolver.MasterSolverInit(paths, legs, tails, durations);
        MasterSolver.constructFirstStage();
        MasterSolver.writeLPFile("ma", 0);
        MasterSolver.solve();
        MasterSolver.addColumn();
        MasterSolver.writeLPFile("ma1", 0);

        double lBound = 0;
        double uBound = Double.MAX_VALUE;
        int iter = 0;
        lBound = MasterSolver.getObjValue();

        System.out.println();
        System.out.println("Algorithm Starts: ");

        //labelling algorithm duration - lgnormal (parameters, set of legs) -> scenarios -> set of paths

        do {
            // starts here
            SubSolverWrapper.SubSolverWrapperInit(paths, legs, tails, durations, MasterSolver.getxValues(),
                    numScenarios);
            new SubSolverWrapper().buildSubModel();

            MasterSolver.constructBendersCut(SubSolverWrapper.getAlphaValue(), SubSolverWrapper.getBetaValue());

            MasterSolver.writeLPFile("",iter);
            MasterSolver.solve();
            MasterSolver.writeLPFile("ma1", iter);

            lBound = MasterSolver.getObjValue();

            if(SubSolverWrapper.getuBound() < uBound)
                uBound = SubSolverWrapper.getuBound();

            iter++;

            System.out.println("LB: " + lBound + " UB: " + uBound + " Iter: " + iter);
            // ends here
        } while(uBound - lBound > 0.001); // && (System.currentTimeMillis() - Optimizer.stTime)/1000 < Optimizer.runTime); // && iter < 10);

        logger.info("Algorithm ends.");

        lb = lBound;
        ub = uBound;
    }
}
