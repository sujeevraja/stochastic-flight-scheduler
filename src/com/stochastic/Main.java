package com.stochastic;

import com.stochastic.controller.Controller;
import com.stochastic.utility.OptException;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;


public class Main {
    /**
     * Only responsibility is to own main().
     */
    private final static Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            logger.info("Started optimization...");
            Controller controller = new Controller();
            controller.readData();

            int numSamples = 10;
            double scale = 2.5;
            double shape = 0.4;
            LogNormalDistribution ln = new LogNormalDistribution(scale, shape);

            int[] delayTimes = new int[numSamples];
            for (int i = 0; i < numSamples; ++i)
                delayTimes[i] = (int) Math.round(ln.sample());

            Arrays.sort(delayTimes);
            ArrayList<Integer> uniqueDelays = new ArrayList<>();
            ArrayList<Double> probabilities = new ArrayList<>();

            DecimalFormat df = new DecimalFormat("##.##");
            df.setRoundingMode(RoundingMode.HALF_UP);

            final double baseProbability = 1.0 / numSamples;
            int numCopies = 1;

            uniqueDelays.add(delayTimes[0]);
            int prevDelayTime = delayTimes[0];
            for(int i = 1; i < numSamples; ++i) {
                int delayTime = delayTimes[i];

                if(delayTime != prevDelayTime) {
                    final double prob = Double.parseDouble(df.format(numCopies * baseProbability));
                    probabilities.add(prob); // add probabilities for previous time.
                    uniqueDelays.add(delayTime); // add new delay time.
                    numCopies = 1;
                } else
                    numCopies++;

                prevDelayTime = delayTime;
            }
            probabilities.add(numCopies * baseProbability);

            // If there are n samples, Prob(x <= quantiles[i]) = (i+1) / n where 0 <= i <= n-1.
            // double[] quantiles = new double[numSamples - 1];
            // double probLevel = 1.0 / numSamples;
            // for(int i = 0; i < numSamples - 1; ++i)
            //     quantiles[i] = ln.inverseCumulativeProbability((i+1) * probLevel);

            // controller.createTestDisruption();
            // controller.solve();
            // controller.solveSecondStage();
            logger.info("Completed optimization.");
        } catch (OptException oe) {
            logger.error(oe);
        }
    }
}

