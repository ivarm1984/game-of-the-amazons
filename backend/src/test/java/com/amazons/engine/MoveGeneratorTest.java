package com.amazons.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveGeneratorTest {

    @Test
    void queenInMiddleOfEmptyBoardReachesThirtyFiveSquares() {
        Board board = Board.empty().withPiece(new Position(5, 5), Board.WHITE);
        List<Move> moves = MoveGenerator.generateLegalMoves(board, PieceColor.WHITE);
        Set<Position> destinations = moves.stream().map(Move::amazonTo).collect(Collectors.toSet());
        assertEquals(35, destinations.size());
    }

    @Test
    void queenInCornerOfEmptyBoardReachesTwentySevenSquares() {
        Board board = Board.empty().withPiece(new Position(0, 0), Board.WHITE);
        List<Move> moves = MoveGenerator.generateLegalMoves(board, PieceColor.WHITE);
        Set<Position> destinations = moves.stream().map(Move::amazonTo).collect(Collectors.toSet());
        assertEquals(27, destinations.size());
    }

    @Test
    void queenBoxedInOnAllEightSidesHasNoLegalMoves() {
        Position center = new Position(5, 5);
        Board board = Board.empty().withPiece(center, Board.WHITE);
        int[][] deltas = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
        for (int[] d : deltas) {
            board = board.withPiece(new Position(center.row() + d[0], center.col() + d[1]), Board.ARROW);
        }
        List<Move> moves = MoveGenerator.generateLegalMoves(board, PieceColor.WHITE);
        assertTrue(moves.isEmpty());
    }

    @Test
    void arrowShotMayPassBackThroughTheQueensVacatedOrigin() {
        // A lone queen with only one open direction: it must step (5,5)->(5,6),
        // and from there should legally be able to shoot the arrow straight back
        // onto the square it just vacated, (5,5).
        Board board = Board.empty().withPiece(new Position(5, 5), Board.WHITE);
        List<Move> moves = MoveGenerator.generateLegalMoves(board, PieceColor.WHITE);
        Move shootBackToOrigin = new Move(new Position(5, 5), new Position(5, 6), new Position(5, 5));
        assertTrue(moves.contains(shootBackToOrigin),
                "expected the arrow to be able to burn the square the amazon just left");
    }

    @Test
    void moveCannotJumpOverAnotherPiece() {
        Board board = Board.empty()
                .withPiece(new Position(5, 5), Board.WHITE)
                .withPiece(new Position(5, 7), Board.BLACK);
        List<Move> moves = MoveGenerator.generateLegalMoves(board, PieceColor.WHITE);
        boolean anyMoveLandsPastBlocker = moves.stream()
                .anyMatch(m -> m.amazonTo().row() == 5 && m.amazonTo().col() >= 7);
        assertTrue(!anyMoveLandsPastBlocker);
        boolean canReachRightUpToBlocker = moves.stream()
                .anyMatch(m -> m.amazonTo().equals(new Position(5, 6)));
        assertTrue(canReachRightUpToBlocker);
    }
}
