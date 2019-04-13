package stochastic.solver;

import stochastic.domain.Leg;
import stochastic.domain.Tail;
import stochastic.network.Network;
import stochastic.network.Path;
import stochastic.registry.Parameters;
import stochastic.utility.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.utility.Enums;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

class PricingProblemSolver {
    /**
     * PricingProblemSolver uses label setting and partial path pruning to generate routes for the second-stage
     * model.
     */
    private final static Logger logger = LogManager.getLogger(SubSolverWrapper.class);

    private Enums.ReducedCostStrategy reducedCostStrategy;
    private int numReducedCostPaths;

    private Tail tail;
    private ArrayList<Leg> legs;
    private int numLegs;
    private Network network;
    private int[] delays; // delays[i] = total departure delay of legs[i] (a_{rf} in paper)

    // Dual values fromm latest solution of Second Stage Restricted Master Problem.
    private double tailDual; // \mu in paper, free
    private double[] legCoverDuals; // \nu in paper, free
    private double[] delayLinkDuals; // \pi in paper, <= 0

    // containers utilized during labeling procedure
    private ArrayList<ArrayList<Label>> labels; // labels[i] are labels ending at legs[i].
    private ArrayList<Label> sinkLabels; // labels ending at sink node.
    private PriorityQueue<Label> unprocessedLabels;

    PricingProblemSolver(Tail tail, ArrayList<Leg> legs, Network network, int[] delays,
                         double tailDual, double[] legCoverDuals, double[] delayLinkDuals) {
        this.reducedCostStrategy = Parameters.getReducedCostStrategy();
        this.numReducedCostPaths = Parameters.getNumReducedCostPaths();

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

        this.unprocessedLabels = new PriorityQueue<>();
        this.sinkLabels = new ArrayList<>();
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

        ArrayList<Path> paths = new ArrayList<>();
        int numPaths = sinkLabels.size();
        if (reducedCostStrategy == Enums.ReducedCostStrategy.BEST_PATHS) {
            sinkLabels.sort(Comparator.comparing(Label::getReducedCost));
            numPaths = Math.min(numPaths, numReducedCostPaths);
        }

        for (int i = 0; i < numPaths; ++i)
            paths.add(buildPathFromLabel(sinkLabels.get(i)));

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
        while (!unprocessedLabels.isEmpty()) {
            Label label = unprocessedLabels.remove();
            final int legIndex = label.getVertex();
            ArrayList<Integer> neighbors = network.getNeighbors(legIndex);
            if (neighbors != null) {
                generateFeasibleExtensions(label, neighbors);
                if (limitReached())
                    return;
            }
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

            final int totalDelay = delays[i];

            double reducedCost = getReducedCostForLeg(i, totalDelay);
            Label label = new Label(leg, null, totalDelay, reducedCost, legs.size());

            ArrayList<Label> legLabels = labels.get(i);
            if (!canAddTo(label, legLabels))
                continue;

            legLabels.add(label);
            unprocessedLabels.add(label);

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
            unprocessedLabels.add(extension);
            Leg nextLeg = legs.get(nextIndex);
            if (!nextLeg.getArrPort().equals(tail.getSinkPort()))
                continue;

            double pathReducedCost = extension.getReducedCost() - tailDual;
            if (pathReducedCost <= -Constants.EPS) {
                Label copy = new Label(extension);
                copy.setReducedCost(pathReducedCost);
                if (canAddTo(copy, sinkLabels)) {
                    sinkLabels.add(copy);
                    if (limitReached())
                        return;
                }
            }
        }
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

        double reducedCost = label.getReducedCost() + getReducedCostForLeg(legIndex, totalDelay);
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
        return reducedCostStrategy == Enums.ReducedCostStrategy.FIRST_PATHS &&
                sinkLabels.size() > Parameters.getNumReducedCostPaths();
    }
}
