package com.stochastic.dao;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.stochastic.bean.ParameterBean;
import org.w3c.dom.Document;

public class ParameterDAO {
    /**
     * Used to read scenario parameters from Parameters.xml.
     */
    private ParameterBean parameterBean;

    public ParameterDAO(String filePath) {
        try {
            File xmlFile = new File(filePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            DateTimeFormatter format = DateTimeFormatter.ISO_DATE_TIME;
            parameterBean = new ParameterBean();

            String windowStart = doc.getElementsByTagName("windowStart").item(0).getTextContent();
            parameterBean.setWindowStart(LocalDateTime.parse(windowStart, format));

            String windowEnd = doc.getElementsByTagName("windowEnd").item(0).getTextContent();
            parameterBean.setWindowEnd(LocalDateTime.parse(windowEnd, format));
        } catch (Exception e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public ParameterBean getParameterBean() {
        return parameterBean;
    }
}
