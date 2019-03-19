package com.stochastic.solver;

import com.stochastic.utility.Constants;

class Label {
    /**
     * This class is used to track and prune partial paths in a label-setting algorithm.
     */
    private int vertex;
    private Label predecessor;
    private double reducedCost;
    private int propagatedDelay;

    /**
     * Note: The "dominates" function is valid only if the graph is a DAG (Directed Acyclic Graph).
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
     */
    boolean dominates(Label other) {
        if (vertex != other.vertex)
            return false;

        // TODO check if the inequalities should be the other way around.
        if (reducedCost > other.reducedCost)
            return false;

        boolean strict = reducedCost <= other.reducedCost - Constants.EPS;
        if (propagatedDelay > other.propagatedDelay)
            return false;

        if (!strict && propagatedDelay < other.propagatedDelay)
            strict = true;

        return strict;
    }
}
