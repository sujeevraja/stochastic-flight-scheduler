package stochastic.main;

import ilog.concert.IloException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.dao.RescheduleSolutionDAO;
import stochastic.dao.ScheduleDAO;
import stochastic.delay.DelayGenerator;
import stochastic.delay.Scenario;
import stochastic.delay.StrategicDelayGenerator;
import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Path;
import stochastic.output.KpiManager;
import stochastic.output.QualityChecker;
import stochastic.output.RescheduleSolution;
import stochastic.registry.DataRegistry;
import stochastic.registry.Parameters;
import stochastic.solver.BendersSolver;
import stochastic.solver.DepSolver;
import stochastic.solver.NaiveSolver;
import stochastic.utility.CSVHelper;
import stochastic.utility.Enums;
import stochastic.utility.OptException;

import java.io.*;
import java.util.*;

class Controller {
    /**
     * Class that controls the entire solution process from reading data to writing output.
     */
    private final static Logger logger = LogManager.getLogger(Controller.class);
    private DataRegistry dataRegistry;
    private KpiManager kpiManager;

    private RescheduleSolution naiveModelSolution;
    private double naiveModelSolutionTime;

    private RescheduleSolution depSolution;
    private double depObjective;
    private double depSolutionTime;

    private RescheduleSolution bendersSolution;
    private double bendersSolutionTime;
    private double bendersLowerBound;
    private double bendersUpperBound;
    private double bendersGap;
    private int bendersNumIterations;
    private int bendersNumCuts;

    Controller() {
        dataRegistry = new DataRegistry();
        // String timeStamp = new SimpleDateFormat("yyyy_MM_dd'T'HH_mm_ss").format(new Date());
        kpiManager = new KpiManager(dataRegistry);
    }

    DataRegistry getDataRegistry() {
        return dataRegistry;
    }

    final void readData() throws OptException {
        logger.info("Started reading data...");

        // Read leg data and remove unnecessary legs
        String instancePath = Parameters.getInstancePath();
        ArrayList<Leg> legs = new ScheduleDAO(instancePath + "/Schedule.xml").getLegs();
        storeLegs(legs);
        // limitNumTails();
        logger.info("Collected leg and tail data from Schedule.xml.");
        logger.info("completed reading data.");

        logger.info("started building connection network...");
        dataRegistry.buildConnectionNetwork();
        dataRegistry.getNetwork().countPathsForTails(dataRegistry.getTails());
        logger.info("built connection network.");
    }

    final void setDelayGenerator() {
        // DelayGenerator dgen = new FirstFlightDelayGenerator(dataRegistry.getLegs().size(), dataRegistry.getTails());
        // DelayGenerator dgen = new TestDelayGenerator(dataRegistry.getLegs().size(), dataRegistry.getTails());
        DelayGenerator dgen = new StrategicDelayGenerator(dataRegistry.getLegs());
        dataRegistry.setDelayGenerator(dgen);
    }

    final void buildScenarios() throws OptException {
        if (Parameters.isParsePrimaryDelaysFromFiles())
            parsePrimaryDelaysFromFiles();
        else
            buildScenariosFromDistribution();
    }

    /**
     * Generates delay realizations and probabilities for second stage scenarios.
     */
    private void buildScenariosFromDistribution() {
        DelayGenerator dgen = dataRegistry.getDelayGenerator();
        Scenario[] scenarios = dgen.generateScenarios(Parameters.getNumSecondStageScenarios());
        dataRegistry.setDelayScenarios(scenarios);

        double avgTotalPrimaryDelay = 0.0;
        for (Scenario s : scenarios)
            avgTotalPrimaryDelay += s.getTotalPrimaryDelay();
        avgTotalPrimaryDelay /= scenarios.length;

        logger.info("average total primary delay (minutes): " + avgTotalPrimaryDelay);

        final int budget = (int) Math.round(avgTotalPrimaryDelay * Parameters.getRescheduleBudgetFraction());
        dataRegistry.setRescheduleTimeBudget(budget);
    }

    final void writeScenariosToFile() throws OptException {
        logger.info("starting scenario writing...");
        Scenario[] scenarios = dataRegistry.getDelayScenarios();
        ArrayList<Leg> legs = dataRegistry.getLegs();
        String prefix = "solution/primary_delays_";
        String suffix = ".csv";
        List<String> headers = new ArrayList<>(Arrays.asList("leg_id", "delay_minutes"));

        for (int i = 0; i < scenarios.length; ++i) {
            String filePath = prefix + i + suffix;
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
                CSVHelper.writeLine(writer, headers);

                int[] primaryDelays = scenarios[i].getPrimaryDelays();
                for (int j = 0; j < primaryDelays.length; ++j) {
                    if (primaryDelays[j] > 0) {
                        Leg leg = legs.get(j);
                        List<String> line = new ArrayList<>(Arrays.asList(
                            leg.getId().toString(),
                            Integer.toString(primaryDelays[j])));

                        CSVHelper.writeLine(writer, line);
                    }
                }

                writer.close();
            } catch (IOException ex) {
                logger.error(ex);
                throw new OptException("error writing primary delays to file");
            }

        }
        logger.info("completed scenario writing.");
    }

    private void parsePrimaryDelaysFromFiles() throws OptException {
        logger.info("staring primary delay parsing...");
        ArrayList<Leg> legs = dataRegistry.getLegs();
        final int numScenarios = Parameters.getNumSecondStageScenarios();
        final double probability = 1.0 / numScenarios;
        Scenario[] scenarios = new Scenario[numScenarios];
        final String prefix = "solution/primary_delays_";
        final String suffix = ".csv";

        // Build map from leg id to index for delay data collection.
        Map<Integer, Integer> legIdIndexMap = new HashMap<>();
        for (int i = 0; i < legs.size(); ++i)
            legIdIndexMap.put(legs.get(i).getId(), i);

        double avgTotalPrimaryDelay = 0.0;

        for (int i = 0; i < numScenarios; ++i) {
            // read delay data
            String filePath = prefix + i + suffix;
            Map<Integer, Integer> primaryDelayMap = new HashMap<>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(filePath));
                CSVHelper.parseLine(reader); // parse once to skip headers

                while (true) {
                    List<String> line = CSVHelper.parseLine(reader);
                    if (line == null)
                        break;

                    int legId = Integer.parseInt(line.get(0));
                    int primaryDelay = Integer.parseInt(line.get(1));
                    primaryDelayMap.put(legId, primaryDelay);
                }

                reader.close();
            } catch (IOException ex) {
                logger.error(ex);
                throw new OptException("error reading primary delays from file");
            }

            // build scenario, store it
            int[] delays = new int[legs.size()];
            Arrays.fill(delays, 0);
            for (Map.Entry<Integer, Integer> entry : primaryDelayMap.entrySet())
                delays[legIdIndexMap.get(entry.getKey())] = entry.getValue();

            scenarios[i] = new Scenario(probability, delays);
            avgTotalPrimaryDelay += scenarios[i].getTotalPrimaryDelay();
        }

        dataRegistry.setDelayScenarios(scenarios);

        avgTotalPrimaryDelay /= scenarios.length;
        logger.info("average total primary delay (minutes): " + avgTotalPrimaryDelay);

        final int budget = (int) Math.round(
            avgTotalPrimaryDelay * Parameters.getRescheduleBudgetFraction());
        dataRegistry.setRescheduleTimeBudget(budget);
        logger.info("completed primary delay parsing.");
    }

    final void solve() throws OptException {
        Enums.Model model = Parameters.getModel();
        if (model == Enums.Model.BENDERS) solveWithBenders();
        else if (model == Enums.Model.DEP) solveWithDEP();
        else if (model == Enums.Model.NAIVE) solveWithNaiveApproach();
        else {
            solveWithNaiveApproach();
            solveWithDEP();
            solveWithBenders();
        }
    }

    private void solveWithBenders() throws OptException {
        try {
            BendersSolver bendersSolver = new BendersSolver(dataRegistry);
            if (Parameters.isWarmStartBenders())
                bendersSolver.solve(naiveModelSolution);
            else
                bendersSolver.solve(null);

            bendersSolution = bendersSolver.getFinalRescheduleSolution();
            bendersSolutionTime = bendersSolver.getSolutionTime();
            bendersLowerBound = bendersSolver.getLowerBound();
            bendersUpperBound = bendersSolver.getUpperBound();
            bendersGap = bendersSolver.getPercentGap();
            bendersNumIterations = bendersSolver.getIteration();
            bendersNumCuts = bendersSolver.getNumBendersCuts();
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("CPLEX error in Benders");
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing to files during Benders");
        }
    }

    final double getBendersRescheduleCost() {
        return bendersSolution.getRescheduleCost();
    }

    final double getBendersSolutionTime() {
        return bendersSolutionTime;
    }

    final double getBendersLowerBound() {
        return bendersLowerBound;
    }

    final double getBendersUpperBound() {
        return bendersUpperBound;
    }

    final double getBendersGap() {
        return bendersGap;
    }

    final int getBendersNumCuts() {
        return bendersNumCuts;
    }

    final int getBendersNumIterations() {
        return bendersNumIterations;
    }

    private void solveWithNaiveApproach() throws OptException {
        try {
            NaiveSolver naiveSolver = new NaiveSolver(dataRegistry);
            naiveSolver.solve();
            naiveModelSolution = naiveSolver.getFinalRescheduleSolution();
            naiveModelSolutionTime = naiveSolver.getSolutionTime();
        } catch (IloException ex) {
            logger.error(ex);
            throw new OptException("exception solving naive model");
        }
    }

    final double getNaiveModelRescheduleCost() {
        return naiveModelSolution.getRescheduleCost();
    }

    final double getNaiveModelSolutionTime() {
        return naiveModelSolutionTime;
    }

    private void solveWithDEP() throws OptException {
        DepSolver depSolver = new DepSolver();
        depSolver.solve(dataRegistry);
        depSolution = depSolver.getDepSolution();
        depObjective = depSolver.getObjValue();
        depSolutionTime = depSolver.getSolutionTimeInSeconds();
    }

    final double getDepRescheduleCost() {
        return depSolution.getRescheduleCost();
    }

    final double getDepSolutionTime() {
        return depSolutionTime;
    }

    /**
     * Processes the parsed data to populate data registry containers like legs, tails and on-plan paths.
     *
     * @param inputLegs list of legs parsed from a Schedule.xml file.
     */
    private void storeLegs(ArrayList<Leg> inputLegs) {
        ArrayList<Leg> legs = new ArrayList<>();
        HashMap<Integer, ArrayList<Leg>> tailHashMap = new HashMap<>();

        // Collect legs and build a mapping between tail and legs.
        Integer index = 0;
        for (Leg leg : inputLegs) {
            Integer tailId = leg.getOrigTailId();

            leg.setIndex(index);
            ++index;
            legs.add(leg);

            if (tailHashMap.containsKey(tailId))
                tailHashMap.get(tailId).add(leg);
            else {
                ArrayList<Leg> tailLegs = new ArrayList<>();
                tailLegs.add(leg);
                tailHashMap.put(tailId, tailLegs);
            }
        }

        dataRegistry.setLegs(legs);
        logger.info("Number of legs: " + legs.size());

        // build tails from schedule
        ArrayList<Tail> tails = new ArrayList<>();
        for (Map.Entry<Integer, ArrayList<Leg>> entry : tailHashMap.entrySet()) {
            ArrayList<Leg> tailLegs = entry.getValue();
            tailLegs.sort(Comparator.comparing(Leg::getDepTime));
            tails.add(new Tail(entry.getKey(), tailLegs));
        }

        tails.sort(Comparator.comparing(Tail::getId));
        for (int i = 0; i < tails.size(); ++i)
            tails.get(i).setIndex(i);

        dataRegistry.setTails(tails);
        dataRegistry.buildIdTailMap();
        logger.info("Number of tails: " + tails.size());

        HashMap<Integer, Path> tailPaths = new HashMap<>();
        for (Map.Entry<Integer, ArrayList<Leg>> entry : tailHashMap.entrySet()) {
            ArrayList<Leg> tailLegs = entry.getValue();
            tailLegs.sort(Comparator.comparing(Leg::getDepTime));

            // correct turn times as we assume that the input schedule is always valid.
            for (int i = 0; i < tailLegs.size() - 1; ++i) {
                Leg leg = tailLegs.get(i);
                Leg nextLeg = tailLegs.get(i + 1);
                int turnTime = (int) (nextLeg.getDepTime() - leg.getArrTime());
                if (turnTime < leg.getTurnTimeInMin()) {
                    logger.warn("turn after leg " + leg.getId() + " is shorter than its turn time "
                            + leg.getTurnTimeInMin() + ".");
                    logger.warn("shortening it to " + turnTime);
                    leg.setTurnTimeInMin(turnTime);
                }
            }

            Path p = new Path(dataRegistry.getTail(entry.getKey()));

            for (Leg l : tailLegs)
                p.addLeg(l, 0);

            tailPaths.put(entry.getKey(), p);
        }
        dataRegistry.setTailOrigPathMap(tailPaths);
    }

    /**
     * This function helps reduce problem size for debugging/testing purposes.
     */
    private void limitNumTails() {
        // limit the stored tails.
        ArrayList<Tail> newTails = new ArrayList<>();
        ArrayList<Tail> oldTails = dataRegistry.getTails();

        int tailIndex = 0;

        for (int i = 0; i < 60; ++i, ++tailIndex) { // this causes infeasible 2nd stage, check why
            // for (int i = 10; i < 60; ++i, ++tailIndex) {
            // for (int i = 20; i < 60; ++i, ++tailIndex) {
            // for (int i = 30; i < 60; ++i, ++tailIndex) {
            // for (int i = 40; i < 60; ++i, ++tailIndex) {
            // for (int i = 43; i < 60; ++i, ++tailIndex) {
            // for (int i = 44; i < 60; ++i, ++tailIndex) {
            // for (int i = 44; i < 55; ++i, ++tailIndex) {
            // for (int i = 44; i < 54; ++i, ++tailIndex) {
            // for (int i = 45; i < 54; ++i, ++tailIndex) {
            Tail tail = oldTails.get(i);
            tail.setIndex(tailIndex);
            newTails.add(tail);
            logger.debug("selected tail " + tail.getId());
        }
        dataRegistry.setTails(newTails);

        // cleanup tail paths.
        HashMap<Integer, Path> tailHashMap = dataRegistry.getTailOrigPathMap();
        HashMap<Integer, Path> newTailPathMap = new HashMap<>();
        for (Tail tail : newTails)
            newTailPathMap.put(tail.getId(), tailHashMap.get(tail.getId()));
        dataRegistry.setTailOrigPathMap(newTailPathMap);

        // cleanup legs.
        ArrayList<Leg> newLegs = new ArrayList<>();
        int legIndex = 0;
        for (Leg leg : dataRegistry.getLegs()) {
            Integer tailId = leg.getOrigTailId();
            if (newTailPathMap.containsKey(tailId)) {
                leg.setIndex(legIndex);
                newLegs.add(leg);
                ++legIndex;
            }
        }
        dataRegistry.setLegs(newLegs);
    }

    void processSolution() throws OptException {
        try {
            ArrayList<RescheduleSolution> rescheduleSolutions = getAllRescheduleSolutions();
            for (RescheduleSolution sln : rescheduleSolutions) {
                if (!sln.isOriginalSchedule()) {
                    sln.writeCSV(dataRegistry.getLegs());
                    logger.info("wrote " + sln.getName() + " reschedule solution");
                    kpiManager.addKpi(sln.getName() + " reschedule cost", sln.getRescheduleCost());
                }
            }

            kpiManager.addKpi("naive model solution time (seconds)", naiveModelSolutionTime);
            kpiManager.addKpi("dep solution time (seconds)", depSolutionTime);
            kpiManager.addKpi("dep objective", depObjective);
            kpiManager.addKpi("benders solution time (seconds)", bendersSolutionTime);
            kpiManager.addKpi("benders iterations", bendersNumIterations);
            kpiManager.addKpi("benders lower bound", bendersLowerBound);
            kpiManager.addKpi("benders upper bound", bendersUpperBound);
            kpiManager.addKpi("benders gap (%)", bendersGap);
            kpiManager.addKpi("benders cuts added", bendersNumCuts);
            kpiManager.writeOutput();

            if (Parameters.isCheckSolutionQuality()) {
                Scenario[] testScenarios = dataRegistry.getDelayGenerator().generateScenarios(
                    Parameters.getNumTestScenarios());
                QualityChecker qc = new QualityChecker(dataRegistry, testScenarios);
                qc.compareSolutions(rescheduleSolutions);
            }
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("error writing solution");
        }
    }

    private ArrayList<RescheduleSolution> getAllRescheduleSolutions() {
        ArrayList<RescheduleSolution> rescheduleSolutions = new ArrayList<>();
        RescheduleSolution original = new RescheduleSolution("original", 0, null);
        original.setOriginalSchedule(true);
        rescheduleSolutions.add(original);
        if (naiveModelSolution != null)
            rescheduleSolutions.add(naiveModelSolution);

        if (depSolution != null)
            rescheduleSolutions.add(depSolution);

        if (bendersSolution != null)
            rescheduleSolutions.add(bendersSolution);

        return rescheduleSolutions;
    }

    void writeRescheduleSolutions() throws OptException {
        try {
            if (naiveModelSolution != null)
                naiveModelSolution.writeCSV(dataRegistry.getLegs());

            if (depSolution != null)
                depSolution.writeCSV(dataRegistry.getLegs());

            if (bendersSolution != null)
                bendersSolution.writeCSV(dataRegistry.getLegs());
        } catch (IOException ex) {
            logger.error(ex);
            throw new OptException("problem writing reschedule solutions to csv");
        }
    }

    ArrayList<RescheduleSolution> collectRescheduleSolutionsFromFiles() throws OptException {
        ArrayList<RescheduleSolution> rescheduleSolutions = new ArrayList<>();
        RescheduleSolution original = new RescheduleSolution("original", 0, null);
        original.setOriginalSchedule(true);
        rescheduleSolutions.add(original);

        ArrayList<Leg> legs = dataRegistry.getLegs();

        String[] models = new String[] {"naive", "dep", "benders"};
        for (String model : models)
            rescheduleSolutions.add(
                    (new RescheduleSolutionDAO(model, legs)).getRescheduleSolution());

        return rescheduleSolutions;
    }
}
