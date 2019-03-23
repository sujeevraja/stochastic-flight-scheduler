package com.stochastic.solver;

import com.stochastic.domain.Leg;
import com.stochastic.domain.Tail;
import com.stochastic.network.Network;
import com.stochastic.network.Path;
import com.stochastic.registry.Parameters;
import com.stochastic.utility.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

class PricingProblemSolver {
    /**
     * PricingProblemSolver uses label setting and partial path pruning to generate routes for the second-stage
     * model.
     */
    private final static Logger logger = LogManager.getLogger(SubSolverWrapper.class);
    private Tail tail;
    private ArrayList<Leg> legs;
    private int numLegs;
    private Network network;
    private int[] delays; // delays[i] = total departure delay of legs[i] (a_{rf} in paper)

    // Dual values fromm latest solution of Second Stage Restricted Master Problem.
    private double tailDual;
    private double[] legCoverDuals; // \beta in paper, free
    private double[] delayLinkDuals; // \gamma in paper, <= 0

    // containers utilized during labeling procedure
    private ArrayList<ArrayList<Label>> labels; // labels[i] are labels ending at legs[i].
    private ArrayList<Label> sinkLabels; // labels ending at sink node.
    private ArrayList<Label> newSinkLabels; // labels ending at sink node for new paths.

    PricingProblemSolver(Tail tail, ArrayList<Leg> legs, Network network, int[] delays,
                         double tailDual, double[] legCoverDuals, double[] delayLinkDuals) {
        this.tail = tail;
        this.legs = legs;
        this.numLegs = legs.size();
        this.network = network;
        this.delays = delays;

        this.tailDual = tailDual;
        this.legCoverDuals = legCoverDuals;
        this.delayLinkDuals = delayLinkDuals;

        this.labels = new ArrayList<>();
        for (int i = 0; i < numLegs; ++i)
            labels.add(new ArrayList<>());

        this.sinkLabels = new ArrayList<>();
        this.newSinkLabels = new ArrayList<>();
    }

    /**
     * Given a list of paths that have already been generated, this function builds labels by following the legs and
     * delay times on each path and populates the sinkLabels container with them. This helps prune out dominated
     * (especially duplicate) paths.
     *
     * @param paths list of paths that have already been generated.
     */
    void initLabelsForExistingPaths(ArrayList<Path> paths) {
        for (Path path : paths) {
            ArrayList<Leg> pathLegs = path.getLegs();
            if (pathLegs.isEmpty())
                continue;

            Leg srcLeg = pathLegs.get(0);
            int srcIndex = srcLeg.getIndex();
            int totalDelay = delays[srcIndex];
            double reducedCost = getReducedCostForLeg(srcLeg.getIndex(), totalDelay);

            Label label = new Label(srcLeg, null, totalDelay, reducedCost, legs.size());
            labels.get(srcIndex).add(label);

            // TODO check if we need to add only non-dominated labels from paths.
            for (int i = 1; i < pathLegs.size(); ++i) {
                Leg leg = pathLegs.get(i);
                label = extend(label, leg.getIndex());
                labels.get(leg.getIndex()).add(label);
            }

            // Add the last label as a sink label.
            Label copy = new Label(label);
            copy.setReducedCost(label.getReducedCost() - tailDual);
            copy.setPreExisting();
            sinkLabels.add(copy);
        }
    }

    /**
     * Builds paths for the given tail using the label setting algorithm.
     *
     * Note that only negative reduced cost paths built from non-dominated labels will be provided.
     *
     * @return ArrayList with generated paths (possibly empty).
     */
    ArrayList<Path> generatePathsForTail() {
        initSourceLabels();
        if (!limitReached())
            runLabelSettingAlgorithm();

        /*
        ArrayList<Path> paths = new ArrayList<>();
        for (Label sinkLabel : sinkLabels) {
            if (!sinkLabel.isPreExisting()) {
                Path path = buildPathFromLabel(sinkLabel);
                paths.add(path);
            }
        }
        */

        /*
        ArrayList<Label> newLabels = new ArrayList<>();
        for (Label sinkLabel : sinkLabels)
            if (!sinkLabel.isPreExisting())
                newLabels.add(sinkLabel);

        newLabels.sort(Comparator.comparing(Label::getReducedCost));

        ArrayList<Path> paths = new ArrayList<>();
        int numPaths = Math.min(50, newLabels.size());
        for (int i = 0;  i < numPaths; ++i)
            paths.add(buildPathFromLabel(newLabels.get(i)));
            */

        /*
        newSinkLabels.sort(Comparator.comparing(Label::getReducedCost));
        int numPaths = Math.min(Parameters.getNumReducedCostPaths(), newSinkLabels.size());
        ArrayList<Path> paths = new ArrayList<>();
        for (int i = 0; i < numPaths;  ++i)
            paths.add(buildPathFromLabel(newSinkLabels.get(i)));
            */

        ArrayList<Path> paths = new ArrayList<>();
        for (Label label : newSinkLabels)
            paths.add(buildPathFromLabel(label));

        return paths;
    }

    /**
     * Builds a path by traversing predecessors until none can be found.
     *
     * @param label The sink label from which we will do a backwards traversal to build the path.
     * @return the built Path object.
     */
    private Path buildPathFromLabel(Label label) {
        ArrayList<Leg> pathLegs = new ArrayList<>();
        ArrayList<Integer> pathDelays = new ArrayList<>();

        while(label != null) {
            pathLegs.add(label.getLeg());
            pathDelays.add(label.getTotalDelay());
            label = label.getPredecessor();
        }

        Path path = new Path(tail);
        for(int i = pathLegs.size() - 1; i >= 0; --i) {
            path.addLeg(pathLegs.get(i), pathDelays.get(i));
        }
        return path;
    }

    /**
     * Runs the forward label setting algorithm to solve the pricing problem for the second-stage.
     *
     * Using the labels created in "initSourceLabels", this algorithm builds and adds feasible extensions for each
     * unprcessed label until no more extensions are possible. A label is said to be unprocessed if we have not checked
     * it for feasible extension. It is processed when we have built all of its possible feasible extensions. This is
     * marked using the "processed" flag in the Label object. The initial labels and feasible extensions are stored in
     * the "labels" member. Along the way, this also creates a set of * non-dominated labels that can connect to the
     * tail's sink port (stored in "sinkLabels"). The non-dominated sink-labels can be used to build and provide paths
     * with negative reduced cost. These paths can then in turn be * added to the Restricted Master Problem (RMP) of
     * the second-stage. We will reach optimality if we cannot find any sink label with a negative reduced cost.
     */
    private void runLabelSettingAlgorithm() {
        Label label = getBestLabelToExtend();
        while (label != null) {
            Integer legIndex = label.getVertex();
            ArrayList<Integer> neighbors = network.getNeighbors(legIndex);
            if (neighbors != null) {
                generateFeasibleExtensions(label, neighbors);
                if (limitReached())
                    return;
            }
            label.setProcessed();
            label = getBestLabelToExtend();
        }
    }

    /**
     * Creates initial labels for flights that can connect to the given tail's source port. These labels will be used
     * to build feasible extensions in "runLabelSettingAlgorithm()".
     */
    private void initSourceLabels() {
        for (int i = 0; i < numLegs; ++i) {
            Leg leg = legs.get(i);
            if (!tail.getSourcePort().equals(leg.getDepPort()))
                continue;

            int totalDelay = delays[i];

            double reducedCost = getReducedCostForLeg(i, totalDelay);
            Label label = new Label(leg, null, totalDelay, reducedCost, legs.size());

            ArrayList<Label> legLabels = labels.get(i);
            if (!canAddTo(label, legLabels))
                continue;

            legLabels.add(label);

            // reduced cost for path = (sum of flight reduced costs) - \alpha_t
            if (!leg.getArrPort().equals(tail.getSinkPort()))
                continue;

            double pathReducedCost = label.getReducedCost() - tailDual;
            if (pathReducedCost >= -Constants.EPS)
                continue;

            Label copy = new Label(label);
            copy.setReducedCost(pathReducedCost);
            if (canAddTo(copy, sinkLabels)) {
                sinkLabels.add(copy);
                newSinkLabels.add(copy);
                if (limitReached())
                    return;
            }
        }
    }

    /**
     * Generates and stores all feasible extensions of the given label.
     *
     * @param label label to be extended.
     * @param neighbors candidates for feasible extensions.
     */
    private void generateFeasibleExtensions(Label label, ArrayList<Integer> neighbors) {
        for (Integer nextIndex : neighbors) {
            Label extension = extend(label, nextIndex);
            if (!canAddTo(extension, labels.get(nextIndex)))
                continue;

            labels.get(nextIndex).add(extension);
            Leg nextLeg = legs.get(nextIndex);
            if (!nextLeg.getArrPort().equals(tail.getSinkPort()))
                continue;

            Label copy = new Label(extension);
            double pathReducedCost = extension.getReducedCost() - tailDual;
            if (pathReducedCost <= -Constants.EPS) {
                copy.setReducedCost(pathReducedCost);
                if (canAddTo(copy, sinkLabels)) {
                    sinkLabels.add(copy);
                    newSinkLabels.add(copy);
                    if (limitReached())
                        return;
                }
            }
        }
    }

    /**
     * Finds the least reduced cost label among all vertices.
     *
     * @return the found label.
     */
    private Label getBestLabelToExtend() {
        double minReducedCost = Constants.INFINITY;
        Label bestLabel = null;
        for (ArrayList<Label> legLabels : labels) {
            for (Label label : legLabels) {
                if (!label.isProcessed() && label.getReducedCost() <= minReducedCost - Constants.EPS) {
                    minReducedCost = label.getReducedCost();
                    bestLabel = label;
                }
            }
        }
        return bestLabel;
    }

    /**
     * Create a new label as a forward extension of label using the given legIndex.
     *
     * @param label label to be extended forward (similar to appending to a path).
     * @param legIndex vertex of the new label.
     * @return extended Label object (will be a new object).
     */
    private Label extend(Label label, int legIndex) {
        Leg nextLeg = legs.get(legIndex);
        LocalDateTime nextLegDepTime = nextLeg.getDepTime().plusMinutes(delays[legIndex]);

        Leg prevLeg = legs.get(label.getVertex());
        LocalDateTime prevLegEndTime = prevLeg.getArrTime()
                .plusMinutes(label.getTotalDelay())
                .plusMinutes(prevLeg.getTurnTimeInMin());

        if (nextLegDepTime.isBefore(prevLegEndTime))
            nextLegDepTime = prevLegEndTime;

        int totalDelay = (int) Duration.between(nextLeg.getDepTime(), nextLegDepTime).toMinutes();

        // reduced cost for flight f = - \beta_f - (a_{rf} * \gamma_f)
        double reducedCost = (label.getReducedCost() - legCoverDuals[legIndex] -
                (totalDelay * delayLinkDuals[legIndex]));

        return label.extend(nextLeg, totalDelay, reducedCost);
    }

    /**
     * Returns true if label is dominated by any element of labels, false otherwise.
     *
     * @param label The label we want to add to labels if it is not dominated.
     * @param labels The labels that we have alredy created/processed.
     * @return true if label can be added to labels, false otherwise.
     */
    private boolean canAddTo(Label label, ArrayList<Label> labels)  {
        for (Label existingLabel : labels) {
            if (existingLabel.dominates(label))
                return false;
        }
        return true;
    }

    /**
     * Calculates the reduced cost of the given leg using the provided delay value.
     *
     * @param legIndex position of leg in legs list.
     * @param totalDelay delay time of leg (includes primary and propagated delays).
     * @return reduced cost of leg.
     */
    private double getReducedCostForLeg(int legIndex, int totalDelay) {
        // reduced cost for flight f = - \beta_f - (a_{rf} * \gamma_f)
        return -(legCoverDuals[legIndex] + (totalDelay * delayLinkDuals[legIndex]));
    }

    private boolean limitReached() {
        return newSinkLabels.size() > Parameters.getNumReducedCostPaths();
    }
}
