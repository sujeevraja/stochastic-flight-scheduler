package com.stochastic.dao;

import com.stochastic.domain.Leg;
import com.stochastic.utility.Utility;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class LegDAO {
    /**
     * Used to read relevant leg data from Schedule.xml
     */
    private ArrayList<Leg> legs;
    private DateTimeFormatter format;

    public LegDAO(String filePath) {
        try {
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
        } catch (Exception e ) {
            e.printStackTrace();
            System.exit(1);
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
        Integer fltNum = Utility.getInt(legElem, "fltNum");
        Integer tail = Utility.getInt(legElem, "tail");

        String depTimeStr = legElem.getElementsByTagName("depTime").item(0).getTextContent();
        LocalDateTime depTime = LocalDateTime.parse(depTimeStr, format);

        String arrTimeStr = legElem.getElementsByTagName("arrTime").item(0).getTextContent();
        LocalDateTime arrTime = LocalDateTime.parse(arrTimeStr, format);

        return new Leg(id, depPort, arrPort, turnTime, fltNum, tail, depTime, arrTime);
    }
}
