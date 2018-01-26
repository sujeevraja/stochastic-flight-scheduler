## Robust Flight Scheduler

This repository contains Java code to solve a 2-stage stochastic model related to flight planning.
The model is a 2-stage stochastic model where the first stage decides published turn times, while
the second stage maximizes OTP based on the published turn times and random flight delays.

## Dependencies

- CPLEX
- log4j 1.2.16
    - To use, download the jar file archive (apache-log4j-2.10.0-bin.zip).
    - Place log4j-api-2.10.0.jar and log4j-core-2.10.0.jar in the lib folder.

## Usage

- Clone the repository.
- Add the `lib` folder to the classpath.
- Run with Intellij or Eclipse.
