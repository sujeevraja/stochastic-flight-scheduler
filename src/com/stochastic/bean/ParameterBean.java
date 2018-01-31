package com.stochastic.bean;

import java.time.LocalDateTime;

public class ParameterBean {
    /**
     * used to store data from Parameters.xml.
     */

    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;

    public ParameterBean() {
        windowStart = null;
        windowEnd = null;
    }

    public void setWindowStart(LocalDateTime windowStart) {
        this.windowStart = windowStart;
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public void setWindowEnd(LocalDateTime windowEnd) {
        this.windowEnd = windowEnd;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }
}
