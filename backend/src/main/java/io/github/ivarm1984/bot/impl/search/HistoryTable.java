package io.github.ivarm1984.bot.impl.search;

import io.github.ivarm1984.engine.Move;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * History heuristic (Schaeffer 1989): a per-search table scoring moves by how often they've
 * triggered a beta cutoff elsewhere in the tree, weighted by the depth the cutoff happened at (a
 * cutoff found deeper in the tree says more about a move's general strength than one found a ply
 * from a leaf). Ordering candidates by this score - ahead of the raw move-generation order
 * {@link NegamaxSearch} falls back to at non-root nodes - is a real, cheap improvement: unlike
 * re-scoring every candidate with the static evaluator (as the root ordering does), a table lookup
 * costs nothing per move, which matters given how many candidates a node can have before
 * {@code branchingLimit} truncates them at Amazons' branching factor.
 */
final class HistoryTable {

    private final Map<Move, Integer> scores = new HashMap<>();

    void recordCutoff(Move move, int depth) {
        scores.merge(move, depth * depth, Integer::sum);
    }

    /** Sorts {@code moves} in place, highest-scoring (most often a cutoff) first. */
    void orderByHistory(List<Move> moves) {
        moves.sort((a, b) -> Integer.compare(scores.getOrDefault(b, 0), scores.getOrDefault(a, 0)));
    }
}
