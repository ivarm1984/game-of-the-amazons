package com.amazons.engine;

/**
 * Outcome of a finished game. {@code reason} is a machine-readable code
 * ("NO_LEGAL_MOVES" for ordinary play, or a forfeit reason such as "TIMEOUT",
 * "BOT_THREW", "ILLEGAL_MOVE_RETURNED" attached by the match runner) - a
 * forfeit is just a loss with a reason, so callers never need to special-case it.
 */
public record GameResult(GameStatus status, PieceColor winner, String reason, int totalPlies) {

    public static final String NO_LEGAL_MOVES = "NO_LEGAL_MOVES";

    public static GameResult win(PieceColor winner, String reason, int totalPlies) {
        GameStatus status = winner == PieceColor.WHITE ? GameStatus.WHITE_WINS : GameStatus.BLACK_WINS;
        return new GameResult(status, winner, reason, totalPlies);
    }
}
