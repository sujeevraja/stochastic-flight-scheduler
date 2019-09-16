package stochastic.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import stochastic.solver.SubSolverRunnable;

public class SubModelActor extends AbstractActor {
    private ActorRef bendersDataHolder;
    private IloCplex cplex;

    private SubModelActor(ActorRef bendersDataHolder, boolean disableOutput) throws IloException {
        this.bendersDataHolder = bendersDataHolder;
        cplex = new IloCplex();
        if (disableOutput)
            cplex.setOut(null);
    }

    static Props props(ActorRef bendersDataHolder, boolean disableOutput) {
        return Props.create(SubModelActor.class,
            () -> new SubModelActor(bendersDataHolder, disableOutput));
    }

    // SubModelActor messages
    static class SolveModel {
        private SubSolverRunnable subSolverRunnable;

        SolveModel(SubSolverRunnable subSolverRunnable) {
            this.subSolverRunnable = subSolverRunnable;
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(SolveModel.class, this::handle)
            .build();
    }

    @Override
    public void postStop() {
        cplex.end();
        cplex = null;
    }

    private void handle(SolveModel solveModel) {
        SubSolverRunnable subSolverRunnable = solveModel.subSolverRunnable;
        subSolverRunnable.setCplex(cplex);
        subSolverRunnable.run();
        BendersDataHolder.UpdateCut updateCut = new BendersDataHolder.UpdateCut(
            subSolverRunnable.getCutNum(),
            subSolverRunnable.getAlpha(),
            subSolverRunnable.getBeta(),
            subSolverRunnable.getObjValue(),
            subSolverRunnable.getProbability());
        bendersDataHolder.tell(updateCut, getSelf());
    }
}

