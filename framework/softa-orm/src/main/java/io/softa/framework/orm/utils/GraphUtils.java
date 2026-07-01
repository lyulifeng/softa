package io.softa.framework.orm.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generic directed-graph algorithms over an adjacency map ({@code node -> its successors}). Pure and
 * domain-agnostic: callers build the graph and phrase their own errors from the returned findings.
 *
 * <p>Both algorithms are a single DFS, {@code O(V + E)}. Used by {@code ModelManager} to validate the
 * {@code onDelete=CASCADE} graph (no cycle; bounded chain depth), but nothing here is CASCADE-specific.
 */
public final class GraphUtils {

    private GraphUtils() {}

    /**
     * Find a cycle (including a self-loop) in the directed graph.
     *
     * @param graph adjacency map {@code node -> successors} (a node absent as a key = no successors)
     * @param <T>   node type (its {@code equals}/{@code hashCode} identify a node)
     * @return the cycle as an ordered, closed path {@code [n, …, n]} (first == last), or an empty list
     *         if the graph is acyclic
     */
    public static <T> List<T> findCycle(Map<T, List<T>> graph) {
        Set<T> onPath = new LinkedHashSet<>();   // nodes on the current DFS path, in order (for the cycle)
        Set<T> cleared = new HashSet<>();         // fully explored, proven cycle-free — never revisited
        for (T node : graph.keySet()) {
            List<T> cycle = findCycleFrom(node, graph, onPath, cleared);
            if (cycle != null) {
                return cycle;
            }
        }
        return List.of();
    }

    private static <T> List<T> findCycleFrom(T node, Map<T, List<T>> graph, Set<T> onPath, Set<T> cleared) {
        if (cleared.contains(node)) {
            return null;
        }
        if (!onPath.add(node)) {
            // `node` is already on the current path → cycle. onPath is ordered, so the suffix from `node`
            // onward, closed back to `node`, IS the cycle.
            List<T> cycle = new ArrayList<>(onPath.stream().dropWhile(n -> !n.equals(node)).toList());
            cycle.add(node);
            return cycle;
        }
        for (T next : graph.getOrDefault(node, List.of())) {
            List<T> cycle = findCycleFrom(next, graph, onPath, cleared);
            if (cycle != null) {
                return cycle;   // propagate up; onPath left as-is (traversal aborts)
            }
        }
        onPath.remove(node);
        cleared.add(node);
        return null;
    }

    /**
     * The longest path (most nodes) in a DAG, as an ordered node list — a DAG longest-path DP that
     * correctly handles re-convergence (diamonds are memoized, not double-counted).
     *
     * @param graph adjacency map {@code node -> successors}
     * @param <T>   node type
     * @return the longest path (empty for an empty graph)
     * @throws IllegalArgumentException if the graph has a cycle ("longest" is then ill-defined) — call
     *                                  {@link #findCycle} first
     */
    public static <T> List<T> longestPath(Map<T, List<T>> graph) {
        Map<T, Integer> depth = new HashMap<>();   // node -> longest path length starting at it
        T deepest = null;
        int max = 0;
        for (T node : graph.keySet()) {
            int d = depthFrom(node, graph, depth, new LinkedHashSet<>());
            if (d > max) {
                max = d;
                deepest = node;
            }
        }
        if (deepest == null) {
            return List.of();
        }
        // Reconstruct: from the deepest root, follow the child whose depth is exactly one less, each step.
        List<T> path = new ArrayList<>();
        for (T cur = deepest; cur != null; ) {
            path.add(cur);
            T nextDeepest = null;
            for (T child : graph.getOrDefault(cur, List.of())) {
                Integer cd = depth.get(child);
                if (cd != null && cd == depth.get(cur) - 1) {
                    nextDeepest = child;
                    break;
                }
            }
            cur = nextDeepest;
        }
        return path;
    }

    private static <T> int depthFrom(T node, Map<T, List<T>> graph, Map<T, Integer> depth, Set<T> onPath) {
        Integer memo = depth.get(node);
        if (memo != null) {
            return memo;
        }
        if (!onPath.add(node)) {
            throw new IllegalArgumentException(
                    "longestPath requires an acyclic graph; cycle reached at node " + node);
        }
        int childMax = 0;
        for (T next : graph.getOrDefault(node, List.of())) {
            childMax = Math.max(childMax, depthFrom(next, graph, depth, onPath));
        }
        onPath.remove(node);
        int d = childMax + 1;
        depth.put(node, d);
        return d;
    }
}
