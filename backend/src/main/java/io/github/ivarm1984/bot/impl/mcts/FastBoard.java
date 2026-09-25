package io.github.ivarm1984.bot.impl.mcts;

import io.github.ivarm1984.engine.Board;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.PieceColor;
import io.github.ivarm1984.engine.Position;

/**
 * Mutable, allocation-free Amazons board for search, kept behind the {@code Bot} boundary as the
 * shared engine's documentation invites. The 10x10 board sits inside a one-cell {@link #BLOCKED}
 * border (12x12), so every ray walk stops on {@code cells[sq] != EMPTY} alone with no bounds checks.
 *
 * <p>A move is packed into one {@code int}: {@code from | to << 8 | arrow << 16}, each a padded
 * square index. Moves are applied and taken back in place ({@link #make} / {@link #unmake}) rather
 * than copying the board, so a search walks one board up and down its tree.
 */
final class FastBoard {

    static final int WIDTH = 12;
    static final int CELLS = WIDTH * WIDTH;

    static final byte EMPTY = 0;
    static final byte WHITE = 1;
    static final byte BLACK = 2;
    static final byte BLOCKED = 3;

    /** The eight queen directions as padded-index offsets. */
    static final int[] DIRECTIONS = {-WIDTH - 1, -WIDTH, -WIDTH + 1, -1, 1, WIDTH - 1, WIDTH, WIDTH + 1};

    /** Upper bound on legal moves in any position; generously above the opening's 2176. */
    static final int MAX_MOVES = 10000;

    final byte[] cells = new byte[CELLS];
    /** {@code queens[side][i]}: padded square of each side's queens; side 0 is White, 1 is Black. */
    final int[][] queens = new int[2][4];
    int sideToMove;

    static FastBoard of(Board board, PieceColor sideToMove) {
        FastBoard fast = new FastBoard();
        java.util.Arrays.fill(fast.cells, BLOCKED);
        int[] queenCount = new int[2];
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                byte cell = board.at(row, col);
                int sq = square(row, col);
                fast.cells[sq] = cell == Board.ARROW ? BLOCKED : cell;
                if (cell == Board.WHITE || cell == Board.BLACK) {
                    int side = cell - 1;
                    fast.queens[side][queenCount[side]++] = sq;
                }
            }
        }
        fast.sideToMove = side(sideToMove);
        return fast;
    }

    static int side(PieceColor color) {
        return color == PieceColor.WHITE ? 0 : 1;
    }

    static int square(int row, int col) {
        return (row + 1) * WIDTH + col + 1;
    }

    static int from(int move) {
        return move & 0xFF;
    }

    static int to(int move) {
        return (move >>> 8) & 0xFF;
    }

    static int arrow(int move) {
        return (move >>> 16) & 0xFF;
    }

    static int encode(int from, int to, int arrow) {
        return from | to << 8 | arrow << 16;
    }

    static Move toMove(int move) {
        return new Move(position(from(move)), position(to(move)), position(arrow(move)));
    }

    static int fromMove(Move move) {
        return encode(square(move.amazonFrom()), square(move.amazonTo()), square(move.arrowTo()));
    }

    private static int square(Position p) {
        return square(p.row(), p.col());
    }

    private static Position position(int sq) {
        return new Position(sq / WIDTH - 1, sq % WIDTH - 1);
    }

    /** Writes every legal move for the side to move into {@code out}, returning how many. */
    int generateMoves(int[] out) {
        int count = 0;
        int[] mine = queens[sideToMove];
        for (int q = 0; q < 4; q++) {
            int from = mine[q];
            cells[from] = EMPTY; // an arrow may pass back through the square the queen left
            for (int d : DIRECTIONS) {
                for (int to = from + d; cells[to] == EMPTY; to += d) {
                    for (int d2 : DIRECTIONS) {
                        for (int arrow = to + d2; cells[arrow] == EMPTY; arrow += d2) {
                            out[count++] = encode(from, to, arrow);
                        }
                    }
                }
            }
            cells[from] = (byte) (sideToMove + 1);
        }
        return count;
    }

    /** True when the side to move has at least one legal move: some queen has an empty neighbour. */
    boolean hasMove() {
        int[] mine = queens[sideToMove];
        for (int q = 0; q < 4; q++) {
            int sq = mine[q];
            for (int d : DIRECTIONS) {
                if (cells[sq + d] == EMPTY) {
                    return true;
                }
            }
        }
        return false;
    }

    void make(int move) {
        int from = from(move);
        int to = to(move);
        int[] mine = queens[sideToMove];
        for (int q = 0; q < 4; q++) {
            if (mine[q] == from) {
                mine[q] = to;
                break;
            }
        }
        cells[from] = EMPTY;
        cells[to] = (byte) (sideToMove + 1);
        cells[arrow(move)] = BLOCKED;
        sideToMove ^= 1;
    }

    void unmake(int move) {
        sideToMove ^= 1;
        int from = from(move);
        int to = to(move);
        int[] mine = queens[sideToMove];
        for (int q = 0; q < 4; q++) {
            if (mine[q] == to) {
                mine[q] = from;
                break;
            }
        }
        cells[arrow(move)] = EMPTY;
        cells[to] = EMPTY;
        cells[from] = (byte) (sideToMove + 1);
    }
}
