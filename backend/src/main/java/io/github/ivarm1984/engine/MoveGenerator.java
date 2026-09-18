package io.github.ivarm1984.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * The single source of truth for Amazons move legality. Every bot receives its
 * legal moves pre-generated here rather than re-deriving them, so all bots see
 * identical, correct legality with no risk of a bespoke generator being subtly
 * more or less permissive.
 */
public final class MoveGenerator {

    private MoveGenerator() {
    }

    private static final int[][] DIRECTIONS = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1}, {0, 1},
            {1, -1}, {1, 0}, {1, 1}
    };

    public static List<Move> generateLegalMoves(Board board, PieceColor color) {
        List<Move> moves = new ArrayList<>();
        for (Position queen : board.queensOf(color)) {
            for (Position dest : reachableSquares(board, queen)) {
                // Vacate the origin and occupy the destination before ray-casting for
                // arrow targets, so a shot may legally pass back through the square
                // the amazon just left.
                Board afterStep = board.vacateAndOccupy(queen, dest, color);
                for (Position arrow : reachableSquares(afterStep, dest)) {
                    moves.add(new Move(queen, dest, arrow));
                }
            }
        }
        return moves;
    }

    private static List<Position> reachableSquares(Board board, Position from) {
        List<Position> squares = new ArrayList<>();
        for (int[] d : DIRECTIONS) {
            int r = from.row() + d[0];
            int c = from.col() + d[1];
            while (Position.isValid(r, c) && board.at(r, c) == Board.EMPTY) {
                squares.add(new Position(r, c));
                r += d[0];
                c += d[1];
            }
        }
        return squares;
    }
}
