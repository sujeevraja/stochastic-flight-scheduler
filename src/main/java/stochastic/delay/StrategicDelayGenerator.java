package stochastic.delay;

import stochastic.domain.Leg;
import stochastic.utility.Enums;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class StrategicDelayGenerator implements DelayGenerator {
    /**
     * StrategicDelayGenerator can be used to generate random primary delays for flights based on a specified distribution
     * and flight selection strategy.
     */
    private ArrayList<Leg> legs;
    private Enums.FlightPickStrategy flightPickStrategy;
    private Sampler sampler;

    public StrategicDelayGenerator(ArrayList<Leg> legs, Enums.FlightPickStrategy flightPickStrategy,
                                   Enums.DistributionType distributionType, double mean, double sd) {
        this.legs = legs;
        this.flightPickStrategy = flightPickStrategy;
        sampler = new Sampler(distributionType, mean, sd);
    }

    @Override
    public Scenario[] generateScenarios(int numSamples) {
        ArrayList<Leg> selectedLegs = selectLegs();
        final double probability = 1.0 / numSamples;
        Scenario[] scenarios = new Scenario[numSamples];

        for (int i = 0; i < numSamples; ++i) {
            int[] delays = new int[legs.size()];
            Arrays.fill(delays, 0);
            for (Leg leg : selectedLegs)
                delays[leg.getIndex()] = sampler.sample();
            scenarios[i] = new Scenario(probability, delays);
        }

        return scenarios;
    }

    private ArrayList<Leg> selectLegs() {
        switch (flightPickStrategy) {
            case HUB:
                return selectHubLegs();
            case RUSH_TIME:
                return selectRushTimeLegs();
            default:
                return legs;
        }
    }

    private ArrayList<Leg> selectHubLegs() {
        // find port with most departures.
        HashMap<Integer, Integer> portDeparturesMap = new HashMap<>();
        for (Leg leg : legs) {
            int depPort = leg.getDepPort();
            if (portDeparturesMap.containsKey(depPort))
                portDeparturesMap.replace(depPort, portDeparturesMap.get(depPort) + 1);
            else
                portDeparturesMap.put(depPort, 1);
        }

        int maxNumDepartures = 0;
        int hub = -1;
        for (Map.Entry<Integer, Integer> entry : portDeparturesMap.entrySet()) {
            if (entry.getValue() > maxNumDepartures) {
                hub = entry.getKey();
                maxNumDepartures = entry.getValue();
            }
        }

        // collect legs departing from hub and return them.
        ArrayList<Leg> selectedLegs = new ArrayList<>();
        for (Leg leg : legs)
            if (leg.getDepPort().equals(hub))
                selectedLegs.add(leg);

        return selectedLegs;
    }

    private ArrayList<Leg> selectRushTimeLegs() {
        // find earliest departure and latest arrival times.
        LocalDateTime earliestDepTime = legs.get(0).getDepTime();
        LocalDateTime latestArrTime = legs.get(0).getArrTime();

        for (int i = 1; i < legs.size(); ++i) {
            Leg leg = legs.get(i);
            if (leg.getDepTime().isBefore(earliestDepTime))
                earliestDepTime = leg.getDepTime();

            if (leg.getArrTime().isAfter(latestArrTime))
                latestArrTime = leg.getArrTime();
        }

        int dayLengthInMinutes = (int) Duration.between(earliestDepTime, latestArrTime).toMinutes();
        int rushTimeMinutes = (int) (0.25 * dayLengthInMinutes); // first one-fourth of day assumed to be rush time.

        LocalDateTime latestDepTime = earliestDepTime.plusMinutes(rushTimeMinutes);

        ArrayList<Leg> selectedLegs = new ArrayList<>();
        for (Leg leg : legs) {
            if (!leg.getDepTime().isAfter(latestDepTime))
                selectedLegs.add(leg);
        }

        return  selectedLegs;
    }
}
