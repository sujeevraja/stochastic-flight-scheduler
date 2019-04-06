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
- solve the same problem using a naive MIP that reschedules based on expected values of primary delays.
- compare the performance of the two models with the original schedule using new random delay scenarios. 

## Dependencies

- CPLEX
- Gradle (installation optional)
- commons math3 3.6.1 (obtained by Gradle)
- log4j 2.10.0 (obtained by Gradle)
- snakeyaml 1.23 (obtained by Gradle)

## Usage

Clone the repository. Create a file named `gradle.properties` in the root
folder. Add the following two lines to the file:

```
cplexJarPath=/file/path/to/cplex/jar
cplexLibPath=/folder/path/to/cplex/library
```

The key `cplexJarPath` specifies the complete path to the CPLEX JAR file. It
must end with `cplex.jar`. The `cplexLibPath` should point to the path that
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
which cleans the gradle build files and `./gradlew cleanfiles` which cleans
logs and solution files. If Gradle is installed, `./gradlew` can be replaced
with `gradle` in all the above commands.

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
For subsequent runnning, we can continue doing the same thing, or select
this configuration from the `Run` menu. Debugging can also be done with this
configuration from the same menu. After the first run, this task will show
up as something like `Run project-name [run]` and `Debug project-name [run]`
in the `Run` manu, where `project-name` is the name of the project.

## Additional notes

CPLEX and other dependencies have been set up correctly in `build.gradle`. In
case some depdendencies need access to config files, the files can be placed
in `src/main/resources`. This folder alredy holds `log4j2.xml`, the config
file for the `log4j` logging utility.
