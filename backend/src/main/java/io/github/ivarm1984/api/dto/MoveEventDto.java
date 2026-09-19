package io.github.ivarm1984.api.dto;

public record MoveEventDto(int ply, String mover, MoveDto move, BoardDto board, String nextToMove, int evaluation) {
}
