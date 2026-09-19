package io.github.ivarm1984.bot.impl.search;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.bot.impl.heuristic.BoardEvaluator;
import io.github.ivarm1984.engine.Board;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.PieceColor;
import org.springframework.stereotype.Component;

/**
 * The strongest field bot: alpha-beta minimax with iterative deepening up to
 * 6 plies, and a game-phase-aware evaluation. Amazons rewards mobility early
 * (when the board is open) and territory late (once queens are boxed into
 * their own regions), so the evaluation shifts its weights as the board
 * fills up with arrows.
 */
@BotMetadata(id = "alpha-beta-master", displayName = "Alpha-Beta Master Bot", difficulty = 10,
        description = "The strongest field bot: alpha-beta minimax with iterative deepening up to 6 plies "
                + "and a game-phase-aware evaluation that leans on mobility early and territory late.")
@Component
public class AlphaBetaMasterBot implements Bot {

    private static final int MAX_DEPTH = 6;
    private static final int BRANCHING_LIMIT = 6;

    @Override
    public Move decideMove(BotInput input) {
        PieceColor me = input.myColor();
        return NegamaxSearch.findBestMove(input.state().board(), me, input.legalMoves(),
                AlphaBetaMasterBot::evaluate, input.deadline(), MAX_DEPTH, BRANCHING_LIMIT);
    }

    private static int evaluate(Board board, PieceColor sideToMove) {
        double phase = gamePhase(board);
        double mobilityWeight = 1.5 - phase;
        double queenTerritoryWeight = 1.0 + 2.5 * phase;
        double score = mobilityWeight * BoardEvaluator.mobilityDiff(board, sideToMove)
                + queenTerritoryWeight * BoardEvaluator.queenTerritoryDiff(board, sideToMove)
                + BoardEvaluator.kingTerritoryDiff(board, sideToMove);
        return (int) Math.round(score);
    }

    /** 0.0 at the start of the game, approaching 1.0 as the board fills up with arrows. */
    private static double gamePhase(Board board) {
        int emptyCells = 0;
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (board.at(row, col) == Board.EMPTY) {
                    emptyCells++;
                }
            }
        }
        int emptyCellsAtStart = Board.SIZE * Board.SIZE - 8;
        double filled = 1.0 - ((double) emptyCells / emptyCellsAtStart);
        return Math.max(0.0, Math.min(1.0, filled));
    }
}
