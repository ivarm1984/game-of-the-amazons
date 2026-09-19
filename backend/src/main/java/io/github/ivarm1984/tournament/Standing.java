package io.github.ivarm1984.tournament;

/** One bot's running tournament record. Immutable - each finished game produces a fresh snapshot. */
public record Standing(String botId, double elo, int wins, int losses, int draws, int gamesPlayed) {

    public static Standing initial(String botId) {
        return new Standing(botId, Elo.STARTING_RATING, 0, 0, 0, 0);
    }

    public Standing afterGame(double newElo, double score) {
        int w = wins + (score == 1.0 ? 1 : 0);
        int l = losses + (score == 0.0 ? 1 : 0);
        int d = draws + (score == 0.5 ? 1 : 0);
        return new Standing(botId, newElo, w, l, d, gamesPlayed + 1);
    }
}
