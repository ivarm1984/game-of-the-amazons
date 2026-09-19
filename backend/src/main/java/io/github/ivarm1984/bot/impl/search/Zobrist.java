package io.github.ivarm1984.bot.impl.search;

import io.github.ivarm1984.engine.Board;
import io.github.ivarm1984.engine.PieceColor;

import java.util.Random;

/**
 * Zobrist hashing for {@link Board}: a 64-bit fingerprint built by XORing a random key per
 * (cell, piece) pair present on the board, plus a key for the side to move. Two boards with the
 * same pieces on the same squares - however they were reached - hash identically, which is
 * exactly the transposition a {@link TranspositionTable} exploits.
 *
 * <p>Recomputed from scratch per node ({@link Board} doesn't track a hash incrementally through
 * {@code applyMove}) rather than incrementally maintained - a full 100-cell scan is cheap next to
 * the flood-fill evaluator and full move regeneration every search node already pays for.
 */
final class Zobrist {

    private static final long[][] CELL_KEYS = new long[Board.SIZE * Board.SIZE][4];
    private static final long WHITE_TO_MOVE_KEY;

    static {
        Random random = new Random(0xA5A5_1984L);
        for (long[] cellKeys : CELL_KEYS) {
            // Index 0 (Board.EMPTY) is left zero: an empty cell contributes nothing to the hash.
            for (int piece = 1; piece < cellKeys.length; piece++) {
                cellKeys[piece] = random.nextLong();
            }
        }
        WHITE_TO_MOVE_KEY = random.nextLong();
    }

    private Zobrist() {
    }

    static long hash(Board board, PieceColor sideToMove) {
        long hash = sideToMove == PieceColor.WHITE ? WHITE_TO_MOVE_KEY : 0L;
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                byte piece = board.at(row, col);
                if (piece != Board.EMPTY) {
                    hash ^= CELL_KEYS[row * Board.SIZE + col][piece];
                }
            }
        }
        return hash;
    }
}
