package com.amazons.bot;

import com.amazons.engine.PieceColor;

/** Announced to a bot once at the start of a match, before any {@code decideMove} call. */
public record BotContext(PieceColor myColor, String opponentBotId) {
}
