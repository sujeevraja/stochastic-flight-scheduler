package stochastic.actor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.routing.RoundRobinPool;
import akka.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import stochastic.utility.OptException;

import java.util.concurrent.TimeUnit;

public class ActorManager {
    private final static Logger logger = LogManager.getLogger(ActorManager.class);
    private final ActorSystem actorSystem;
    private ActorRef router;

    public ActorManager() {
        actorSystem = ActorSystem.create("benders");
    }

    public final void createSubModelActors(int numThreads, boolean disableOutput) {
        router = actorSystem.actorOf(
            new RoundRobinPool(numThreads).props(SubModelActor.props(disableOutput)));
    }

    public final void solveModel() throws OptException {
        Timeout timeout = new Timeout(5, TimeUnit.SECONDS);
        Future<Object> future = Patterns.ask(router, new SubModelActor.SolveModel("test"), timeout);
        try {
            String result = (String) Await.result(future, timeout.duration());
            logger.info(result);
        } catch (Exception ex) {
            logger.error(ex);
            throw new OptException("unknown exception waiting for actor result");
        }

        // router.tell(new SubModelActor.SolveModel("test"), ActorRef.noSender());
        // router.tell(new SubModelActor.SolveModel("test"), ActorRef.noSender());
    }

    public void end() {
        actorSystem.terminate();
    }
}
