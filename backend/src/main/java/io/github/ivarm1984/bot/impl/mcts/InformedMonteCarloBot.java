package io.github.ivarm1984.bot.impl.mcts;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.bot.Deadline;
import io.github.ivarm1984.bot.impl.heuristic.BoardEvaluator;
import io.github.ivarm1984.engine.Board;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.MoveGenerator;
import io.github.ivarm1984.engine.PieceColor;
import io.github.ivarm1984.engine.Position;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Monte Carlo Tree Search specialised for Amazons, with three refinements over the textbook
 * algorithm that {@link MonteCarloBot} implements. All three exist because Amazons punishes
 * textbook MCTS in the same way: the branching factor (over two thousand moves in the opening)
 * is so large that whatever the search spends per simulation, it gets back far too few of them.
 *
 * <ul>
 *   <li><b>Early playout termination.</b> {@link #simulate} plays only {@link #PLAYOUT_PLIES}
 *   plies and then scores the position with a territory evaluation, instead of playing on to a
 *   real win or loss. A full random Amazons playout runs ~75 plies and the result is close to
 *   noise - decided more by who stumbles into self-blocking first than by territorial skill - so
 *   it is both the most expensive part of a simulation and the least informative. Cutting it
 *   short trades a noisy exact outcome for a much cheaper informative estimate. This is the
 *   single largest strength gain in this class; see the note on tuning below.</li>
 *   <li><b>Informed (semi-random) playouts.</b> The plies that do get played are picked mostly
 *   greedily from a small random sample of legal moves, scored by a cheap local-mobility
 *   heuristic, rather than uniformly at random.</li>
 *   <li><b>Progressive widening.</b> Amazons is too wide to give every child even one visit in
 *   any reasonable budget. Each {@link Node} unlocks new children only as its own visit count
 *   grows, trying its heuristically-best untried move first, so effort concentrates on a
 *   narrowing promising subset instead of spreading thin across thousands of untested
 *   siblings.</li>
 * </ul>
 *
 * <p>Every one of these only pays off if the tree still gets a competitive number of simulations
 * out of the same deadline, so the cost of each simulation is the binding constraint rather than
 * an implementation detail. Two consequences worth stating, because both were measured rather
 * than assumed, and getting either wrong makes this bot lose to plain {@link MonteCarloBot}:
 *
 * <ul>
 *   <li>Only the root orders <em>all</em> of its moves by the heuristic, and it scores each move
 *   exactly once to do so. Interior nodes never score their full move list; each new child is the
 *   best of a small random sample ({@link #EXPANSION_SAMPLE_SIZE}). Scoring every move at every
 *   node - which a comparator-based sort does O(log n) times over per move - costs more than
 *   every playout in the search put together.</li>
 *   <li>Playout length and evaluation cost dominate everything else. Shortening playouts
 *   monotonically improved playing strength all the way down to {@link #PLAYOUT_PLIES}, and an
 *   evaluation including {@link BoardEvaluator#mobilityDiff} - two full move generations per
 *   simulation - was catastrophically worse than territory alone despite being the better
 *   evaluation per call.</li>
 * </ul>
 */
@BotMetadata(id = "mcts-informed", displayName = "Informed Monte Carlo Bot", difficulty = 10,
        description = "Monte Carlo Tree Search with early playout termination, heuristically-biased "
                + "playouts and progressive widening - the refinements that make MCTS competitive "
                + "on a board as wide as Amazons rather than a slower way to play randomly.")
@Component
public class InformedMonteCarloBot implements Bot {

    private static final double EXPLORATION = Math.sqrt(2);

    /**
     * Children unlocked at a node with {@code v} visits: {@code ceil(FACTOR * sqrt(v + 1))}. Only
     * matters when simulations are scarce - widening faster than they arrive leaves every child on
     * one or two visits of pure noise, degrading the search into a greedy heuristic move-picker.
     */
    private static final double WIDENING_FACTOR = 1.5;

    /** Moves scored when an interior node unlocks one more child. Bounded, unlike the move list. */
    private static final int EXPANSION_SAMPLE_SIZE = 16;

    /**
     * Plies played before {@link #simulate} gives up on reaching a real result and evaluates the
     * position instead. Deliberately tiny: strength rose monotonically as this came down from a
     * full game, and two plies still beat evaluating the node directly with no playout at all.
     */
    private static final int PLAYOUT_PLIES = 2;

    private static final int PLAYOUT_SAMPLE_SIZE = 8;
    private static final double PLAYOUT_RANDOM_EPSILON = 0.2;

    private static final int[][] DIRECTIONS = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1}, {0, 1},
            {1, -1}, {1, 0}, {1, 1}
    };

    @Override
    public Move decideMove(BotInput input) {
        List<Move> rootMoves = input.legalMoves();
        if (rootMoves.size() == 1) {
            return rootMoves.get(0);
        }

        PieceColor me = input.myColor();
        Deadline deadline = input.deadline();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Node root = Node.root(input.state().board(), me, rootMoves);

        while (!deadline.hasExpired()) {
            Node node = root;
            while (node.isNonTerminal()) {
                if (node.expandedMoves < allowedChildren(node.visits, node.untried.length)) {
                    node = node.expandNext(random);
                    break;
                }
                node = node.selectChild();
            }
            boolean iWon = simulate(node.board, node.sideToMove, me, random);
            backpropagate(node, me, iWon);
        }

        Node best = root.bestChild();
        return best != null ? best.moveFromParent : rootMoves.get(0);
    }

    private static int allowedChildren(int visits, int moveCount) {
        return Math.min((int) Math.ceil(WIDENING_FACTOR * Math.sqrt(visits + 1)), moveCount);
    }

    private static void backpropagate(Node node, PieceColor me, boolean iWon) {
        Node current = node;
        while (current != null) {
            current.visits++;
            PieceColor mover = current.sideToMove.opposite();
            if (iWon == (mover == me)) {
                current.wins++;
            }
            current = current.parent;
        }
    }

    /**
     * A short informed playout, terminated early and scored by evaluation. Needs no deadline of
     * its own - unlike a playout to the end of the game, {@link #PLAYOUT_PLIES} plies plus one
     * evaluation is bounded and cheap enough that the caller's deadline check between simulations
     * is a fine enough grain.
     */
    private boolean simulate(Board board, PieceColor sideToMove, PieceColor me, ThreadLocalRandom random) {
        Board current = board;
        PieceColor turn = sideToMove;
        for (int ply = 0; ply < PLAYOUT_PLIES; ply++) {
            List<Move> moves = MoveGenerator.generateLegalMoves(current, turn);
            if (moves.isEmpty()) {
                // An actual result this close to the node beats any estimate of one.
                return turn != me;
            }
            Move move = pickPlayoutMove(current, moves, turn, random);
            current = current.applyMove(move, turn);
            turn = turn.opposite();
        }
        return evaluatePlayout(current, me) >= 0;
    }

    /**
     * Queen-move and king-move territory, equally weighted, with no mobility term. Mobility is the
     * better measure of an Amazons position but costs two full move generations, and paying that
     * once per simulation loses far more in simulation count than it gains in accuracy - measured
     * at roughly a 1-in-8 win rate against the same bot scoring on territory alone.
     */
    private static int evaluatePlayout(Board board, PieceColor me) {
        return BoardEvaluator.combined(board, me, 0.0, 1.0, 1.0);
    }

    /** Best-of-random-sample: mostly greedy on {@link #moveHeuristicScore}, with an epsilon chance of pure random. */
    private static Move pickPlayoutMove(Board board, List<Move> moves, PieceColor mover, ThreadLocalRandom random) {
        if (random.nextDouble() < PLAYOUT_RANDOM_EPSILON) {
            return moves.get(random.nextInt(moves.size()));
        }
        List<Position> myQueens = board.queensOf(mover);
        List<Position> theirQueens = board.queensOf(mover.opposite());
        int sampleSize = Math.min(PLAYOUT_SAMPLE_SIZE, moves.size());
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < sampleSize; i++) {
            Move candidate = moves.get(random.nextInt(moves.size()));
            int score = moveHeuristicScore(board, candidate, mover, myQueens, theirQueens);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Cheap local-mobility proxy for a move's quality: how much open space all of the mover's
     * queens keep once the move is played, minus how much the opponent's queens keep. Both queen
     * sets are passed in as they stood <em>before</em> the move, so callers scanning many candidate
     * moves from one position locate them once rather than once per candidate.
     *
     * <p>Deliberately skips full legal-move generation (unlike {@link BoardEvaluator}) - this runs
     * many times per playout ply and per unlocked child, so it stays to a handful of bounded
     * ray-casts instead.
     */
    private static int moveHeuristicScore(Board board, Move move, PieceColor mover,
                                           List<Position> myQueens, List<Position> theirQueens) {
        Board after = board.applyMove(move, mover);
        int myOpenLines = 0;
        for (Position queen : myQueens) {
            // The queen that moved is counted from its destination; the others have not moved.
            myOpenLines += openLineCount(after, queen.equals(move.amazonFrom()) ? move.amazonTo() : queen);
        }
        int theirOpenLines = 0;
        for (Position queen : theirQueens) {
            theirOpenLines += openLineCount(after, queen);
        }
        return myOpenLines - theirOpenLines;
    }

    /** Number of empty squares reachable from {@code from} in a straight line, summed over all 8 directions. */
    private static int openLineCount(Board board, Position from) {
        int count = 0;
        for (int[] d : DIRECTIONS) {
            int r = from.row() + d[0];
            int c = from.col() + d[1];
            while (Position.isValid(r, c) && board.at(r, c) == Board.EMPTY) {
                count++;
                r += d[0];
                c += d[1];
            }
        }
        return count;
    }

    private static final class Node {
        final Node parent;
        final Board board;
        final PieceColor sideToMove;
        final Move moveFromParent;

        /**
         * Every legal move from this node. {@code untried[0..expandedMoves)} have already become
         * children; the rest are still available. At the root the whole array is ordered best-first
         * up front; elsewhere it stays in generation order and {@link #expandNext} swaps its pick
         * into the next slot, so the good-moves-first property costs a bounded sample rather than
         * a full sort of a list thousands of moves long.
         */
        final Move[] untried;
        final boolean orderedBestFirst;
        final List<Node> children = new ArrayList<>();
        int expandedMoves;
        int visits;
        int wins;

        private Node(Node parent, Board board, PieceColor sideToMove, Move[] untried,
                     boolean orderedBestFirst, Move moveFromParent) {
            this.parent = parent;
            this.board = board;
            this.sideToMove = sideToMove;
            this.untried = untried;
            this.orderedBestFirst = orderedBestFirst;
            this.moveFromParent = moveFromParent;
        }

        static Node root(Board board, PieceColor sideToMove, List<Move> legalMoves) {
            return new Node(null, board, sideToMove, sortedByHeuristic(board, legalMoves, sideToMove), true, null);
        }

        /**
         * Root move ordering: one heuristic evaluation per move, then a sort on the cached scores.
         * Scoring inside the comparator instead would evaluate each move O(log n) times over - on a
         * 2000-move opening position that is ~48,000 evaluations where 2,000 suffice.
         */
        private static Move[] sortedByHeuristic(Board board, List<Move> moves, PieceColor mover) {
            List<Position> myQueens = board.queensOf(mover);
            List<Position> theirQueens = board.queensOf(mover.opposite());
            int count = moves.size();
            Integer[] order = new Integer[count];
            int[] scores = new int[count];
            for (int i = 0; i < count; i++) {
                order[i] = i;
                scores[i] = moveHeuristicScore(board, moves.get(i), mover, myQueens, theirQueens);
            }
            Arrays.sort(order, (a, b) -> Integer.compare(scores[b], scores[a]));
            Move[] sorted = new Move[count];
            for (int i = 0; i < count; i++) {
                sorted[i] = moves.get(order[i]);
            }
            return sorted;
        }

        /** A node with no legal moves is a loss for whoever is to move, and nothing to search. */
        boolean isNonTerminal() {
            return untried.length > 0;
        }

        Node expandNext(ThreadLocalRandom random) {
            if (!orderedBestFirst) {
                promoteBestOfSample(random);
            }
            Move move = untried[expandedMoves++];
            Board childBoard = board.applyMove(move, sideToMove);
            PieceColor childSide = sideToMove.opposite();
            Move[] childMoves = MoveGenerator.generateLegalMoves(childBoard, childSide).toArray(new Move[0]);
            Node child = new Node(this, childBoard, childSide, childMoves, false, move);
            children.add(child);
            return child;
        }

        /** Swaps the best of a small random sample of the remaining moves into the next slot. */
        private void promoteBestOfSample(ThreadLocalRandom random) {
            int remaining = untried.length - expandedMoves;
            int sampleSize = Math.min(EXPANSION_SAMPLE_SIZE, remaining);
            if (sampleSize <= 1) {
                return;
            }
            List<Position> myQueens = board.queensOf(sideToMove);
            List<Position> theirQueens = board.queensOf(sideToMove.opposite());
            int bestIndex = expandedMoves;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < sampleSize; i++) {
                int index = expandedMoves + random.nextInt(remaining);
                int score = moveHeuristicScore(board, untried[index], sideToMove, myQueens, theirQueens);
                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = index;
                }
            }
            Move swap = untried[expandedMoves];
            untried[expandedMoves] = untried[bestIndex];
            untried[bestIndex] = swap;
        }

        Node selectChild() {
            Node best = null;
            double bestUcb = Double.NEGATIVE_INFINITY;
            for (Node child : children) {
                double ucb = child.ucbValue(this.visits);
                if (ucb > bestUcb) {
                    bestUcb = ucb;
                    best = child;
                }
            }
            return best;
        }

        double ucbValue(int parentVisits) {
            if (visits == 0) {
                return Double.POSITIVE_INFINITY;
            }
            double exploitation = (double) wins / visits;
            double exploration = EXPLORATION * Math.sqrt(Math.log(parentVisits) / visits);
            return exploitation + exploration;
        }

        /**
         * Most-visited child, breaking ties on win rate. Ties are the common case early in the
         * game, where the branching factor swamps the simulation budget and the leading children
         * sit on a handful of visits each.
         */
        Node bestChild() {
            Node best = null;
            for (Node child : children) {
                if (best == null
                        || child.visits > best.visits
                        || (child.visits == best.visits
                            && (double) child.wins / child.visits > (double) best.wins / best.visits)) {
                    best = child;
                }
            }
            return best;
        }
    }
}
