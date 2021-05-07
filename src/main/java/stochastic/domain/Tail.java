package stochastic.domain;

import java.util.ArrayList;

public class Tail {
    /**
     * Class used to hold individual aircraft and route data.
     * Assumed that original schedule is always non-empty.
     */
    private final Integer id;
    private Integer index;
    private final ArrayList<Leg> origSchedule;
    private final Integer sourcePort;
    private final Integer sinkPort;

    public Tail(Integer id, ArrayList<Leg> origSchedule) {
        this.id = id;
        this.index = null;
        this.origSchedule = origSchedule;
        sourcePort = origSchedule.get(0).getDepPort();
        sinkPort = origSchedule.get(origSchedule.size() - 1).getArrPort();
    }

    public Integer getId() {
        return id;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public Integer getIndex() {
        return index;
    }

    public ArrayList<Leg> getOrigSchedule() {
        return origSchedule;
    }

    public Integer getSourcePort() {
        return sourcePort;
    }

    public Integer getSinkPort() {
        return sinkPort;
    }

    @Override
    public String toString() {
        return "Tail(" + id + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Tail other = (Tail) obj;
        if (id == null) {
            return other.id == null;
        } else return id.equals(other.id);
    }


}
