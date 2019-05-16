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
import stochastic.solver.BendersData;
import stochastic.solver.SubSolverRunnable;
import stochastic.utility.OptException;

import java.util.concurrent.TimeUnit;

public class ActorManager {
    private final static Logger logger = LogManager.getLogger(ActorManager.class);
    private final ActorSystem actorSystem;
    private ActorRef router;
    private ActorRef bendersDataHolder;

    public ActorManager() {
        actorSystem = ActorSystem.create("benders");
    }

    public final void createActors(int numThreads, boolean disableOutput) {
        router = actorSystem.actorOf(
            new RoundRobinPool(numThreads).props(SubModelActor.props(disableOutput)));
        bendersDataHolder = actorSystem.actorOf(BendersDataHolder.props());
    }

    public final void initBendersData(BendersData bendersData, int numScenarios)
            throws OptException{
        askAndWait(bendersDataHolder,
            new BendersDataHolder.InitBendersData(bendersData, numScenarios));
    }

    public final BendersData solveModels(SubSolverRunnable[] models) throws OptException {
        for (SubSolverRunnable model : models)
            router.tell(new SubModelActor.SolveModel(model), ActorRef.noSender());

        boolean done = false;
        while (!done) {
            try {
                Thread.sleep(100);
                done = askAndWait(bendersDataHolder, new BendersDataHolder.Done());
            } catch (InterruptedException ex) {
                logger.error(ex);
                throw new OptException("interruption when waiting for 2nd stage solution");
            }
        }

        return askAndWait(bendersDataHolder, new BendersDataHolder.GetBendersData());
    }

    public void end() {
        actorSystem.terminate();
    }

    @SuppressWarnings("unchecked")
    private static <T, M> T askAndWait(ActorRef r, M message) throws OptException {
        try {
            Timeout timeout = new Timeout(5, TimeUnit.SECONDS);
            Future<Object> future = Patterns.ask(r, message, timeout);
            return (T) Await.result(future, timeout.duration());
        } catch (Exception ex) {
            logger.error(ex);
            throw new OptException("error when querying BendersDataHolder for status");
        }
    }
}
