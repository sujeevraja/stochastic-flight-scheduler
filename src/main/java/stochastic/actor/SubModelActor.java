package stochastic.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import stochastic.solver.SubSolverRunnable;

public class SubModelActor extends AbstractActor {
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
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
        log.info("sub model actor stopped");
    }

    private void handle(SolveModel solveModel) {
        SubSolverRunnable subSolverRunnable = solveModel.subSolverRunnable;
        log.info("starting to solve sub model for iteration " +
            subSolverRunnable.getIter() + " scenario " + subSolverRunnable.getScenarioNum());

        subSolverRunnable.setCplex(cplex);
        subSolverRunnable.run();
        BendersDataHolder.UpdateCut updateCut = new BendersDataHolder.UpdateCut(
            subSolverRunnable.getCutNum(),
            subSolverRunnable.getAlpha(),
            subSolverRunnable.getObjValue(),
            subSolverRunnable.getProbability(),
            subSolverRunnable.getDualsDelay(),
            subSolverRunnable.getDualRisk());

        log.info("finished solving sub model for iteration " + subSolverRunnable.getIter() +
            " scenario " + subSolverRunnable.getScenarioNum());
        bendersDataHolder.tell(updateCut, getSelf());
    }
}

