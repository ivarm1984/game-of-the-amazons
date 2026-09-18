package com.amazons.match;

import com.amazons.engine.Board;
import com.amazons.engine.Move;
import com.amazons.engine.PieceColor;

/** A move was played. Carries the full resulting board (only 100 cells - bandwidth is a non-issue). */
public record MoveEvent(int ply, PieceColor mover, Move move, Board boardAfter, PieceColor nextToMove)
        implements MatchEvent {
}
