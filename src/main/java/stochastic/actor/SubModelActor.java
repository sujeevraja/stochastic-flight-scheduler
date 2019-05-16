package stochastic.actor;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import ilog.concert.IloException;
import ilog.cplex.IloCplex;

public class SubModelActor extends AbstractActor {
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private IloCplex cplex;

    static Props props(boolean disableOutput) {
        return Props.create(SubModelActor.class,
            () -> new SubModelActor(disableOutput));
    }

    SubModelActor(boolean disableOutput) throws IloException {
        cplex = new IloCplex();
        if (disableOutput)
            cplex.setOut(null);
    }

    // SubModelActor messages
    public static class SolveModel {
        private final String message;

        public SolveModel(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
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
        getSender().tell("reply from SubModelActor", getSelf());
    }
}

