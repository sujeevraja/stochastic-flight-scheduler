## Stochastic Flight Scheduler

This repository contains Java code to solve a 2-stage stochastic model related to flight planning.
The model is a 2-stage stochastic model where the first stage reschedules flights to adjust
slacks. The second stage minimizes excess delay over the OTP limit after offsetting primary delays
using the first stage rescheduling.

## Features

Given a flight schedule XML file, this solver can be used to:

- generate random second-stage scenarios and probabilities.
- run the 2-stage model with Benders and generated scenarios to find an optimal reschedule plan.
- solve the second stage models sequentially or in parallel.
- Use full enumeration or column generation with labeling to solve the second stage problems.
- solve the same problem using a naive MIP that uses expected values of primary delays.
- compare performance of the all models and original schedule with new random delay scenarios.
- run batch runs to compare model quality/performance by varying parameters.

## Dependencies

- CPLEX
- Gradle (installation optional)
- commons math3 3.6.1 (for probability distributions, obtained by Gradle)
- commons CLI 1.4 (for Java CLI argument parsing, obtained by Gradle)
- log4j 2.10.0 (for logging, obtained by Gradle)
- snakeyaml 1.23 (for YAML file writing, obtained by Gradle)
- Python3 (only to run the batch runner script _batch_runner.py_)

## Usage

Clone the repository. Create a file named _gradle.properties_ in the root
folder. Add the following two lines to the file:

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
with `gradle` in all the above commands.

## Batch runs

The following types of batch runs are supported:

- budget: get results with varying budget.
- mean: get results with varying distribution and distribution mean.
- quality: get results with varying distributions, flight pick strategies.
- time: get results with varying reschedule time budget.

A separate python script named _batch_runner.py_ has been provided to do these
runs. It has four command line arguments _(-b, -m, -q, -t)_ to run each of the
above runs. It can also run do all the runs using the _-a_ argument. Use the
following steps to setup and start a batch run.

- Disable Java logging in _src/main/resources/log4j2.xml_.
- Get to the repo folder in a terminal.
- Run `gradle clean cleanLogs` to remove the project JAR and logs.
- Run `gradle uberJar`. This prepares a fat JAR at _builds/libs_.
- Start a batch run with Python. For example, `python3 batch_runner.py -a`.

The batch run script can also be made executable with `chmod +x batch_runner.py`
and run directly like `./batch_runner.py -q`.

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

