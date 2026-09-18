package io.github.ivarm1984.api.dto;

import io.github.ivarm1984.engine.GameResult;

public record GameResultDto(String status, String winner, String reason, int totalPlies) {

    public static GameResultDto from(GameResult result) {
        return new GameResultDto(
                result.status().name(),
                result.winner() != null ? result.winner().name() : null,
                result.reason(),
                result.totalPlies());
    }
}
