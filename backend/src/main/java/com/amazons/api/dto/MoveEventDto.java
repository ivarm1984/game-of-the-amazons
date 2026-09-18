package com.amazons.api.dto;

public record MoveEventDto(int ply, String mover, MoveDto move, BoardDto board, String nextToMove) {
}
