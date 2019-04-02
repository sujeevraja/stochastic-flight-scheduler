package com.stochastic.delay;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public interface DelayGenerator {
    Logger logger = LogManager.getLogger(DelayGenerator.class);
    Scenario[] generateScenarios(int numScenarios);
}
