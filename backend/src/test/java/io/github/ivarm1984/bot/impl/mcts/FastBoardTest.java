package io.github.ivarm1984.bot.impl.mcts;

import io.github.ivarm1984.engine.AmazonsGame;
import io.github.ivarm1984.engine.GameState;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.MoveGenerator;
import io.github.ivarm1984.engine.PieceColor;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link FastBoard} is a second, private move generator, which is exactly what the shared engine
 * warns against unless it is held to the engine's own legality. These random games do that: at
 * every position the two generators must agree move for move, and making then unmaking any move
 * must leave the board exactly as it was.
 */
class FastBoardTest {

    @Test
    void agreesWithTheSharedMoveGeneratorThroughRandomGames() {
        Random random = new Random(42);
        int[] buffer = new int[FastBoard.MAX_MOVES];
        for (int game = 0; game < 40; game++) {
            GameState state = AmazonsGame.initial();
            while (true) {
                List<Move> legal = MoveGenerator.generateLegalMoves(state.board(), state.sideToMove());
                FastBoard fast = FastBoard.of(state.board(), state.sideToMove());
                int count = fast.generateMoves(buffer);

                Set<Move> generated = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    generated.add(FastBoard.toMove(buffer[i]));
                }
                assertEquals(count, generated.size(), "duplicate moves generated");
                assertEquals(new HashSet<>(legal), generated);
                assertEquals(!legal.isEmpty(), fast.hasMove());
                if (legal.isEmpty()) {
                    break;
                }

                byte[] cells = fast.cells.clone();
                int[][] queens = {fast.queens[0].clone(), fast.queens[1].clone()};
                for (int i = 0; i < count; i++) {
                    fast.make(buffer[i]);
                    fast.unmake(buffer[i]);
                }
                assertArrayEquals(cells, fast.cells);
                assertArrayEquals(queens[0], fast.queens[0]);
                assertArrayEquals(queens[1], fast.queens[1]);

                Move played = legal.get(random.nextInt(legal.size()));
                fast.make(FastBoard.fromMove(played));
                state = AmazonsGame.apply(state, played);
                FastBoard expected = FastBoard.of(state.board(), state.sideToMove());
                assertArrayEquals(expected.cells, fast.cells);
                assertEquals(expected.sideToMove, fast.sideToMove);
            }
        }
    }

    @Test
    void sideIndexMatchesTheCellEncoding() {
        assertEquals(FastBoard.WHITE - 1, FastBoard.side(PieceColor.WHITE));
        assertEquals(FastBoard.BLACK - 1, FastBoard.side(PieceColor.BLACK));
    }
}
