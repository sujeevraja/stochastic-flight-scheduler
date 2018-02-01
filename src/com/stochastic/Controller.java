package com.stochastic;

import com.stochastic.domain.Leg;
import com.stochastic.dao.EquipmentsDAO;
import com.stochastic.dao.ParametersDAO;
import com.stochastic.dao.ScheduleDAO;
import com.stochastic.registry.DataRegistry;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class Controller {
    /**
     * Class that controls the entire solution process from reading data to writing output.
     */
    private final static Logger logger = LogManager.getLogger(Controller.class);
    private DataRegistry dataRegistry;

    Controller() {
        dataRegistry = new DataRegistry();
    }

    final void readData() {
        logger.info("Started reading data...");
        String scenarioPath = getScenarioPath();

        // Read parameters
        ParametersDAO parametersDAO = new ParametersDAO(scenarioPath + "\\Parameters.xml");
        dataRegistry.setWindowStart(parametersDAO.getWindowStart());
        dataRegistry.setWindowEnd(parametersDAO.getWindowEnd());
        logger.info("Read parameter data.");

        // Read equipment data
        dataRegistry.setEquipment(new EquipmentsDAO(scenarioPath + "\\Equipments.xml").getEquipments().get(0));
        logger.info("Read equipment data.");

        // Read leg data and remove unnecessary legs
        ArrayList<Leg> legs = new ScheduleDAO(scenarioPath + "\\Schedule.xml").getLegs();
        dataRegistry.buildOrigSchedule(legs);
        logger.info("Built original schedule.");
        logger.info("Completed reading data.");
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
}
