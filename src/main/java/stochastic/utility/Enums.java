package stochastic.utility;

public class Enums {

    /**
     * DistributionType specifies the probability distribution to use for generating random flight delays.
     */
    public enum DistributionType { EXPONENTIAL, TRUNCATED_NORMAL, LOGNORMAL }

    /**
     * FlightPickStrategy specifies the strategy to select flights with primary delays.
     *
     * ALL: pick all flights.
     * HUB: all flights departing from the airport that has the most departures.
     * RUSH_TIME: pick flights in the first quarter of the day.
     */
    public enum FlightPickStrategy { ALL, HUB, RUSH_TIME }

    /**
     * ReducedCostStrategy specifies the strategy to use for selecting columns generated by the pricing problem.
     *
     * ALL_PATHS: finish the labeling procedure and use all negative reduced cost paths.
     * BEST_PATHS: finish the labeling procedure and use a specific number of the most negative reduced cost paths.
     * FIRST_PATHS: exit the labeling procedure as soon as a specific number of paths have been found.
     *
     * The number of paths for the BEST_PATHS and FIRST_PATHS strategy is specified using "numReducedCostPaths."
     */
    public enum ReducedCostStrategy { ALL_PATHS, BEST_PATHS, FIRST_PATHS, }
}
