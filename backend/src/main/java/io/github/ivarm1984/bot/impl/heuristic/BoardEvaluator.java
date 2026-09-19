package io.github.ivarm1984.bot.impl.heuristic;

import io.github.ivarm1984.engine.Board;
import io.github.ivarm1984.engine.MoveGenerator;
import io.github.ivarm1984.engine.PieceColor;
import io.github.ivarm1984.engine.Position;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Static position-evaluation building blocks shared by the heuristic and
 * search bots. Every method returns a score from {@code sideToMove}'s point
 * of view: positive favors {@code sideToMove}, negative favors the opponent.
 */
public final class BoardEvaluator {

    private static final int[][] DIRECTIONS = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1}, {0, 1},
            {1, -1}, {1, 0}, {1, 1}
    };
    private static final int UNREACHABLE = Integer.MAX_VALUE;

    private BoardEvaluator() {
    }

    /** Difference in immediately available legal moves. */
    public static int mobilityDiff(Board board, PieceColor sideToMove) {
        PieceColor opponent = sideToMove.opposite();
        int mine = MoveGenerator.generateLegalMoves(board, sideToMove).size();
        int theirs = MoveGenerator.generateLegalMoves(board, opponent).size();
        return mine - theirs;
    }

    /**
     * "Territory" evaluation: for every empty square, count it as mine if my
     * queens can reach it in fewer queen-moves than the opponent's, and
     * vice versa. Classic Amazons evaluation - rewards carving out space
     * your queens control before the opponent can contest it.
     */
    public static int queenTerritoryDiff(Board board, PieceColor sideToMove) {
        int[] mine = queenMoveDistances(board, sideToMove);
        int[] theirs = queenMoveDistances(board, sideToMove.opposite());
        return territoryDiff(mine, theirs);
    }

    /**
     * Same idea as {@link #queenTerritoryDiff} but distances are counted in
     * single king-steps rather than full queen moves - a slower-growing,
     * more local notion of "close" space that rewards actual proximity.
     */
    public static int kingTerritoryDiff(Board board, PieceColor sideToMove) {
        int[] mine = kingMoveDistances(board, sideToMove);
        int[] theirs = kingMoveDistances(board, sideToMove.opposite());
        return territoryDiff(mine, theirs);
    }

    /** Weighted blend of mobility and the two territory measures. */
    public static int combined(Board board, PieceColor sideToMove,
                                double mobilityWeight, double queenTerritoryWeight, double kingTerritoryWeight) {
        double score = mobilityWeight * mobilityDiff(board, sideToMove)
                + queenTerritoryWeight * queenTerritoryDiff(board, sideToMove)
                + kingTerritoryWeight * kingTerritoryDiff(board, sideToMove);
        return (int) Math.round(score);
    }

    /**
     * {@link #combined} with weights that shift as the game progresses instead of staying fixed:
     * mobility matters more on an open board, queen territory more once queens are boxed into their
     * own regions as arrows accumulate. Amazons' classic game-phase-aware evaluation, shared by every
     * search bot that wants it rather than a flat weighting.
     */
    public static int phaseAwareCombined(Board board, PieceColor sideToMove) {
        double phase = gamePhase(board);
        double mobilityWeight = 1.5 - phase;
        double queenTerritoryWeight = 1.0 + 2.5 * phase;
        return combined(board, sideToMove, mobilityWeight, queenTerritoryWeight, 1.0);
    }

    /** 0.0 at the start of the game, approaching 1.0 as the board fills up with arrows. */
    public static double gamePhase(Board board) {
        int emptyCells = 0;
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (board.at(row, col) == Board.EMPTY) {
                    emptyCells++;
                }
            }
        }
        int emptyCellsAtStart = Board.SIZE * Board.SIZE - 8;
        double filled = 1.0 - ((double) emptyCells / emptyCellsAtStart);
        return Math.max(0.0, Math.min(1.0, filled));
    }

    private static int territoryDiff(int[] mine, int[] theirs) {
        int mineCloser = 0;
        int theirsCloser = 0;
        for (int i = 0; i < mine.length; i++) {
            if (mine[i] < theirs[i]) {
                mineCloser++;
            } else if (theirs[i] < mine[i]) {
                theirsCloser++;
            }
        }
        return mineCloser - theirsCloser;
    }

    /** Multi-source BFS: distance (in queen moves) from the nearest queen of {@code color} to every square. */
    private static int[] queenMoveDistances(Board board, PieceColor color) {
        int size = Board.SIZE;
        int[] dist = new int[size * size];
        Arrays.fill(dist, UNREACHABLE);
        Deque<Integer> queue = new ArrayDeque<>();
        for (Position queen : board.queensOf(color)) {
            int idx = queen.row() * size + queen.col();
            dist[idx] = 0;
            queue.add(idx);
        }
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            int row = cell / size;
            int col = cell % size;
            int nextDist = dist[cell] + 1;
            for (int[] d : DIRECTIONS) {
                int r = row + d[0];
                int c = col + d[1];
                while (Position.isValid(r, c) && board.at(r, c) == Board.EMPTY) {
                    int idx = r * size + c;
                    if (nextDist < dist[idx]) {
                        dist[idx] = nextDist;
                        queue.add(idx);
                    }
                    r += d[0];
                    c += d[1];
                }
            }
        }
        return dist;
    }

    /** Multi-source BFS: distance (in single king-steps) from the nearest queen of {@code color} to every square. */
    private static int[] kingMoveDistances(Board board, PieceColor color) {
        int size = Board.SIZE;
        int[] dist = new int[size * size];
        Arrays.fill(dist, UNREACHABLE);
        Deque<Integer> queue = new ArrayDeque<>();
        for (Position queen : board.queensOf(color)) {
            int idx = queen.row() * size + queen.col();
            dist[idx] = 0;
            queue.add(idx);
        }
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            int row = cell / size;
            int col = cell % size;
            int nextDist = dist[cell] + 1;
            for (int[] d : DIRECTIONS) {
                int r = row + d[0];
                int c = col + d[1];
                if (Position.isValid(r, c) && board.at(r, c) == Board.EMPTY) {
                    int idx = r * size + c;
                    if (nextDist < dist[idx]) {
                        dist[idx] = nextDist;
                        queue.add(idx);
                    }
                }
            }
        }
        return dist;
    }
}
