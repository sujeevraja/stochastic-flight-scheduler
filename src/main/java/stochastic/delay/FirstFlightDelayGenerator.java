package stochastic.delay;

import stochastic.domain.Leg;
import stochastic.domain.Tail;
import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.apache.commons.math3.distribution.LogNormalDistribution;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class FirstFlightDelayGenerator implements DelayGenerator {
    /**
     * Class that adds the given delay value to the first flight of eath tail.
     */
    private ArrayList<Tail> tails;
    private LogNormalDistribution distribution;

    public FirstFlightDelayGenerator(ArrayList<Tail> tails, LogNormalDistribution distribution) {
        this.tails = tails;
        this.distribution = distribution;
    }

    @Override
    public Scenario[] generateScenarios(int numSamples) {
        // generate sample delays.
        int[] delayTimes = new int[numSamples];
        for (int i = 0; i < numSamples; ++i)
            delayTimes[i] = (int) Math.round(distribution.sample());

        // round delays, aggregate them and corresponding probabilities.
        Arrays.sort(delayTimes);
        ArrayList<Integer> delays = new ArrayList<>();
        ArrayList<Double> probabilities = new ArrayList<>();

        DecimalFormat df = new DecimalFormat("##.##");
        df.setRoundingMode(RoundingMode.HALF_UP);

        final double baseProbability = 1.0 / numSamples;
        int numCopies = 1;

        delays.add(delayTimes[0]);
        int prevDelayTime = delayTimes[0];
        for (int i = 1; i < numSamples; ++i) {
            int delayTime = delayTimes[i];

            if (delayTime != prevDelayTime) {
                final double prob = Double.parseDouble(df.format(numCopies * baseProbability));
                probabilities.add(prob); // add probabilities for previous time.
                delays.add(delayTime); // add new delay time.
                numCopies = 1;
            } else
                numCopies++;

            prevDelayTime = delayTime;
        }
        probabilities.add(numCopies * baseProbability);

        logScenarioDelays(delays, probabilities);

        // generate scenarios with flight delay map.
        Scenario[] scenarios = new Scenario[delays.size()];
        for (int i = 0; i < delays.size(); ++i) {
            HashMap<Integer, Integer> flightDelays = generateFlightDelays(delays.get(i));
            scenarios[i] = new Scenario(probabilities.get(i), flightDelays);
        }

        return scenarios;
    }

    HashMap<Integer, Integer> generateFlightDelays(int delayTimeInMin) {
        HashMap<Integer, Integer> delayMap = new HashMap<>();

        for(Tail tail : tails) {
            ArrayList<Leg> tailLegs = tail.getOrigSchedule();
            if(!tailLegs.isEmpty()) {
                Leg leg = tailLegs.get(0);
                delayMap.put(leg.getIndex(), delayTimeInMin);
            }
        }

        return delayMap;
    }

    void logScenarioDelays(ArrayList<Integer> delays, ArrayList<Double> probabilities) {
        StringBuilder delayStr = new StringBuilder();
        StringBuilder probStr = new StringBuilder();
        delayStr.append("scenario delays:");
        probStr.append("scenario probabilities:");

        int numScenarios = delays.size();
        for (int i = 0; i < numScenarios; ++i) {
            delayStr.append(" ");
            delayStr.append(delays.get(i));
            probStr.append(" ");
            probStr.append(probabilities.get(i));
        }
        logger.info(delayStr);
        logger.info(probStr);
    }
}
