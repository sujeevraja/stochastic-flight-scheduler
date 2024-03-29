## Stochastic Flight Scheduler

This repository contains data and code used to run experiments for results in the
[paper](https://arxiv.org/abs/2001.08548) on using stochastic programming to adjust flight
schedules to minimize propagated delays. The crux of the code solves a 2-stage stochastic model
where the first stage reschedules flights to adjust slacks in connection time. The second stage
minimizes excess delay after offsetting primary delays using the first stage rescheduling. The code
requires CPLEX to run.

## Results

Results collected for the paper are available in the "scripts" folder. Refer to the readme file in
that folder for further details.

## Table Sources

The following sources were used for Tables reported in the paper.

- Table 1 (data)
    Created manually.
- Table 2 (quality)
    - Default distribution is LogNormal(15,15).
    - For s1,s2,s3,s4,s5: from "v2/results_training.csv" in
    "scripts/original_results/q.7z" filtered by LOG_NORMAL distribution and
    {HUB,RUSH} strategy.
    - For s6: from "run_4_benders_results.csv" obtained by running
        `collect_results.py`.
- Table 3 (column gen)
    - For s1,s2,s3,s4,s5: from "v2/results_benders.csv" in
    "scripts/original_results/t.7z".
    - For s6: from "run_2_time_results.csv" obtained by running
        `collect_results.py`. Filter run type by "columnGen".
- Table 4 (thread)
    - For s1,s2,s3,s4,s5: from "v2/results_benders.csv" in
        "scripts/original_results/p.7z".
    - For s6: from "run_2_time_results.csv" obtained by running
        `collect_results.py`. Filter run type by "parallel".
- Table 5 (single/multi cut)
    - For s1,s2,s3,s4,s5: from "v2/results_benders.csv" in
        "scripts/original_results/s.7z".
    - For s6: from "run_2_time_results.csv" obtained by running
        `collect_results.py`. Filter run type by "cut".
- Table 6 (column caching)
    - For s1,s2,s3,s4,s5: from "v2/results_benders.csv" in
        "scripts/original_results/c.7z".
    - For s6: from "run_2_time_results.csv" obtained by running
        `collect_results.py`. Filter run type by "cache".
- Table 7 (budget)
    - Default distribution is LogNormal(15,15).
    - For s1,s2,s3,s4,s5: from "v2/results_test.csv" in
        "scripts/original_results/b.7z".
    - For s6: from "run_4_quality_results.csv" obtained by running
        `collect_results.py`. Filter run type by "budget".
- Table 8 (distribution)
    - Default distribution mean is 30.
    - For s1,s2,s3,s4,s5: from "v2/results_test.csv" in
        "scripts/original_results/m.7z", filter mean by 30.
    - For s6: from "run_4_quality_results.csv" obtained by running
        `collect_results.py`. Filter run type by "distribution".
- Table 9 (mean)
    - Default distribution is exponential.
    - For s1,s2,s3,s4,s5: from "v2/results_test.csv" in
        "scripts/original_results/m.7z", filter distribution by "exponential".
    - For s6: from "run_3_quality_results.ccsv" obtained by running
        `collect_results.py`. Filter run type by "mean".
- Table 10 (num scenarios)
    - Default distribution is LogNormal(30,15).
    - For s1,s2,s3,s4: from "run_1_scenario_results.csv".
    - For s5,s6: from "run_3_quality_results.csv", filter run type by
        "numScenarios".

## License

License for code in this repo is the MIT license (see LICENSE file in the repo). However,
it relies on CPLEX and Java libraries like commons-math. So, their licenses have to be checked
before using code sections that use these libraries. Code sections with external dependencies are:

- Data input/output (XML, YAML libs)
- Solving models to get solutions, dual values (CPLEX)
- Generating random delays from a specific distribution (commons-math)

## Features

Given a flight schedule XML file, this solver can be used to:

- Generate random second-stage scenarios and probabilities.
- Run the 2-stage model with Benders and generated scenarios to find an optimal re-schedule plan.
- Solve the second stage models sequentially or in parallel.
- Use full enumeration or column generation with labeling to solve the second stage problems.
- Solve the same problem using a naive MIP that uses expected values of primary delays.
- Compare performance of the all models and original schedule with new random delay scenarios.
- Run batch runs to compare model quality/performance by varying parameters.

## Usage

Create a file named _gradle.properties_ in the repo folder. Add the following two lines to the
file:

```
cplexJarPath=/file/path/to/cplex/jar
cplexLibPath=/folder/path/to/cplex/library
```

The key _cplexJarPath_ specifies the complete path to the CPLEX JAR file. It
must end with _cplex.jar_. The _cplexLibPath_ should point to the path that
holds the CPLEX library (file with dll/a/so extension). This path should be
a folder path and NOT a file path. It usually points to a folder within the
CPLEX bin folder. For example, the paths could look like so on a Mac:

```
cplexJarPath=/Applications/CPLEX_Studio128/cplex/lib/cplex.jar
cplexLibPath=/Applications/CPLEX_Studio128/cplex/bin/x86-64_osx
```

Open a terminal and make the `./gradlew` file executable if necessary. Run
`./gradlew build` to compile and `./gradlew run` to run the code. The latter
task can also be run directly. Two other useful tasks are `./gradlew clean`,
which cleans the gradle build files and `./gradlew cleanLogs` which cleans
logs and solution files. If Gradle is installed, `./gradlew` can be replaced
with `gradle` in all the above commands. Run `./gradlew --args="-h"` to see
available command-line arguments.

## Batch runs

The optimizer supports the following types of batch runs:

- Generate delay run: launched with `./gradlew run --args="-generateDelays 50"`
- Benders run: launched with `./gradlew run --args="-batch benders`. The optimizer
    will run just the Benders algorithm for the scenario specified by the `inputName` arg.
- Training run: launched with `./gradlew run --args="-batch train`. The optimizer will
    will run the naive model, DEP and Benders and report some stats about each of them.
- Test run: lanched with`./gradlew run --args="-batch test`. The model will take as input some
    reschedule solutions (usually the output of a "train" run), apply them to a given schedule,
    and will use a different set of test scenarios to generate stats like total propagated delay,
    2-stage objective for 4 types of rescheduling approaches: original (i.e. do nothing), naive,
    Benders and DEP.

To collect results similar to Tables in the [paper]((https://arxiv.org/abs/2001.08548)), go to the
"scripts" folder and run `python3 script_builder.py`. Look at any line of the shell scripts to see
how `batch_runner.py` runs the optimizer. This gives an idea of how to generate different types of
batch run results.

## Running with Intellij

- Open Intellij, select `Import Project` and point it to the repo folder.
- Select `Import Project from external model -> Gradle`.
- Check the following checkboxes:
    + `Use auto-import`
    + `Create directories for empty content roots automatically`
- Select the `Use default gradle wrapper`
- Clock on the `Finish` button on the bottom right.

That's it, nothing else is needed. Ignore complaints about modules that were
removed. To run the project for the first time, select
`View -> Tool Windows -> Gradle`. The Gradle window should open up on the
right. Expand the tree and double-click on `Tasks -> application -> run`.
For subsequent running, we can continue doing the same thing, or select
this configuration from the `Run` menu. Debugging can also be done with this
configuration from the same menu. After the first run, this task will show
up as something like `Run project-name [run]` and `Debug project-name [run]`
in the `Run` menu, where `project-name` is the name of the project.

## Additional notes

### CPLEX

CPLEX and other dependencies have been set up correctly in `build.gradle`.
But this counts on the fact that _cplexJarPath_ and _cplexLibPath_ have been
specified correctly in _~/.gradle/gradle.properties_.

### Config files

In case some dependencies need access to config files, the files can be placed
in _src/main/resources_. This folder already holds _log4j2.xml_, the config
file for the log4j logging utility.


### Java logging

To disable logging, open _log4j2.xml_, comment out or remove all _AppenderRef_
nodes from the _Root_ node. Also change the _level_ value of the _Root_ node
to _ERROR_ for safety.

