package io.github.ivarm1984.api.dto;

import io.github.ivarm1984.engine.Board;

import java.util.ArrayList;
import java.util.List;

/** Wire-format board snapshot: row 0 first, one character per cell ('.', 'W', 'B', 'x'). */
public record BoardDto(List<String> rows) {

    public static BoardDto from(Board board) {
        List<String> rows = new ArrayList<>(Board.SIZE);
        for (int r = 0; r < Board.SIZE; r++) {
            StringBuilder sb = new StringBuilder(Board.SIZE);
            for (int c = 0; c < Board.SIZE; c++) {
                sb.append(symbol(board.at(r, c)));
            }
            rows.add(sb.toString());
        }
        return new BoardDto(rows);
    }

    private static char symbol(byte cell) {
        return switch (cell) {
            case Board.WHITE -> 'W';
            case Board.BLACK -> 'B';
            case Board.ARROW -> 'x';
            default -> '.';
        };
    }
}
