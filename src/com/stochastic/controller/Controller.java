package com.stochastic.controller;

import com.stochastic.domain.Leg;
import com.stochastic.dao.EquipmentsDAO;
import com.stochastic.dao.ParametersDAO;
import com.stochastic.dao.ScheduleDAO;
import com.stochastic.domain.Tail;
import com.stochastic.registry.DataRegistry;
import com.stochastic.utility.OptException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.xml.sax.SAXException;

public class Controller {
    /**
     * Class that controls the entire solution process from reading data to writing output.
     */
    private final static Logger logger = LogManager.getLogger(Controller.class);
    private DataRegistry dataRegistry;

    public Controller() {
        dataRegistry = new DataRegistry();
    }

    public final void readData() throws OptException {
        logger.info("Started reading data...");
        String scenarioPath = getScenarioPath();

        // Read parameters
        ParametersDAO parametersDAO = new ParametersDAO(scenarioPath + "\\Parameters.xml");
        dataRegistry.setWindowStart(parametersDAO.getWindowStart());
        dataRegistry.setWindowEnd(parametersDAO.getWindowEnd());
        dataRegistry.setMaxLegDelayInMin(parametersDAO.getMaxFlightDelayInMin());
        logger.info("Completed reading parameter data from Parameters.xml.");

        // Read equipment data
        dataRegistry.setEquipment(new EquipmentsDAO(scenarioPath + "\\Equipments.xml").getEquipments().get(0));
        logger.info("Completed reading equipment data from Equipments.xml.");

        // Read leg data and remove unnecessary legs
        ArrayList<Leg> legs = new ScheduleDAO(scenarioPath + "\\Schedule.xml",
                dataRegistry.getMaxLegDelayInMin()).getLegs();
        storeLegs(legs);
        logger.info("Collected leg and tail data from Schedule.xml.");
        logger.info("Completed reading data.");
    }

    public final void createTestDisruption() {
        for(Tail tail : dataRegistry.getTails()) {
            if(tail.getId() == 10001) {
                Leg firstLeg = tail.getOrigSchedule().get(0);
                firstLeg.setTurnTimeInMin(60);
                return;
            }
        }
    }

    public final void solveSecondStage() throws OptException {
        if(!disruptionExists()) {
            logger.warn("Calling second-stage solver without any disruptions.");
            logger.warn("Solver not called.");
            return;
        }

        SecondStageController ssc = new SecondStageController(dataRegistry.getLegs(), dataRegistry.getTails(),
                dataRegistry.getWindowStart(), dataRegistry.getWindowEnd(), dataRegistry.getMaxLegDelayInMin());
        ssc.solve();
    }

    private String getScenarioPath() throws OptException {
        try {
            File xmlFile = new File("config.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            Node scenarioNode = doc.getElementsByTagName("scenarioPath").item(0);
            return scenarioNode.getTextContent();
        } catch (ParserConfigurationException pce) {
            logger.error(pce);
            throw new OptException("Unable to create DocumentBuilder to read config.xml");
        } catch (IOException ioe) {
            logger.error(ioe);
            throw new OptException("Unable to read config.xml");
        } catch (SAXException se) {
            logger.error(se);
            throw new OptException("Possibly ill-formed xml in config.xml");
        }
    }

    private void storeLegs(ArrayList<Leg> inputLegs) {
        final ArrayList<Integer> tailIds = dataRegistry.getEquipment().getTailIds();
        final LocalDateTime windowStart = dataRegistry.getWindowStart();
        final LocalDateTime windowEnd = dataRegistry.getWindowEnd();
        ArrayList<Leg> legs = new ArrayList<>();
        HashMap<Integer, ArrayList<Leg>> tailHashMap = new HashMap<>();

        // Cleanup unnecessary legs.
        Integer index = 0;
        for(Leg leg : inputLegs) {
            if(leg.getArrTime().isBefore(windowStart)
                    || leg.getDepTime().isAfter(windowEnd))
                continue;

            Integer tailId = leg.getOrigTailId();
            if(!tailIds.contains(tailId))
                continue;

            leg.setIndex(index);
            ++index;
            legs.add(leg);

            if(tailHashMap.containsKey(tailId))
                tailHashMap.get(tailId).add(leg);
            else {
                ArrayList<Leg> tailLegs = new ArrayList<>();
                tailLegs.add(leg);
                tailHashMap.put(tailId, tailLegs);
            }
        }

        dataRegistry.setLegs(legs);

        // build tails from schedule
        ArrayList<Tail> tails = new ArrayList<>();
        for(Map.Entry<Integer, ArrayList<Leg>> entry : tailHashMap.entrySet()) {
            ArrayList<Leg> tailLegs = entry.getValue();
            tailLegs.sort(Comparator.comparing(Leg::getDepTime));
            tails.add(new Tail(entry.getKey(), tailLegs));
        }

        tails.sort(Comparator.comparing(Tail::getId));
        for(int i = 0; i < tails.size(); ++i)
            tails.get(i).setIndex(i);

        dataRegistry.setTails(tails);
    }

    boolean disruptionExists() {
        for(Tail tail : dataRegistry.getTails()) {
            ArrayList<Leg> tailLegs = tail.getOrigSchedule();
            final Integer numLegs = tailLegs.size();
            if(numLegs <= 1)
                continue;

            for(int i = 0; i < numLegs - 1; ++i) {
                Leg currLeg = tailLegs.get(i);
                Leg nextLeg = tailLegs.get(i+1);

                final Integer turnTime = ((int) Duration.between(currLeg.getArrTime(),
                        nextLeg.getDepTime()).toMinutes());

                if(turnTime < currLeg.getTurnTimeInMin()) {
                    logger.info("turn time violated for legs " + currLeg.getId() + " and " + nextLeg.getId()
                            + " on tail " + tail.getId());
                    logger.info("expected turn time: " + currLeg.getTurnTimeInMin() + " actual: " + turnTime);
                    return true;
                }
            }
        }
        return false;
    }
}
