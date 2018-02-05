package com.stochastic.controller;

import com.stochastic.domain.Leg;
import com.stochastic.dao.EquipmentsDAO;
import com.stochastic.dao.ParametersDAO;
import com.stochastic.dao.ScheduleDAO;
import com.stochastic.domain.Tail;
import com.stochastic.registry.DataRegistry;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Controller {
    /**
     * Class that controls the entire solution process from reading data to writing output.
     */
    private final static Logger logger = LogManager.getLogger(Controller.class);
    private DataRegistry dataRegistry;

    public Controller() {
        dataRegistry = new DataRegistry();
    }

    public final void readData() {
        logger.info("Started reading data...");
        String scenarioPath = getScenarioPath();

        // Read parameters
        ParametersDAO parametersDAO = new ParametersDAO(scenarioPath + "\\Parameters.xml");
        dataRegistry.setWindowStart(parametersDAO.getWindowStart());
        dataRegistry.setWindowEnd(parametersDAO.getWindowEnd());
        logger.info("Completed reading parameter data from Parameters.xml.");

        // Read equipment data
        dataRegistry.setEquipment(new EquipmentsDAO(scenarioPath + "\\Equipments.xml").getEquipments().get(0));
        logger.info("Completed reading equipment data from Equipments.xml.");

        // Read leg data and remove unnecessary legs
        ArrayList<Leg> legs = new ScheduleDAO(scenarioPath + "\\Schedule.xml").getLegs();
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

    public final void solveSecondStage() {
        SecondStageController ssc = new SecondStageController(dataRegistry.getLegs(), dataRegistry.getTails(),
                dataRegistry.getWindowStart(), dataRegistry.getWindowEnd());

        if(ssc.disruptionExists()) {
            ssc.solve();
        }
        else
            logger.warn("Calling second-stage solver without any disruptions.");
    }

    private String getScenarioPath() {
        try {
            File xmlFile = new File("config.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            Node scenarioNode = doc.getElementsByTagName("scenarioPath").item(0);
            return scenarioNode.getTextContent();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    private void storeLegs(ArrayList<Leg> inputLegs) {
        final ArrayList<Integer> tailIds = dataRegistry.getEquipment().getTailIds();
        final LocalDateTime windowStart = dataRegistry.getWindowStart();
        final LocalDateTime windowEnd = dataRegistry.getWindowEnd();
        ArrayList<Leg> legs = new ArrayList<>();
        HashMap<Integer, Leg> legHashMap = new HashMap<>();
        HashMap<Integer, ArrayList<Leg>> tailHashMap = new HashMap<>();

        // Cleanup unnecessary legs.
        for(Leg leg : inputLegs) {
            if(leg.getArrTime().isBefore(windowStart)
                    || leg.getDepTime().isAfter(windowEnd))
                continue;

            Integer tailId = leg.getOrigTail();
            if(!tailIds.contains(tailId))
                continue;

            legs.add(leg);
            legHashMap.put(leg.getId(), leg);

            if(tailHashMap.containsKey(tailId))
                tailHashMap.get(tailId).add(leg);
            else {
                ArrayList<Leg> tailLegs = new ArrayList<>();
                tailLegs.add(leg);
                tailHashMap.put(tailId, tailLegs);
            }
        }

        dataRegistry.setLegs(legs);
        dataRegistry.setLegHashMap(legHashMap);

        // build tails from schedule
        ArrayList<Tail> tails = new ArrayList<>();
        for(Map.Entry<Integer, ArrayList<Leg>> entry : tailHashMap.entrySet()) {
            ArrayList<Leg> tailLegs = entry.getValue();
            tailLegs.sort(Comparator.comparing(Leg::getDepTime));
            tails.add(new Tail(entry.getKey(), tailLegs));
        }

        tails.sort(Comparator.comparing(Tail::getId));
        dataRegistry.setTails(tails);
    }
}
