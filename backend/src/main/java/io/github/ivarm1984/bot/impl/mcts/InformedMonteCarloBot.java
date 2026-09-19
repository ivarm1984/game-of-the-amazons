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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Monte Carlo Tree Search with the two refinements the Amazons literature credits for letting
 * MCTS overtake alpha-beta in this game (R. Lorentz, "Amazons Discover Monte-Carlo", 2008; J.
 * Kloetzer, J. Uiterwijk &amp; H.J. van den Herik, "Monte-Carlo Tree Search in Amazons"):
 *
 * <ul>
 *   <li><b>Informed (semi-random) playouts</b>: {@link #simulate} picks each rollout ply mostly
 *   greedily from a small random sample of legal moves, scored by a cheap local-mobility
 *   heuristic, instead of uniformly at random like {@link MonteCarloBot}. Purely random Amazons
 *   games are close to noise - decided more by who stumbles into self-blocking first than by real
 *   territorial skill - so nudging rollouts towards sane moves makes the win-rate they produce a
 *   far more useful training signal for the tree.</li>
 *   <li><b>Progressive widening</b>: Amazons' branching factor is enormous - well over a thousand
 *   moves in the opening - too wide to give every child even one visit in any reasonable budget.
 *   Each {@link Node} only unlocks new children as its own visit count grows, trying its
 *   heuristically-best untried move first, so search effort concentrates on a narrowing, promising
 *   subset instead of spreading thin across thousands of untested siblings.</li>
 * </ul>
 */
@BotMetadata(id = "mcts-informed", displayName = "Informed Monte Carlo Bot", difficulty = 10,
        description = "Monte Carlo Tree Search with heuristically-biased playouts and progressive "
                + "widening - the two refinements documented in the Amazons literature as the "
                + "difference between plain MCTS and search strong enough to rival alpha-beta.")
@Component
public class InformedMonteCarloBot implements Bot {

    private static final double EXPLORATION = Math.sqrt(2);
    private static final double WIDENING_FACTOR = 4.0;
    private static final int ROLLOUT_SAMPLE_SIZE = 8;
    private static final double ROLLOUT_RANDOM_EPSILON = 0.2;
    private static final int MAX_ROLLOUT_PLIES = 200;
    private static final int ROLLOUT_DEADLINE_CHECK_INTERVAL = 20;

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
        Node root = new Node(null, input.state().board(), me, rootMoves, null);

        while (!deadline.hasExpired()) {
            Node node = root;
            while (!node.priorSortedMoves.isEmpty()) {
                int allowed = Math.min(allowedChildren(node.visits), node.priorSortedMoves.size());
                if (node.expandedMoves < allowed) {
                    node = node.expandNext();
                    break;
                }
                node = node.selectChild();
            }
            boolean iWon = simulate(node.board, node.sideToMove, me, random, deadline);
            backpropagate(node, me, iWon);
        }

        Node best = root.mostVisitedChild();
        return best != null ? best.moveFromParent : rootMoves.get(0);
    }

    private static int allowedChildren(int visits) {
        return (int) Math.ceil(WIDENING_FACTOR * Math.sqrt(visits + 1));
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

    private boolean simulate(Board board, PieceColor sideToMove, PieceColor me,
                              ThreadLocalRandom random, Deadline deadline) {
        Board current = board;
        PieceColor turn = sideToMove;
        for (int ply = 0; ply < MAX_ROLLOUT_PLIES; ply++) {
            if (ply % ROLLOUT_DEADLINE_CHECK_INTERVAL == 0 && deadline.hasExpired()) {
                return BoardEvaluator.mobilityDiff(current, me) >= 0;
            }
            List<Move> moves = MoveGenerator.generateLegalMoves(current, turn);
            if (moves.isEmpty()) {
                return turn != me;
            }
            Move move = pickRolloutMove(current, moves, turn, random);
            current = current.applyMove(move, turn);
            turn = turn.opposite();
        }
        return BoardEvaluator.mobilityDiff(current, me) >= 0;
    }

    /** Best-of-random-sample: mostly greedy on {@link #moveHeuristicScore}, with an epsilon chance of pure random. */
    private static Move pickRolloutMove(Board board, List<Move> moves, PieceColor mover, ThreadLocalRandom random) {
        if (random.nextDouble() < ROLLOUT_RANDOM_EPSILON) {
            return moves.get(random.nextInt(moves.size()));
        }
        int sampleSize = Math.min(ROLLOUT_SAMPLE_SIZE, moves.size());
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < sampleSize; i++) {
            Move candidate = moves.get(random.nextInt(moves.size()));
            int score = moveHeuristicScore(board, candidate, mover);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Cheap local-mobility proxy for a move's quality: how much open space the moving queen keeps
     * around its new square, minus how much open space the arrow leaves the opponent's queens.
     * Deliberately skips full legal-move generation (unlike {@link BoardEvaluator}) - this runs many
     * times per rollout ply and once per untried move at every expanded node, so it stays to a
     * handful of bounded ray-casts instead.
     */
    private static int moveHeuristicScore(Board board, Move move, PieceColor mover) {
        Board after = board.applyMove(move, mover);
        int myOpenLines = openLineCount(after, move.amazonTo());
        int opponentOpenLines = 0;
        for (Position queen : after.queensOf(mover.opposite())) {
            opponentOpenLines += openLineCount(after, queen);
        }
        return myOpenLines - opponentOpenLines;
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
        final List<Move> priorSortedMoves;
        final List<Node> children = new ArrayList<>();
        int expandedMoves;
        int visits;
        int wins;

        Node(Node parent, Board board, PieceColor sideToMove, List<Move> legalMoves, Move moveFromParent) {
            this.parent = parent;
            this.board = board;
            this.sideToMove = sideToMove;
            this.moveFromParent = moveFromParent;
            this.priorSortedMoves = sortedByHeuristic(board, legalMoves, sideToMove);
        }

        private static List<Move> sortedByHeuristic(Board board, List<Move> moves, PieceColor mover) {
            List<Move> sorted = new ArrayList<>(moves);
            sorted.sort((a, b) -> moveHeuristicScore(board, b, mover) - moveHeuristicScore(board, a, mover));
            return sorted;
        }

        Node expandNext() {
            Move move = priorSortedMoves.get(expandedMoves++);
            Board childBoard = board.applyMove(move, sideToMove);
            PieceColor childSide = sideToMove.opposite();
            List<Move> childMoves = MoveGenerator.generateLegalMoves(childBoard, childSide);
            Node child = new Node(this, childBoard, childSide, childMoves, move);
            children.add(child);
            return child;
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

        Node mostVisitedChild() {
            Node best = null;
            int bestVisits = -1;
            for (Node child : children) {
                if (child.visits > bestVisits) {
                    bestVisits = child.visits;
                    best = child;
                }
            }
            return best;
        }
    }
}
