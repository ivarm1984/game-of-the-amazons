package io.github.ivarm1984.bot.impl.search;

import io.github.ivarm1984.bot.Deadline;
import io.github.ivarm1984.engine.Board;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.MoveGenerator;
import io.github.ivarm1984.engine.PieceColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable alpha-beta negamax search shared by every search-based bot. Bots
 * differ only in the {@link Evaluator} they plug in and the depth/branching
 * limits they configure - this class owns the tree walk, iterative deepening
 * and deadline handling.
 *
 * <p>Amazons' branching factor is enormous (well over a thousand moves in the
 * opening), so a full-width search even two plies deep can be too slow. To
 * stay safe under the per-move deadline: the root always considers every
 * legal move (matching the one-ply heuristic bots' cost), but any node found
 * deeper in the tree has its move list truncated to {@code branchingLimit}.
 * Iterative deepening then does the rest - each depth is only kept once it
 * fully completes, so a search that runs out of time simply falls back to
 * the previous (shallower) depth's answer instead of forfeiting.
 */
public final class NegamaxSearch {

    private static final int WIN_SCORE = 1_000_000;

    private static final class TimeUp extends RuntimeException {
        TimeUp() {
            super(null, null, false, false);
        }
    }

    private static final TimeUp TIME_UP = new TimeUp();

    private NegamaxSearch() {
    }

    @FunctionalInterface
    public interface Evaluator {
        int evaluate(Board board, PieceColor sideToMove);
    }

    public static Move findBestMove(Board rootBoard, PieceColor me, List<Move> rootMoves,
                                     Evaluator evaluator, Deadline deadline, int maxDepth, int branchingLimit) {
        if (rootMoves.size() == 1) {
            return rootMoves.get(0);
        }
        Move best = rootMoves.get(0);
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (deadline.hasExpired()) {
                break;
            }
            try {
                best = searchRoot(rootBoard, me, rootMoves, evaluator, deadline, depth, branchingLimit);
            } catch (TimeUp timeUp) {
                break;
            }
        }
        return best;
    }

    private static Move searchRoot(Board board, PieceColor me, List<Move> moves, Evaluator evaluator,
                                    Deadline deadline, int depth, int branchingLimit) {
        List<Move> ordered = orderByStaticEval(board, me, moves, evaluator);
        Move best = ordered.get(0);
        int bestScore = Integer.MIN_VALUE;
        int alpha = -WIN_SCORE - 1;
        int beta = WIN_SCORE + 1;
        for (Move move : ordered) {
            Board next = board.applyMove(move, me);
            int score = -negamax(next, me.opposite(), evaluator, deadline, depth - 1, -beta, -alpha, branchingLimit);
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
            alpha = Math.max(alpha, score);
        }
        return best;
    }

    private static int negamax(Board board, PieceColor sideToMove, Evaluator evaluator, Deadline deadline,
                                int depth, int alpha, int beta, int branchingLimit) {
        if (deadline.hasExpired()) {
            throw TIME_UP;
        }
        List<Move> moves = MoveGenerator.generateLegalMoves(board, sideToMove);
        if (moves.isEmpty()) {
            // sideToMove cannot move: an immediate, maximally bad outcome for them.
            return -WIN_SCORE;
        }
        if (depth == 0) {
            return evaluator.evaluate(board, sideToMove);
        }
        List<Move> candidates = moves;
        if (candidates.size() > branchingLimit) {
            candidates = candidates.subList(0, branchingLimit);
        }
        int best = -WIN_SCORE - 1;
        for (Move move : candidates) {
            Board next = board.applyMove(move, sideToMove);
            int score = -negamax(next, sideToMove.opposite(), evaluator, deadline, depth - 1, -beta, -alpha, branchingLimit);
            if (score > best) {
                best = score;
            }
            if (best > alpha) {
                alpha = best;
            }
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    /**
     * Sorts a candidate move list by the static evaluation of the position it
     * leads to, best first. Each move is evaluated exactly once (cached
     * alongside it) rather than inside the sort comparator - re-evaluating on
     * every comparison would turn an O(n) sweep into O(n log n) evaluator
     * calls, which is far too slow against Amazons' huge branching factor.
     *
     * <p>Package-private rather than private: {@link TranspositionSearch} reuses it for its own
     * root move ordering rather than duplicating this logic.
     */
    static List<Move> orderByStaticEval(Board board, PieceColor mover, List<Move> moves, Evaluator evaluator) {
        List<ScoredMove> scored = new ArrayList<>(moves.size());
        for (Move move : moves) {
            int score = evaluator.evaluate(board.applyMove(move, mover), mover);
            scored.add(new ScoredMove(move, score));
        }
        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        List<Move> ordered = new ArrayList<>(scored.size());
        for (ScoredMove sm : scored) {
            ordered.add(sm.move);
        }
        return ordered;
    }

    private record ScoredMove(Move move, int score) {
    }
}
