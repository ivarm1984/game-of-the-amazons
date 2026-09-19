package io.github.ivarm1984.bot.impl.search;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.bot.impl.heuristic.BoardEvaluator;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.PieceColor;
import org.springframework.stereotype.Component;

@BotMetadata(id = "minimax-territory", displayName = "Minimax Territory Bot", difficulty = 6,
        description = "Alpha-beta minimax (iterative deepening, up to 3 plies) scored on a blend of "
                + "mobility and queen-move territory.")
@Component
public class MinimaxTerritoryBot implements Bot {

    private static final int MAX_DEPTH = 3;
    private static final int BRANCHING_LIMIT = 10;

    @Override
    public Move decideMove(BotInput input) {
        PieceColor me = input.myColor();
        return NegamaxSearch.findBestMove(input.state().board(), me, input.legalMoves(),
                (board, side) -> BoardEvaluator.combined(board, side, 1.0, 2.0, 0.0),
                input.deadline(), MAX_DEPTH, BRANCHING_LIMIT);
    }
}
