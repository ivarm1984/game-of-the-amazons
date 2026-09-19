package io.github.ivarm1984.api.dto;

public record GameStartedEventDto(int gameIndex, String matchId, String whiteBotId, String blackBotId) {
}
