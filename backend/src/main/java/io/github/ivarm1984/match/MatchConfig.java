package io.github.ivarm1984.match;

import java.time.Duration;

public record MatchConfig(String matchId, String whiteBotId, String blackBotId, Duration softMoveBudget) {
}
