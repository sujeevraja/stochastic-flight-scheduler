package com.stochastic.dao;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.w3c.dom.Document;

public class ParametersDAO {
    /**
     * Used to read scenario parameters from Parameters.xml.
     */
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;

    public ParametersDAO(String filePath) {
        try {
            File xmlFile = new File(filePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            DateTimeFormatter format = DateTimeFormatter.ISO_DATE_TIME;

            String ws = doc.getElementsByTagName("windowStart").item(0).getTextContent();
            windowStart = LocalDateTime.parse(ws, format);

            String we = doc.getElementsByTagName("windowEnd").item(0).getTextContent();
            windowEnd = LocalDateTime.parse(we, format);
        } catch (Exception e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }
}
