package com.amazons.match;

import com.amazons.engine.GameResult;
import com.amazons.engine.Move;

import java.util.List;

public record MatchResult(String matchId, GameResult result, List<Move> moves) {
}
