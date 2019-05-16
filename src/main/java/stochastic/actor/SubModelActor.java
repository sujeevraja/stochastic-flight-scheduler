package stochastic.actor;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import stochastic.solver.SubSolverRunnable;

public class SubModelActor extends AbstractActor {
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private IloCplex cplex;

    private SubModelActor(boolean disableOutput) throws IloException {
        cplex = new IloCplex();
        if (disableOutput)
            cplex.setOut(null);
    }

    static Props props(boolean disableOutput) {
        return Props.create(SubModelActor.class,
            () -> new SubModelActor(disableOutput));
    }

    // SubModelActor messages
    public static class SolveModel {
        private SubSolverRunnable subSolverRunnable;

        public SolveModel(SubSolverRunnable subSolverRunnable) {
            this.subSolverRunnable = subSolverRunnable;
        }

        public SubSolverRunnable getSubSolverRunnable() {
            return subSolverRunnable;
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
        log.debug("received solveModel message");
        SubSolverRunnable subSolverRunnable = solveModel.getSubSolverRunnable();
        subSolverRunnable.setCplex(cplex);
        // subSolverRunnable.setBendersData(bendersData);
        subSolverRunnable.run();
        // getSender().tell("reply from SubModelActor", getSelf());
    }
}

