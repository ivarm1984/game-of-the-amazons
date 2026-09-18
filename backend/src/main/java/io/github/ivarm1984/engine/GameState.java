package io.github.ivarm1984.engine;

import java.util.ArrayList;
import java.util.List;

/** Immutable snapshot of a game in progress: the board, whose turn it is, and the move history. */
public final class GameState {

    private final Board board;
    private final PieceColor sideToMove;
    private final int moveNumber;
    private final List<Move> history;

    public GameState(Board board, PieceColor sideToMove, int moveNumber, List<Move> history) {
        this.board = board;
        this.sideToMove = sideToMove;
        this.moveNumber = moveNumber;
        this.history = List.copyOf(history);
    }

    public static GameState initial() {
        return new GameState(Board.initial(), PieceColor.WHITE, 0, List.of());
    }

    public Board board() {
        return board;
    }

    public PieceColor sideToMove() {
        return sideToMove;
    }

    public int moveNumber() {
        return moveNumber;
    }

    public List<Move> history() {
        return history;
    }

    public GameState afterMove(Move move) {
        Board next = board.applyMove(move, sideToMove);
        List<Move> nextHistory = new ArrayList<>(history);
        nextHistory.add(move);
        return new GameState(next, sideToMove.opposite(), moveNumber + 1, nextHistory);
    }
}
