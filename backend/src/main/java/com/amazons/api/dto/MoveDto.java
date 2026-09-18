package com.amazons.api.dto;

import com.amazons.engine.Move;

public record MoveDto(String from, String to, String arrow) {

    public static MoveDto from(Move move) {
        return new MoveDto(move.amazonFrom().toString(), move.amazonTo().toString(), move.arrowTo().toString());
    }
}
