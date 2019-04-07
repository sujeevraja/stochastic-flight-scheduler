package stochastic.dao;

import stochastic.domain.Leg;
import stochastic.utility.OptException;
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
    private final static Logger logger = LogManager.getLogger(ScheduleDAO.class);
    private ArrayList<Leg> legs;
    private DateTimeFormatter format;

    public ScheduleDAO(String filePath) throws OptException {
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
        Integer id = getInt(legElem, "id");
        Integer depPort = getInt(legElem, "depPort");
        Integer arrPort = getInt(legElem, "arrPort");
        Integer turnTime = getInt(legElem, "turnTime");
        Integer tail = getInt(legElem, "tail");
        Integer fltNum = getInt(legElem, "fltNum");

        String depTimeStr = legElem.getElementsByTagName("depTime").item(0).getTextContent();
        LocalDateTime depTime = LocalDateTime.parse(depTimeStr, format);

        String arrTimeStr = legElem.getElementsByTagName("arrTime").item(0).getTextContent();
        LocalDateTime arrTime = LocalDateTime.parse(arrTimeStr, format);

        return new Leg(id, fltNum, depPort, arrPort, turnTime, tail, depTime, arrTime);
    }

    private static Integer getInt(Element elem, String tag) {
        return Integer.parseInt(elem.getElementsByTagName(tag).item(0).getTextContent());
    }
}
