## Robust Flight Scheduler

This repository contains Java code to solve a 2-stage stochastic model related to flight planning.
The model is a 2-stage stochastic model where the first stage decides published turn times, while
the second stage maximizes OTP based on the published turn times and random flight delays.

## Dependencies

- CPLEX
- log4j 2.10.0
    - To use, download the jar file archive (apache-log4j-2.10.0-bin.zip).
    - Place log4j-api-2.10.0.jar and log4j-core-2.10.0.jar in the lib folder.

## Usage

- Clone the repository.
- Add cplex.jar as a dependency and the path to the cplex dll file as
    `-Djava.library.path=/path/to/cplex/dll/folder`.
- Run the project using the `main()` function in the `Main` class.

### Running with Intellij

- Use the Intellij option to create project fromm existing sources by pointing it to the repository
  folder.
- Right-click on the "robust-flight-scheduler" module in the project explorer pane on the left.
- In the "Project Structure" dialog, click on "Modules".
- There will be three tabs on the right pane called "Sources", "Paths" and "Dependencies".
- Click on the "Dependencies" tab.
- There should be a + sign on the right bside the table that has titles "Export" and "Scope".
- Clicking on this sign allows us to add JARs or directories as dependencies.
- Click on it, select "JARs or directories" and add cplex.jar.
- Repeat this process with log4j-api-2.10.0.jar and log4j-core-2.10.0.jar in the lib folder.
- Click on the "Apply" and "OK" buttons in the bottom right of the dialog. This should close it.
- Then, click on the "Run -> Edit Configurations -> Main".
- Add the following line to the "VM options" textbox after changing the path appropriately:

`-Xms32m -Xmx4g -Djava.library.path=\path\to\cplex\dll\folder`

- Add the following line to the "Program arguments" box:

`-Dlog4j.configurationFile=lib/log4j2.xml`

- Add the path to the folder containing the "src" folder to the "Working Directory" textbox.

- Click on "Apply" and "Ok".

- Now, the project can be run with "Run -> Run Main" or "Run -> Run ... -> Main".
