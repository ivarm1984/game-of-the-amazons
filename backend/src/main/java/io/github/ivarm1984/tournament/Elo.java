package io.github.ivarm1984.tournament;

/** Standard Elo rating math, applied game-by-game as a tournament progresses. */
public final class Elo {

    public static final double STARTING_RATING = 1500.0;
    public static final double K_FACTOR = 32.0;

    private Elo() {
    }

    public static double expectedScore(double ratingSelf, double ratingOpponent) {
        return 1.0 / (1.0 + Math.pow(10, (ratingOpponent - ratingSelf) / 400.0));
    }

    /** Returns {newWhiteRating, newBlackRating} given white's game score (1 win / 0.5 draw / 0 loss). */
    public static double[] updateRatings(double whiteRating, double blackRating, double whiteScore) {
        double expectedWhite = expectedScore(whiteRating, blackRating);
        double expectedBlack = 1.0 - expectedWhite;
        double blackScore = 1.0 - whiteScore;
        double newWhite = whiteRating + K_FACTOR * (whiteScore - expectedWhite);
        double newBlack = blackRating + K_FACTOR * (blackScore - expectedBlack);
        return new double[] { newWhite, newBlack };
    }
}
