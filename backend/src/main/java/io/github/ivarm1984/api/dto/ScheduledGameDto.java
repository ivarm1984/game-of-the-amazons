package io.github.ivarm1984.api.dto;

import io.github.ivarm1984.tournament.ScheduledGame;

public record ScheduledGameDto(int index, String whiteBotId, String blackBotId) {

    public static ScheduledGameDto from(ScheduledGame game) {
        return new ScheduledGameDto(game.index(), game.whiteBotId(), game.blackBotId());
    }
}
