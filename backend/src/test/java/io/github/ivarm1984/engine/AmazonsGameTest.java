package io.github.ivarm1984.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmazonsGameTest {

    @Test
    void initialPositionHasNoWinnerAndHasLegalMoves() {
        GameState state = AmazonsGame.initial();
        assertTrue(AmazonsGame.result(state).isEmpty());
        assertFalse(AmazonsGame.legalMoves(state).isEmpty());
    }

    @Test
    void sideWithNoLegalMovesLoses() {
        Position boxedQueen = new Position(5, 5);
        Board board = Board.empty().withPiece(boxedQueen, Board.WHITE);
        int[][] deltas = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
        for (int[] d : deltas) {
            board = board.withPiece(new Position(boxedQueen.row() + d[0], boxedQueen.col() + d[1]), Board.ARROW);
        }
        GameState state = new GameState(board, PieceColor.WHITE, 12, List.of());

        Optional<GameResult> result = AmazonsGame.result(state);

        assertTrue(result.isPresent());
        assertEquals(GameStatus.BLACK_WINS, result.get().status());
        assertEquals(PieceColor.BLACK, result.get().winner());
        assertEquals(GameResult.NO_LEGAL_MOVES, result.get().reason());
        assertEquals(12, result.get().totalPlies());
    }

    @Test
    void randomlyPlayedGameFromTheStartAlwaysTerminates() {
        Random random = new Random(42);
        GameState state = AmazonsGame.initial();
        int safetyCap = 2000;

        for (int ply = 0; ply < safetyCap; ply++) {
            Optional<GameResult> result = AmazonsGame.result(state);
            if (result.isPresent()) {
                assertTrue(result.get().winner() == PieceColor.WHITE || result.get().winner() == PieceColor.BLACK);
                return;
            }
            List<Move> legalMoves = AmazonsGame.legalMoves(state);
            Move chosen = legalMoves.get(random.nextInt(legalMoves.size()));
            state = AmazonsGame.apply(state, chosen);
        }

        throw new AssertionError("game did not terminate within " + safetyCap + " plies");
    }
}
