package com.amazons.engine;

import java.util.List;
import java.util.Optional;

/** Facade tying the engine pieces together: the only entry point callers outside {@code engine} need. */
public final class AmazonsGame {

    private AmazonsGame() {
    }

    public static GameState initial() {
        return GameState.initial();
    }

    public static List<Move> legalMoves(GameState state) {
        return MoveGenerator.generateLegalMoves(state.board(), state.sideToMove());
    }

    public static GameState apply(GameState state, Move move) {
        return state.afterMove(move);
    }

    /** Terminal check using freshly-generated legal moves. Prefer {@link #result(GameState, List)} if you already have them. */
    public static Optional<GameResult> result(GameState state) {
        return result(state, legalMoves(state));
    }

    /** Terminal check reusing legal moves the caller already generated for this state, avoiding duplicate work. */
    public static Optional<GameResult> result(GameState state, List<Move> legalMovesForSideToMove) {
        if (!legalMovesForSideToMove.isEmpty()) {
            return Optional.empty();
        }
        PieceColor winner = state.sideToMove().opposite();
        return Optional.of(GameResult.win(winner, GameResult.NO_LEGAL_MOVES, state.moveNumber()));
    }
}
