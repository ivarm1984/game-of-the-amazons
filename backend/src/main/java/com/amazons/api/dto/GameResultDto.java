package com.amazons.api.dto;

import com.amazons.engine.GameResult;

public record GameResultDto(String status, String winner, String reason, int totalPlies) {

    public static GameResultDto from(GameResult result) {
        return new GameResultDto(
                result.status().name(),
                result.winner() != null ? result.winner().name() : null,
                result.reason(),
                result.totalPlies());
    }
}
