package com.stochastic.utility;

import org.w3c.dom.Element;

public class Utility {
    /**
     * Used to hold utility functions common to other packages.
     */

    public static Integer getInt(Element elem, String tag) {
        return Integer.parseInt(elem.getElementsByTagName(tag).item(0).getTextContent());
    }
}
