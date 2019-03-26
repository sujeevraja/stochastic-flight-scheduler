package com.stochastic.solver;

import com.stochastic.domain.Leg;
import com.stochastic.utility.Constants;

import java.util.Arrays;

class Label implements Comparable<Label> {
    /**
     * This class is used to track and prune partial paths in a label-setting algorithm.
     */
    private Leg leg;
    private int vertex;
    private Label predecessor;
    private int totalDelay;
    private double reducedCost;
    private boolean[] visited;

    Label(Leg leg, Label predecessor, int totalDelay, double reducedCost, int numLegs) {
        this.leg = leg;
        this.vertex = leg.getIndex();
        this.predecessor = predecessor;
        this.totalDelay = totalDelay;
        this.reducedCost = reducedCost;

        visited = new boolean[numLegs];
        Arrays.fill(visited, false);
        visited[vertex] = true;
    }

    Label(Label other) {
        this.leg = other.leg;
        this.vertex = other.vertex;
        this.predecessor = other.predecessor;
        this.totalDelay = other.totalDelay;
        this.reducedCost = other.reducedCost;
        this.visited = other.visited.clone();
    }

    @Override
    public String toString() {
        return "Label(" + leg.getId() + ", " + reducedCost;
    }

    public Leg getLeg() {
        return leg;
    }

    public int getVertex() {
        return vertex;
    }

    public Label getPredecessor() {
        return predecessor;
    }

    int getTotalDelay() {
        return totalDelay;
    }

    void setReducedCost(double reducedCost) {
        this.reducedCost = reducedCost;
    }

    double getReducedCost() {
        return reducedCost;
    }

    /**
     * The dominance condition here is valid only if the graph is a DAG (Directed Acyclic Graph).
     * Reason is the following:
     *  - Consider paths p1 = {1, 2, 3, 4} and p2 = {1, 4}.
     *  - Assume p2 is better in terms of both reduced cost and propagated delay in p1.
     *  - Consider any flight f that can be appended to p2.
     *  - If the graph is a DAG, f cannot be 1, 2, 3 or 4 (we know 2, 3 precede 4 and so cannot be after 4).
     *  - In this case, dominance works: {p2, f} will always be better than {p1, f}.
     *  - However, if cycles are allowed, this doesn't work.
     *
     * Cyclic graph example:
     *  - Say you have two paths p1 = {1, 2, 3, 4} and p2 = {1, 4}.
     *  - Let p2 dominate p1. Then, any {p2, v} should be better than {p1, v} for v =/= 1, 2, 3, 4.
     *  - What about v in p1 \ p2? We want to prune p1 out and just use p2 at 4.
     *  - If {p2, v} dominates {p1, v}, we are ok as we will preserve p2.
     *  - Consider a simple case with 1 tail.
     *  - Say p3 = {1, 2, 3, 4, 5} is the optimal solution.
     *  - Say we have p2 = {1, 4} dominating p1.
     *  - At 4, p2 will dominate p1. So, p1 may be thrown away. We can only build {1, 4, 3, 2, 5}.
     *  - So, it is possible to throw out the optimal solution.
     *
     * @param other the label with which we want to check the dominance condition.
     * @return true if this dominates other, false otherwise.
     */
    boolean dominates(Label other) {
        if (vertex != other.vertex)
            return false;

        // TODO check if the inequalities should be the other way around.
        if (reducedCost > other.reducedCost)
            return false;

        boolean strict = reducedCost <= other.reducedCost - Constants.EPS;
        if (totalDelay > other.totalDelay)
            return false;

        if (!strict && totalDelay < other.totalDelay)
            strict = true;

        return strict || Arrays.equals(visited, other.visited);
    }

    Label extend(Leg nextLeg, int totalDelay, double reducedCost) {
        Label extension = new Label(this);
        extension.leg = nextLeg;
        extension.vertex = nextLeg.getIndex();
        extension.predecessor = this;
        extension.totalDelay = totalDelay;
        extension.reducedCost = reducedCost;
        extension.visited[nextLeg.getIndex()] = true;
        return extension;
    }

    /**
     * "compareTo" is used only to populate a priority queue of unprocessed labels such that we always get the least
     * reduced cost label.
     *
     * @param other label to compare "this" with.
     * @return 1 if this has a lower reduced cost than other, 0 if equal and 1 otherwise.
     */
    @Override
    public int compareTo(Label other) {
        if (reducedCost >= other.reducedCost + Constants.EPS)
            return 1;

        if (reducedCost <= other.reducedCost - Constants.EPS)
            return -1;

        return 0;
    }
}
