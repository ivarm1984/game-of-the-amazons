package io.github.ivarm1984.engine;

public enum GameStatus {
    IN_PROGRESS,
    WHITE_WINS,
    BLACK_WINS,
    /**
     * Amazons has no true draws - a game always ends when one side runs out of
     * legal moves. This status exists only for aborted/errored matches and for
     * schema robustness; it should never be reached by ordinary play.
     */
    DRAW
}
