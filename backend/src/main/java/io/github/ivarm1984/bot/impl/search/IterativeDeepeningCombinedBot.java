package io.github.ivarm1984.bot.impl.search;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.bot.impl.heuristic.BoardEvaluator;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.PieceColor;
import org.springframework.stereotype.Component;

@BotMetadata(id = "iterative-deepening-combined", displayName = "Iterative Deepening Bot", difficulty = 8,
        description = "Alpha-beta minimax, iterative deepening up to 5 plies as the deadline allows, "
                + "scored on a territory-heavy blend of mobility and queen/king-move territory.")
@Component
public class IterativeDeepeningCombinedBot implements Bot {

    private static final int MAX_DEPTH = 5;
    private static final int BRANCHING_LIMIT = 6;

    @Override
    public Move decideMove(BotInput input) {
        PieceColor me = input.myColor();
        return NegamaxSearch.findBestMove(input.state().board(), me, input.legalMoves(),
                (board, side) -> BoardEvaluator.combined(board, side, 1.0, 2.5, 1.0),
                input.deadline(), MAX_DEPTH, BRANCHING_LIMIT);
    }
}
