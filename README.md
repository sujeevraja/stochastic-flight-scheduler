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
- commons math3 3.6.1 (included in repo in `lib` folder)
- log4j 2.10.0 (included in repo in `lib` folder)
- snakeyaml 1.23 (included in repo in `lib` folder)

## Usage

- Clone the repository.
- Add cplex.jar as a dependency and the path to the cplex dll file as
    `-Djava.library.path=/path/to/cplex/dll/folder`.
- Add all libraries provided as JAR files in the `lib` folder as dependencies.
- Run the project using the `main()` function in the `Main` class.

### Running with Intellij

- Use the Intellij option to create project from existing sources by pointing it to the repository
  folder.
- Right-click on the "robust-flight-scheduler" module in the project explorer pane on the left.
- In the "Project Structure" dialog, click on "Modules".
- There will be three tabs on the right pane called "Sources", "Paths" and "Dependencies".
- Click on the "Dependencies" tab.
- There should be a + sign on the right beside the table that has titles "Export" and "Scope".
- Clicking on this sign allows us to add JARs or directories as dependencies.
- Click on it, select "JARs or directories" and add cplex.jar.
- If needed, repeat this dependency addition procedure with all JAR files in the lib folder.
- Click on the "Apply" and "OK" buttons in the bottom right of the dialog. This should close it.
- Then, click on the "Run -> Edit Configurations -> Main".
- If there are no configurations available, create a new one.
- Add the following line to the "VM options" text box after changing the path appropriately:

`-Xms32m -Xmx4g -Djava.library.path=\path\to\cplex\dll\folder`

- Click on "Apply" and "Ok".

- Now, the project can be run with "Run -> Run Main" or "Run -> Run ... -> Main".
