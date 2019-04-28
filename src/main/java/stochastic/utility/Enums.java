package stochastic.utility;

public class Enums {

    /**
     * DistributionType specifies the probability distribution to use for generating random flight delays.
     */
    public enum DistributionType {EXPONENTIAL, TRUNCATED_NORMAL, LOGNORMAL}

    /**
     * FlightPickStrategy specifies the strategy to select flights with primary delays.
     * <p>
     * ALL: pick all flights.
     * HUB: all flights departing from the airport that has the most departures.
     * RUSH_TIME: pick flights in the first quarter of the day.
     */
    public enum FlightPickStrategy {ALL, HUB, RUSH_TIME}

    /**
     * ColumnGenStrategy specifies the column generation strategy for second stage problems.
     * <p>
     * FULL_ENUMERATION: enumerate all paths and solve single LP once.
     * ALL_PATHS: finish the labeling procedure and use all negative reduced cost paths.
     * BEST_PATHS: finish the labeling procedure and use a specific number of the most negative reduced cost paths.
     * FIRST_PATHS: exit the labeling procedure as soon as a specific number of paths have been found.
     * <p>
     * The number of paths for the BEST_PATHS and FIRST_PATHS strategy is specified using "numReducedCostPaths."
     */
    public enum ColumnGenStrategy {FULL_ENUMERATION, ALL_PATHS, BEST_PATHS, FIRST_PATHS}

    /**
     * Specifies KPIs that can be collected by running second-stage scenarios with an adjusted initial schedule.
     * The adjustment can be nothing (for original schedule), or based on naive/DEP/Benders reschedule solution.
     */
    public enum TestKPI {
        delayCost,
        totalFlightDelay,
        maximumFlightDelay,
        averageFlightDelay,
        totalPropagatedDelay,
        maximumPropagatedDelay,
        averagePropagatedDelay,
        totalExcessDelay,
        maximumExcessDelay,
        averageExcessDelay,
        delaySolutionTimeInSec
    }
}
