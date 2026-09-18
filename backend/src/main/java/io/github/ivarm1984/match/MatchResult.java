package io.github.ivarm1984.match;

import io.github.ivarm1984.engine.GameResult;
import io.github.ivarm1984.engine.Move;

import java.util.List;

public record MatchResult(String matchId, GameResult result, List<Move> moves) {
}
