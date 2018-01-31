package com.stochastic;

import com.stochastic.domain.Leg;
import com.stochastic.registry.Parameters;
import com.stochastic.dao.EquipmentDAO;
import com.stochastic.dao.ParameterDAO;
import com.stochastic.dao.LegDAO;
import com.stochastic.registry.DataRegistry;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;

class Controller {
    /**
     * Class that controls the entire solution process from reading data to writing output.
     */

    private Parameters parameters;
    private DataRegistry dataRegistry;

    Controller() {
        parameters = null;
    }

    final void readData() {
        String scenarioPath = getScenarioPath();

        // Read parameters
        parameters = new ParameterDAO(scenarioPath + "\\Parameters.xml").getParameters();

        // Read equipment data
        dataRegistry = new DataRegistry();
        dataRegistry.setEquipments(new EquipmentDAO(scenarioPath + "\\Equipments.xml").getEquipments());
        dataRegistry.buildTailEqpMap();

        // Read leg data
        ArrayList<Leg> legs = new LegDAO(scenarioPath + "\\Schedule.xml").getLegs();
        int a = 1;
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
