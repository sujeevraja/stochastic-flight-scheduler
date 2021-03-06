package stochastic.delay;

import stochastic.domain.Leg;
import stochastic.domain.Tail;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;

public class FirstFlightDelayGenerator implements DelayGenerator {
    /**
     * Class that adds the given delay value to the first flight of eath tail.
     */
    private int numLegs;
    private ArrayList<Tail> tails;
    private Sampler sampler;

    public FirstFlightDelayGenerator(int numLegs, ArrayList<Tail> tails) {
        this.numLegs = numLegs;
        this.tails = tails;
        this.sampler = new Sampler();
    }

    @Override
    public Scenario[] generateScenarios(int numSamples) {
        // generate sample delays.
        int[] delayTimes = new int[numSamples];
        for (int i = 0; i < numSamples; ++i)
            delayTimes[i] = sampler.sample();

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
            int[] flightDelays = generateFlightDelays(delays.get(i));
            scenarios[i] = new Scenario(probabilities.get(i), flightDelays);
        }

        return scenarios;
    }

    int[] generateFlightDelays(int delayTimeInMin) {
        int[] delays = new int[numLegs];
        Arrays.fill(delays, 0);

        for (Tail tail : tails) {
            ArrayList<Leg> tailLegs = tail.getOrigSchedule();
            if (!tailLegs.isEmpty()) {
                Leg leg = tailLegs.get(0);
                delays[leg.getIndex()] = delayTimeInMin;
            }
        }

        return delays;
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
