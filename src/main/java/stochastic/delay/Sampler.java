package stochastic.delay;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import stochastic.registry.Parameters;
import stochastic.utility.Enums;

class Sampler {
    /**
     * generates integer samples of delay based on specified distribution, mean and standard deviation.
     */
    private RealDistribution distribution;

    Sampler() {
        final Enums.DistributionType distributionType = Parameters.getDistributionType();
        final double mean = Parameters.getDistributionMean();
        final double sd = Parameters.getDistributionSd();

        switch (distributionType) {
            case TRUNCATED_NORMAL:
                distribution = new NormalDistribution(mean, sd);
                break;
            case LOGNORMAL:
                distribution = getLogNormal(mean, sd);
                break;
            default:
                distribution = new ExponentialDistribution(mean);
        }
    }

    /**
     * Generates non-negative integer samples by rounding realizations of the distribution set up in the constructor.
     *
     * The values of lognormal and exponential will always be non-negative. However, the negativity check is needed
     * for the normal distribution right-truncated at 0.
     *
     * @return random integer delay value.
     */
    int sample() {
        while (true) {
            final int realization = (int) Math.round(distribution.sample());
            if (realization >= 0)
                return realization;
        }
    }

    /**
     * returns a lognormal distribution with the given mean and standard deviation.
     *
     * If our lognormal random variable is X, the given mean and sd are that of X. They will be used to calculate the
     * mean and standard deviation of the normal distribution Y = ln(X). This is necessary as the normal mean (scale)
     * and normal sd (shape) are the parameters required by the apache LogNormalDistribution class. The calculation
     * formulae were obtained from the Wikipedia page for LogNormal distribution.
     *
     * @param mean lognormal mean.
     * @param sd lognormal standard deviation.
     * @return LogNormalDistribution object with given mean and sd.
     */
    private LogNormalDistribution getLogNormal(double mean, double sd) {
        final double c = 1 + ((sd*sd) / (mean*mean));
        final double normalMean = Math.log(mean / Math.sqrt(c)); // scale of lognormal
        final double normalSd = Math.sqrt(Math.log(c)); // shape of lognormal
        return new LogNormalDistribution(normalMean, normalSd);
    }
}
