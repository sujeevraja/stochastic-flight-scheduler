package com.stochastic.network;

import com.stochastic.domain.Leg;
import java.util.ArrayList;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Network {
    /**
     * Class used to hold a connection network.
     * In such a network, flights are nodes, while arcs exist between two nodes only if
     * time and space connectivity are satisfied.
     * This network will be used to enumerate paths for a set partitioning model that will
     * be solved for each tail.
     */

    private final static Logger logger = LogManager.getLogger(Network.class);
    private ArrayList<Node> nodes;

    public Network(ArrayList<Leg> legs) {
        logger.info("Started building flight nodes...");
        nodes = new ArrayList<>();
        for(Leg leg : legs) {
            Node node = new Node(leg.getId(), leg.getDepTime(), leg.getArrTime(), leg.getDepPort(), leg.getArrPort());
            nodes.add(node);
        }
        logger.info("Completed building flight nodes.");
    }

    public void enumeratePaths() {
    }
}
