package com.amazons.match;

import com.amazons.engine.Board;
import com.amazons.engine.GameResult;

public record MatchFinishedEvent(GameResult result, Board finalBoard) implements MatchEvent {
}
