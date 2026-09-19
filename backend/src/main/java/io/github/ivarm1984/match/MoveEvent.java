package io.github.ivarm1984.match;

import io.github.ivarm1984.engine.Board;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.PieceColor;

/**
 * A move was played. Carries the full resulting board (only 100 cells - bandwidth is a
 * non-issue) plus a static evaluation of the resulting position, always from White's point of
 * view (positive favors White), so a spectator UI can chart it without caring whose turn it is.
 */
public record MoveEvent(int ply, PieceColor mover, Move move, Board boardAfter, PieceColor nextToMove,
                         int evaluation) implements MatchEvent {
}
