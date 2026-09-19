package io.github.ivarm1984.tournament;

import io.github.ivarm1984.engine.GameResult;

import java.util.List;

public record GameFinishedEvent(int gameIndex, String matchId, GameResult result, List<Standing> standings)
        implements TournamentEvent {
}
