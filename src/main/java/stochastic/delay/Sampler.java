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

        if (distributionType == Enums.DistributionType.TRUNCATED_NORMAL) {
            distribution = new NormalDistribution(mean, sd);
        } else if (distributionType == Enums.DistributionType.LOGNORMAL) {
            // Formulae from wikipedia.
            final double c = 1 + ((sd*sd) / (mean*mean));
            final double normalMean = Math.log(mean / Math.sqrt(c)); // scale of lognormal
            final double normalSd = Math.sqrt(Math.log(c)); // shape of lognormal
            distribution = new LogNormalDistribution(normalMean, normalSd);
        } else { // default is exponential
            distribution = new ExponentialDistribution(mean);
        }
    }

    int sample() {
        while (true) {
            final int realization = (int) Math.round(distribution.sample());
            if (realization >= 0)
                return realization;
        }
    }
}
