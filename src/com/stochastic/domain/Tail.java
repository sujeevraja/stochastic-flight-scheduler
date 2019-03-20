package com.stochastic.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;

public class Tail {
    /**
     * Class used to hold individual aircraft and route data.
     * Assumed that original schedule is always non-empty.
     */
    private Integer id;
    private Integer index;
    private ArrayList<Leg> origSchedule;
    private Integer sourcePort;
    private Integer sinkPort;
    private LocalDateTime sourceTime;
    private LocalDateTime sinkTime;

    public Tail(Integer id, ArrayList<Leg> origSchedule) {
        this.id = id;
        this.index = null;
        this.origSchedule = origSchedule;
        sourcePort = origSchedule.get(0).getDepPort();
        sinkPort = origSchedule.get(origSchedule.size() - 1).getArrPort();
        sourceTime = origSchedule.get(0).getDepTime();
        sinkTime = origSchedule.get(origSchedule.size() - 1).getArrTime();
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

    public LocalDateTime getSourceTime() {
        return sourceTime;
    }

    public LocalDateTime getSinkTime() {
        return sinkTime;
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
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}  
    
    
}
