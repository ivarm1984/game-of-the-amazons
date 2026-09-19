package io.github.ivarm1984.bot.impl.search;

import io.github.ivarm1984.engine.Move;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-search cache keyed by {@link Zobrist} hash. Each entry remembers, for the position it was
 * computed from: how many plies deep that computation searched, the resulting score, whether that
 * score is exact or only a bound (from an alpha-beta cutoff), and the best move found - which
 * doubles as a move-ordering hint the next time this position is visited, whether that's a real
 * transposition or the next iterative-deepening pass revisiting the same root.
 *
 * <p>One instance is built fresh inside a single {@link TranspositionSearch#findBestMove} call and
 * discarded afterwards - a {@link io.github.ivarm1984.bot.Bot} may never keep search state in an
 * instance field - but it still pays off within that one call, both across iterative-deepening
 * depths and across transpositions within a single depth.
 */
final class TranspositionTable {

    enum Bound { EXACT, LOWER, UPPER }

    record Entry(int depth, int score, Bound bound, Move bestMove) {
    }

    private final Map<Long, Entry> entries = new HashMap<>();

    Entry get(long hash) {
        return entries.get(hash);
    }

    /**
     * Depth-preferred replacement: a shallower result never evicts a deeper one already recorded
     * for the same position, since the deeper result is strictly more trustworthy.
     */
    void put(long hash, Entry candidate) {
        Entry existing = entries.get(hash);
        if (existing == null || candidate.depth() >= existing.depth()) {
            entries.put(hash, candidate);
        }
    }
}
