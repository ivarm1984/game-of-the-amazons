package io.github.ivarm1984.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardInitialPositionTest {

    @Test
    void whiteQueensStartAtExpectedSquares() {
        Board board = Board.initial();
        Set<Position> white = toSet(board.queensOf(PieceColor.WHITE));
        assertEquals(Set.of(
                new Position(3, 0), new Position(0, 3),
                new Position(0, 6), new Position(3, 9)
        ), white);
    }

    @Test
    void blackQueensStartAtExpectedSquares() {
        Board board = Board.initial();
        Set<Position> black = toSet(board.queensOf(PieceColor.BLACK));
        assertEquals(Set.of(
                new Position(6, 0), new Position(9, 3),
                new Position(9, 6), new Position(6, 9)
        ), black);
    }

    @Test
    void startingPositionIsRotationallySymmetric() {
        Board board = Board.initial();
        List<Position> white = board.queensOf(PieceColor.WHITE);
        Set<Position> rotatedWhite = white.stream()
                .map(p -> new Position(9 - p.row(), 9 - p.col()))
                .collect(Collectors.toSet());
        assertEquals(toSet(board.queensOf(PieceColor.BLACK)), rotatedWhite,
                "Black's squares should be the 180-degree rotation of White's squares");
    }

    @Test
    void boardHasExactlyEightQueensAndNoArrowsAtStart() {
        Board board = Board.initial();
        int occupied = 0;
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                byte cell = board.at(r, c);
                assertTrue(cell != Board.ARROW, "no arrows should exist at the start");
                if (cell != Board.EMPTY) occupied++;
            }
        }
        assertEquals(8, occupied);
    }

    private static Set<Position> toSet(List<Position> positions) {
        return Set.copyOf(positions);
    }
}
