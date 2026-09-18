package io.github.ivarm1984.match;

import io.github.ivarm1984.engine.Board;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.PieceColor;

/** A move was played. Carries the full resulting board (only 100 cells - bandwidth is a non-issue). */
public record MoveEvent(int ply, PieceColor mover, Move move, Board boardAfter, PieceColor nextToMove)
        implements MatchEvent {
}
