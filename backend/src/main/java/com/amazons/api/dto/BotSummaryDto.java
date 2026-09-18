package com.amazons.api.dto;

import com.amazons.bot.RegisteredBot;

public record BotSummaryDto(String id, String displayName, int difficulty, String description) {

    public static BotSummaryDto from(RegisteredBot bot) {
        return new BotSummaryDto(bot.id(), bot.displayName(), bot.difficulty(), bot.description());
    }
}
