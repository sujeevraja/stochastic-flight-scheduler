package stochastic.delay;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import stochastic.utility.Enums;

public class Sampler {
    /**
     * generates integer samples of delay based on specified distribution, mean and standard deviation.
     */
    private RealDistribution distribution;

    Sampler(Enums.DistributionType distributionType, double mean, double sd) {
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

    double sample() {
        while (true) {
            final double realization = distribution.sample();
            if (realization >= 0)
                return realization;
        }
    }
}
