package io.github.ivarm1984.tournament;

/** {@code matchId} lets the viewer open /api/matches/{matchId}/stream to spectate this game live. */
public record GameStartedEvent(int gameIndex, String matchId, String whiteBotId, String blackBotId)
        implements TournamentEvent {
}
