package io.github.ivarm1984.api.dto;

import io.github.ivarm1984.bot.RegisteredBot;

public record BotSummaryDto(String id, String displayName, int difficulty, String description) {

    public static BotSummaryDto from(RegisteredBot bot) {
        return new BotSummaryDto(bot.id(), bot.displayName(), bot.difficulty(), bot.description());
    }
}
