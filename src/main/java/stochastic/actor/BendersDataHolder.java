package stochastic.actor;

import akka.actor.AbstractActor;
import akka.actor.Props;
import stochastic.solver.BendersData;

public class BendersDataHolder extends AbstractActor {
    private BendersData bendersData;
    private int numScenarios;
    private int numScenariosProcessed;

    private BendersDataHolder() {
        numScenarios = 0;
        numScenariosProcessed = 0;
    }

    static Props props() {
        return Props.create(BendersDataHolder.class, BendersDataHolder::new);
    }

    // messages
    static class InitBendersData {
        private final BendersData bendersData;
        private final int numScenarios;

        InitBendersData(BendersData bendersData, int numScenarios) {
            this.bendersData = bendersData;
            this.numScenarios = numScenarios;
        }
    }
    static class UpdateCut {
        private final int cutNum;
        private final double alpha;
        private final double objValue;
        private final double probability;
        private double[] dualsDelay;
        private Double dualRisk;

        UpdateCut(int cutNum, double alpha, double objValue, double probability,
                  double[] dualsDelay, Double dualsRisk) {
            this.cutNum = cutNum;
            this.alpha = alpha;
            this.objValue = objValue;
            this.probability = probability;
            this.dualsDelay = dualsDelay;
            this.dualRisk = dualsRisk;
        }
    }
    static class Done {}
    static class GetBendersData {}


    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(InitBendersData.class, this::handle)
            .match(UpdateCut.class, this::handle)
            .match(Done.class, __ -> replyToDoneQuestion())
            .match(GetBendersData.class, __ -> sendBendersData())
            .build();
    }

    private void handle(InitBendersData initBendersData) {
        this.bendersData = initBendersData.bendersData;
        this.numScenarios = initBendersData.numScenarios;
        numScenariosProcessed = 0;
        getSender().tell(true, getSelf());
    }

    private void handle(UpdateCut updateCut) {
        bendersData.updateAlpha(updateCut.cutNum, updateCut.alpha, updateCut.probability);
        bendersData.updateBeta(updateCut.cutNum, updateCut.dualsDelay, updateCut.probability,
            updateCut.dualRisk);
        bendersData.setUpperBound(bendersData.getUpperBound() +
            (updateCut.objValue * updateCut.probability));
        ++numScenariosProcessed;
    }

    private void replyToDoneQuestion() {
        getSender().tell(numScenariosProcessed == numScenarios, getSelf());
    }

    private void sendBendersData() {
        getSender().tell(bendersData, getSelf());
    }
}
