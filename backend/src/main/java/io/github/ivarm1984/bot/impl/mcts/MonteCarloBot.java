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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Monte Carlo Tree Search: builds a search tree via UCB1 selection, random
 * rollouts, and backpropagation, one simulation at a time until the deadline
 * runs out. Unlike the minimax bots it needs no hand-designed evaluation
 * function for the tree itself - only random playouts to see who tends to
 * win - which makes it a genuinely different way of picking a strong move.
 */
@BotMetadata(id = "mcts", displayName = "Monte Carlo Bot", difficulty = 9,
        description = "Monte Carlo Tree Search: UCB1-guided selection over random playouts, budgeted "
                + "by the per-move deadline rather than a fixed search depth.")
@Component
public class MonteCarloBot implements Bot {

    private static final double EXPLORATION = Math.sqrt(2);
    private static final int MAX_ROLLOUT_PLIES = 200;
    private static final int ROLLOUT_DEADLINE_CHECK_INTERVAL = 20;

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
            while (node.untriedMoves.isEmpty() && !node.children.isEmpty()) {
                node = node.selectChild();
            }
            if (!node.untriedMoves.isEmpty()) {
                node = node.expand(random);
            }
            boolean iWon = simulate(node.board, node.sideToMove, me, random, deadline);
            backpropagate(node, me, iWon);
        }

        Node best = root.mostVisitedChild();
        return best != null ? best.moveFromParent : rootMoves.get(0);
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
            Move move = moves.get(random.nextInt(moves.size()));
            current = current.applyMove(move, turn);
            turn = turn.opposite();
        }
        return BoardEvaluator.mobilityDiff(current, me) >= 0;
    }

    private static final class Node {
        final Node parent;
        final Board board;
        final PieceColor sideToMove;
        final Move moveFromParent;
        final List<Move> untriedMoves;
        final List<Node> children = new ArrayList<>();
        int visits;
        int wins;

        Node(Node parent, Board board, PieceColor sideToMove, List<Move> legalMoves, Move moveFromParent) {
            this.parent = parent;
            this.board = board;
            this.sideToMove = sideToMove;
            this.untriedMoves = new ArrayList<>(legalMoves);
            this.moveFromParent = moveFromParent;
        }

        Node expand(ThreadLocalRandom random) {
            int i = random.nextInt(untriedMoves.size());
            Move move = untriedMoves.remove(i);
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
