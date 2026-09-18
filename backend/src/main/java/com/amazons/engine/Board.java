package com.amazons.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable 10x10 Amazons board. Cheap to copy (100 bytes) so the shared engine
 * stays immutable-by-copy rather than needing make/unmake bookkeeping; bots that
 * need faster internal representations for deep search are free to convert to
 * their own structures behind the Bot interface boundary.
 */
public final class Board {

    public static final int SIZE = Position.BOARD_SIZE;

    public static final byte EMPTY = 0;
    public static final byte WHITE = 1;
    public static final byte BLACK = 2;
    public static final byte ARROW = 3;

    private final byte[] cells;

    private Board(byte[] cells) {
        this.cells = cells;
    }

    public static Board initial() {
        byte[] c = new byte[SIZE * SIZE];
        placeQueen(c, 3, 0, WHITE);
        placeQueen(c, 0, 3, WHITE);
        placeQueen(c, 0, 6, WHITE);
        placeQueen(c, 3, 9, WHITE);
        placeQueen(c, 6, 0, BLACK);
        placeQueen(c, 9, 3, BLACK);
        placeQueen(c, 9, 6, BLACK);
        placeQueen(c, 6, 9, BLACK);
        return new Board(c);
    }

    private static void placeQueen(byte[] c, int row, int col, byte value) {
        c[idx(row, col)] = value;
    }

    private static int idx(int row, int col) {
        return row * SIZE + col;
    }

    public byte at(int row, int col) {
        return cells[idx(row, col)];
    }

    public byte at(Position p) {
        return at(p.row(), p.col());
    }

    public List<Position> queensOf(PieceColor color) {
        byte target = color == PieceColor.WHITE ? WHITE : BLACK;
        List<Position> result = new ArrayList<>(4);
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (cells[idx(r, c)] == target) {
                    result.add(new Position(r, c));
                }
            }
        }
        return result;
    }

    /**
     * Board after a queen has stepped from {@code from} to {@code to}, but before
     * the arrow has been shot. Used by move generation to ray-cast arrow targets
     * from the queen's new square, correctly treating the vacated origin as empty.
     */
    public Board vacateAndOccupy(Position from, Position to, PieceColor color) {
        byte[] c = cells.clone();
        c[idx(from.row(), from.col())] = EMPTY;
        c[idx(to.row(), to.col())] = color == PieceColor.WHITE ? WHITE : BLACK;
        return new Board(c);
    }

    /** Board after a full move (queen relocation + arrow shot) has been applied. */
    public Board applyMove(Move move, PieceColor mover) {
        byte[] c = cells.clone();
        c[idx(move.amazonFrom().row(), move.amazonFrom().col())] = EMPTY;
        c[idx(move.amazonTo().row(), move.amazonTo().col())] = mover == PieceColor.WHITE ? WHITE : BLACK;
        c[idx(move.arrowTo().row(), move.arrowTo().col())] = ARROW;
        return new Board(c);
    }

    public Board copy() {
        return new Board(cells.clone());
    }

    /** Empty board with no pieces at all. Package-private: only for building test fixtures. */
    static Board empty() {
        return new Board(new byte[SIZE * SIZE]);
    }

    /** Board with one additional cell set. Package-private: only for building test fixtures. */
    Board withPiece(Position p, byte value) {
        byte[] c = cells.clone();
        c[idx(p.row(), p.col())] = value;
        return new Board(c);
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        for (int r = SIZE - 1; r >= 0; r--) {
            for (int c = 0; c < SIZE; c++) {
                sb.append(symbolFor(cells[idx(r, c)]));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static char symbolFor(byte cell) {
        return switch (cell) {
            case WHITE -> 'W';
            case BLACK -> 'B';
            case ARROW -> 'x';
            default -> '.';
        };
    }
}
