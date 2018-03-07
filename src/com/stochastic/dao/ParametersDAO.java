package com.stochastic.dao;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.stochastic.utility.OptException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class ParametersDAO {
    /**
     * Used to read scenario parameters from Parameters.xml.
     */
    private final static Logger logger = LogManager.getLogger(ParametersDAO.class);
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private Integer maxFlightDelayInMin;

    public ParametersDAO(String filePath) throws OptException {
        try {
            File xmlFile = new File(filePath);
            System.out.println(" filePath: " + filePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            DateTimeFormatter format = DateTimeFormatter.ISO_DATE_TIME;

            String ws = doc.getElementsByTagName("windowStart").item(0).getTextContent();
            windowStart = LocalDateTime.parse(ws, format);

            String we = doc.getElementsByTagName("windowEnd").item(0).getTextContent();
            windowEnd = LocalDateTime.parse(we, format);

            String fltDelay = doc.getElementsByTagName("maxFlightDelayInMin").item(0).getTextContent();
            maxFlightDelayInMin = Integer.parseInt(fltDelay);
        } catch (ParserConfigurationException pce) {
            logger.error(pce);
            throw new OptException("Unable to create DocumentBuilder to read Parameters.xml");
        } catch (IOException ioe) {
            logger.error(ioe);
            throw new OptException("Unable to read Parameters.xml");
        } catch (SAXException se) {
            logger.error(se);
            throw new OptException("Possibly ill-formed xml in Parameters.xml");
        }
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }

    public Integer getMaxFlightDelayInMin() {
        return maxFlightDelayInMin;
    }
}
