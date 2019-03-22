package com.stochastic.utility;

import org.w3c.dom.Element;

public class Utility {
    /**
     * Used to hold utility functions common to other packages.
     */

    public static Integer getInt(Element elem, String tag) {
        return Integer.parseInt(elem.getElementsByTagName(tag).item(0).getTextContent());
    }

    public static double roundUp(double val, int precision) {
        return Math.ceil(val * Math.pow(10, precision)) / Math.pow(10, precision);
    }

    public static double roundDown(double val, int precision) {
        return Math.floor(val * Math.pow(10, precision)) / Math.pow(10, precision);
    }
}
