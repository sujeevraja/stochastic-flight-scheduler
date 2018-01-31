package com.stochastic.dao;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.stochastic.registry.Parameters;
import org.w3c.dom.Document;

public class ParameterDAO {
    /**
     * Used to read scenario parameters from Parameters.xml.
     */
    private Parameters parameters;

    public ParameterDAO(String filePath) {
        try {
            File xmlFile = new File(filePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            DateTimeFormatter format = DateTimeFormatter.ISO_DATE_TIME;
            parameters = new Parameters();

            String windowStart = doc.getElementsByTagName("windowStart").item(0).getTextContent();
            parameters.setWindowStart(LocalDateTime.parse(windowStart, format));

            String windowEnd = doc.getElementsByTagName("windowEnd").item(0).getTextContent();
            parameters.setWindowEnd(LocalDateTime.parse(windowEnd, format));
        } catch (Exception e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public Parameters getParameters() {
        return parameters;
    }
}
