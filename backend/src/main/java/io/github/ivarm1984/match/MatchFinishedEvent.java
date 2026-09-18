package io.github.ivarm1984.match;

import io.github.ivarm1984.engine.Board;
import io.github.ivarm1984.engine.GameResult;

public record MatchFinishedEvent(GameResult result, Board finalBoard) implements MatchEvent {
}
