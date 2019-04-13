package stochastic.delay;

import stochastic.domain.Tail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

public class TestDelayGenerator extends FirstFlightDelayGenerator {
    /**
     * Class that generates specified delays for the first flight of each tail.
     */
    public TestDelayGenerator(int numLegs, ArrayList<Tail> tails) {
        super(numLegs, tails, null);
    }

    @Override
    public Scenario[] generateScenarios(int numScenarios) {
        ArrayList<Integer> delays;
        ArrayList<Double> probabilities;

        // delays = new ArrayList<>(Collections.singletonList(45));
        // probabilities = new ArrayList<>(Collections.singletonList(1.0));

        // delays = new ArrayList<>(Arrays.asList(45, 60));
        // probabilities = new ArrayList<>(Arrays.asList(0.5, 0.5));

        // delays = new ArrayList<>(Arrays.asList(22, 23, 30, 32, 33, 34, 36, 46, 52));
        // probabilities = new ArrayList<>(Arrays.asList(0.1, 0.1, 0.1, 0.2, 0.1, 0.1, 0.1, 0.1, 0.1));

        delays = new ArrayList<>(Arrays.asList(33, 34, 36, 46, 52));
        probabilities = new ArrayList<>(Arrays.asList(0.2, 0.2, 0.2, 0.2, 0.2));

        logScenarioDelays(delays, probabilities);

        // generate scenarios with flight delay map.
        Scenario[] scenarios = new Scenario[delays.size()];
        for (int i = 0; i < delays.size(); ++i) {
            int[] flightDelays = generateFlightDelays(delays.get(i));
            scenarios[i] = new Scenario(probabilities.get(i), flightDelays);
        }

        return scenarios;
    }
}
