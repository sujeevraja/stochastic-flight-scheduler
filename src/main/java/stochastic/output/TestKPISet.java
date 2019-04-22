package stochastic.output;

import stochastic.utility.Enums;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds KPIs generated for a delay solution.
 */
public class TestKPISet {
    private Map<Enums.TestKPI, Double> kpis;

    TestKPISet() {
        kpis = new LinkedHashMap<>();
        for (Enums.TestKPI kpi : Enums.TestKPI.values())
            kpis.put(kpi, 0.0);
    }

    void setKpi(Enums.TestKPI kpi, Double value) {
        kpis.replace(kpi, value);
    }

    public Double getKpi(Enums.TestKPI kpi) {
        return kpis.get(kpi);
    }

    void storeAverageKPIs(TestKPISet[] scenarioKPIs) {
        for (TestKPISet testKPISet : scenarioKPIs)
            for (Enums.TestKPI kpi : Enums.TestKPI.values())
                kpis.replace(kpi, kpis.get(kpi) + testKPISet.kpis.get(kpi));

        for (Enums.TestKPI kpi : Enums.TestKPI.values())
            kpis.replace(kpi, kpis.get(kpi) / scenarioKPIs.length);
    }

    public static TestKPISet getPercentageDecrease(TestKPISet base, TestKPISet adjusted) {
        TestKPISet percentDecreaseSet = new TestKPISet();
        for (Enums.TestKPI kpi : Enums.TestKPI.values()) {
            double baseVal = base.kpis.get(kpi);
            double adjustedVal = adjusted.kpis.get(kpi);
            double decrease = ((baseVal - adjustedVal) / baseVal) * 100.0;
            percentDecreaseSet.setKpi(kpi, decrease);
        }
        return percentDecreaseSet;
    }
}
