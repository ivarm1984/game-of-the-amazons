package io.github.ivarm1984.bot.impl.search;

import io.github.ivarm1984.bot.Deadline;
import io.github.ivarm1984.engine.Board;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.MoveGenerator;
import io.github.ivarm1984.engine.PieceColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Alpha-beta negamax search like {@link NegamaxSearch}, but backed by a {@link TranspositionTable}
 * keyed on {@link Zobrist} hashes and a {@link HistoryTable}. Three effects compound to let it
 * search meaningfully deeper, or at least meaningfully better-ordered, than the plain version in
 * the same time budget:
 *
 * <ul>
 *   <li>A position reached by a different move order - a real transposition - reuses its
 *   already-computed score instead of being re-walked.</li>
 *   <li>The table's remembered best move at a node, whether from an earlier shallower
 *   iterative-deepening pass or a sibling branch that transposed into the same position, is tried
 *   first.</li>
 *   <li>Every other candidate at a non-root node is ordered by the history heuristic instead of
 *   {@link NegamaxSearch}'s raw move-generation order. Ordering them by the static evaluator
 *   instead, the way the root already does, was tried and measured slower overall: it means an
 *   extra evaluator call per candidate at every node in the tree, and Amazons' branching factor
 *   makes that cost outweigh the better ordering it buys - measured on the initial position, it
 *   dropped the reachable depth from 2 to 1 within the same 3-second budget. The history heuristic
 *   gets a real ordering signal from moves that already caused cutoffs elsewhere in the tree for
 *   free, with no extra evaluator calls.</li>
 * </ul>
 *
 * <p>Both tables are built fresh per {@link #findBestMove} call and discarded after - per
 * {@link io.github.ivarm1984.bot.Bot}'s statelessness contract, a bot may never keep search state
 * in an instance field - but they still pay off across the iterative-deepening passes and
 * transpositions within that single move decision.
 */
public final class TranspositionSearch {

    private static final int WIN_SCORE = 1_000_000;

    private static final class TimeUp extends RuntimeException {
        TimeUp() {
            super(null, null, false, false);
        }
    }

    private static final TimeUp TIME_UP = new TimeUp();

    private TranspositionSearch() {
    }

    public static Move findBestMove(Board rootBoard, PieceColor me, List<Move> rootMoves,
                                     NegamaxSearch.Evaluator evaluator, Deadline deadline, int maxDepth,
                                     int branchingLimit) {
        if (rootMoves.size() == 1) {
            return rootMoves.get(0);
        }
        TranspositionTable table = new TranspositionTable();
        HistoryTable history = new HistoryTable();
        Move best = rootMoves.get(0);
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (deadline.hasExpired()) {
                break;
            }
            try {
                best = searchRoot(rootBoard, me, rootMoves, evaluator, deadline, depth, branchingLimit, table, history);
            } catch (TimeUp timeUp) {
                break;
            }
        }
        return best;
    }

    private static Move searchRoot(Board board, PieceColor me, List<Move> moves, NegamaxSearch.Evaluator evaluator,
                                    Deadline deadline, int depth, int branchingLimit, TranspositionTable table,
                                    HistoryTable history) {
        long hash = Zobrist.hash(board, me);
        List<Move> ordered = NegamaxSearch.orderByStaticEval(board, me, moves, evaluator);
        putHashMoveFirst(ordered, table.get(hash));

        Move best = ordered.get(0);
        int bestScore = Integer.MIN_VALUE;
        int alpha = -WIN_SCORE - 1;
        int beta = WIN_SCORE + 1;
        for (Move move : ordered) {
            Board next = board.applyMove(move, me);
            int score = -negamax(next, me.opposite(), evaluator, deadline, depth - 1, -beta, -alpha, branchingLimit, table, history);
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
            alpha = Math.max(alpha, score);
        }
        table.put(hash, new TranspositionTable.Entry(depth, bestScore, TranspositionTable.Bound.EXACT, best));
        return best;
    }

    private static int negamax(Board board, PieceColor sideToMove, NegamaxSearch.Evaluator evaluator, Deadline deadline,
                                int depth, int alpha, int beta, int branchingLimit, TranspositionTable table,
                                HistoryTable history) {
        if (deadline.hasExpired()) {
            throw TIME_UP;
        }
        int alphaOrig = alpha;
        long hash = Zobrist.hash(board, sideToMove);
        TranspositionTable.Entry entry = table.get(hash);
        if (entry != null && entry.depth() >= depth) {
            switch (entry.bound()) {
                case EXACT -> {
                    return entry.score();
                }
                case LOWER -> alpha = Math.max(alpha, entry.score());
                case UPPER -> beta = Math.min(beta, entry.score());
            }
            if (alpha >= beta) {
                return entry.score();
            }
        }

        List<Move> moves = MoveGenerator.generateLegalMoves(board, sideToMove);
        if (moves.isEmpty()) {
            // sideToMove cannot move: an immediate, maximally bad outcome for them - true no
            // matter how much deeper a future request searches, so it's stored as permanently deep.
            table.put(hash, new TranspositionTable.Entry(Integer.MAX_VALUE, -WIN_SCORE, TranspositionTable.Bound.EXACT, null));
            return -WIN_SCORE;
        }
        if (depth == 0) {
            return evaluator.evaluate(board, sideToMove);
        }

        List<Move> candidates = new ArrayList<>(moves);
        history.orderByHistory(candidates);
        putHashMoveFirst(candidates, entry);
        if (candidates.size() > branchingLimit) {
            candidates = candidates.subList(0, branchingLimit);
        }

        int best = -WIN_SCORE - 1;
        Move bestMove = candidates.get(0);
        for (Move move : candidates) {
            Board next = board.applyMove(move, sideToMove);
            int score = -negamax(next, sideToMove.opposite(), evaluator, deadline, depth - 1, -beta, -alpha, branchingLimit, table, history);
            if (score > best) {
                best = score;
                bestMove = move;
            }
            if (best > alpha) {
                alpha = best;
            }
            if (alpha >= beta) {
                history.recordCutoff(move, depth);
                break;
            }
        }

        TranspositionTable.Bound bound = best <= alphaOrig ? TranspositionTable.Bound.UPPER
                : best >= beta ? TranspositionTable.Bound.LOWER
                : TranspositionTable.Bound.EXACT;
        table.put(hash, new TranspositionTable.Entry(depth, best, bound, bestMove));
        return best;
    }

    /**
     * Moves the table's recorded best move for this exact position (the "hash move") to the front
     * of the candidate list, in place - it's the single most likely move to trigger an early beta
     * cutoff, ahead of even a static-eval ordering. A no-op if there's no entry yet, or its move
     * isn't in this list (only possible on a hash collision, since the same hash always implies the
     * same legal moves).
     */
    private static void putHashMoveFirst(List<Move> moves, TranspositionTable.Entry entry) {
        Move hashMove = entry != null ? entry.bestMove() : null;
        if (hashMove == null) {
            return;
        }
        int index = moves.indexOf(hashMove);
        if (index > 0) {
            moves.remove(index);
            moves.add(0, hashMove);
        }
    }
}
