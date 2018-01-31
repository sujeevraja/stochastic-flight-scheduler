package com.stochastic.registry;

import java.time.LocalDateTime;

public class Parameters {
    /**
     * used to store data from Parameters.xml.
     */

    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;

    public Parameters() {
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
