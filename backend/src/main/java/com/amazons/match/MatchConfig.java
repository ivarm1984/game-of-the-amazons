package com.amazons.match;

import java.time.Duration;

public record MatchConfig(String matchId, String whiteBotId, String blackBotId, Duration softMoveBudget) {
}
