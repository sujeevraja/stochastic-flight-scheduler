package com.stochastic.dao;

import com.stochastic.domain.Equipment;
import com.stochastic.utility.Utility;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class EquipmentsDAO {
    /**
     * Used to load equipment and tail data.
     */
    private ArrayList<Equipment> equipments;

    public EquipmentsDAO(String filePath) {
        try {
            File xmlFile = new File(filePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();
            NodeList nodeList = doc.getElementsByTagName("eqp");


            equipments = new ArrayList<>();
            for(int i = 0; i < nodeList.getLength(); ++i) {
                Element eqpElem = (Element) nodeList.item(i);
                Equipment eqp = buildEquipment((eqpElem));
                equipments.add(eqp);
            }
        } catch (Exception e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public ArrayList<Equipment> getEquipments() {
        return equipments;
    }

    private Equipment buildEquipment(Element eqpElem) {
        Integer id = Utility.getInt(eqpElem, "id");
        Integer capacity = Utility.getInt(eqpElem, "capacity");
        ArrayList<Integer> tails = new ArrayList<>();

        NodeList tailList = eqpElem.getElementsByTagName("tail");
        for(int i = 0; i < tailList.getLength(); ++i) {
            Element tailElem = (Element) tailList.item(i);
            Integer tailId = Integer.parseInt(tailElem.getTextContent());
            if(tailId == 10001 || tailId == 10010)
                tails.add(tailId);

            // read in a small subset of data for testing purposes.
            // if (i == 4)
            //    break;
        }

        Collections.sort(tails);
        return new Equipment(id, capacity, tails);
    }
}
