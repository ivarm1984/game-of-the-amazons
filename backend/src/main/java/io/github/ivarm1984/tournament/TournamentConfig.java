package io.github.ivarm1984.tournament;

import java.time.Duration;
import java.util.List;

public record TournamentConfig(String tournamentId, List<String> botIds, int gamesPerPairing, Duration softMoveBudget,
                                int maxParallelGames) {
}
