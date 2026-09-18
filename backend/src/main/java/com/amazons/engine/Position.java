package com.amazons.engine;

public record Position(int row, int col) {

    public static final int BOARD_SIZE = 10;

    public Position {
        if (!isValid(row, col)) {
            throw new IllegalArgumentException("Position out of bounds: (" + row + "," + col + ")");
        }
    }

    public static boolean isValid(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    @Override
    public String toString() {
        char file = (char) ('a' + col);
        int rank = row + 1;
        return "" + file + rank;
    }
}
