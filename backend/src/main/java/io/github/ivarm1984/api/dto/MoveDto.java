package io.github.ivarm1984.api.dto;

import io.github.ivarm1984.engine.Move;

public record MoveDto(String from, String to, String arrow) {

    public static MoveDto from(Move move) {
        return new MoveDto(move.amazonFrom().toString(), move.amazonTo().toString(), move.arrowTo().toString());
    }
}
