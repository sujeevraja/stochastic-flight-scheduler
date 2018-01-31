package com.stochastic;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.w3c.dom.Document;
import org.w3c.dom.Node;


public class Main {
    /**
     * Only responsibility is to own main().
     */
    private final static Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            File xmlFile = new File("config.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);

            System.out.println("root: " + doc.getDocumentElement().getNodeName());

            Node scenarioNode = doc.getElementsByTagName("scenarioPath").item(0);
            System.out.println(scenarioNode.getTextContent());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

