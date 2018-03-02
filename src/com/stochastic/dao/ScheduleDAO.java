package com.stochastic.dao;

import com.stochastic.domain.Leg;
import com.stochastic.utility.OptException;
import com.stochastic.utility.Utility;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class ScheduleDAO {
    /**
     * Used to read relevant leg data from Schedule.xml
     */
    private final static Logger logger = LogManager.getLogger(EquipmentsDAO.class);
    private ArrayList<Leg> legs;
    private DateTimeFormatter format;
    private Integer maxFlightDelayInMin;

    public ScheduleDAO(String filePath, Integer maxFligtDelayInMin) throws OptException {
        try {
            this.maxFlightDelayInMin = maxFligtDelayInMin;
            File xmlFile = new File(filePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList nodeList = doc.getElementsByTagName("leg");

            legs = new ArrayList<>();
            format = DateTimeFormatter.ISO_DATE_TIME;
            for(int i = 0; i < nodeList.getLength(); ++i) {
                Node legNode = nodeList.item(i);
                legs.add(buildLeg((Element) legNode));
            }
        } catch (ParserConfigurationException pce) {
            logger.error(pce);
            throw new OptException("Unable to create DocumentBuilder to read Schedule.xml");
        } catch (IOException ioe) {
            logger.error(ioe);
            throw new OptException("Unable to read Schedule.xml");
        } catch (SAXException se) {
            logger.error(se);
            throw new OptException("Possibly ill-formed xml in Schedule.xml");
        }
    }

    public ArrayList<Leg> getLegs() {
        return legs;
    }

    private Leg buildLeg(Element legElem) {
        Integer id = Utility.getInt(legElem, "id");
        Integer depPort = Utility.getInt(legElem, "depPort");
        Integer arrPort = Utility.getInt(legElem, "arrPort");
        Integer turnTime = Utility.getInt(legElem, "turnTime");
        Integer tail = Utility.getInt(legElem, "tail");
        Integer fltNum = Utility.getInt(legElem, "fltNum");

        String depTimeStr = legElem.getElementsByTagName("depTime").item(0).getTextContent();
        LocalDateTime depTime = LocalDateTime.parse(depTimeStr, format);
        LocalDateTime latestDepTime = depTime.plusMinutes(maxFlightDelayInMin);

        String arrTimeStr = legElem.getElementsByTagName("arrTime").item(0).getTextContent();
        LocalDateTime arrTime = LocalDateTime.parse(arrTimeStr, format);

        return new Leg(id, fltNum, depPort, arrPort, turnTime, tail, depTime, arrTime, latestDepTime);
    }
}
