package com.stochastic;

import com.stochastic.bean.ParameterBean;
import com.stochastic.dao.ParameterDAO;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

class Controller {
    /**
     * Class that controls the entire solution process from reading data to writing output.
     */

    private ParameterBean parameterBean;

    Controller() {
        parameterBean = null;
    }

    final void readData() {
        String scenarioPath = getScenarioPath();
        parameterBean = new ParameterDAO(scenarioPath + "\\Parameters.xml").getParameterBean();
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
