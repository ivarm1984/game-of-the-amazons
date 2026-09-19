package io.github.ivarm1984.bot.impl.search;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.bot.impl.heuristic.BoardEvaluator;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.PieceColor;
import org.springframework.stereotype.Component;

@BotMetadata(id = "minimax-king-distance", displayName = "Minimax King-Distance Bot", difficulty = 7,
        description = "Alpha-beta minimax (iterative deepening, up to 3 plies) scored on mobility plus "
                + "both queen-move and king-step territory, favoring squares it can actually reach soon.")
@Component
public class MinimaxKingDistanceBot implements Bot {

    private static final int MAX_DEPTH = 3;
    private static final int BRANCHING_LIMIT = 8;

    @Override
    public Move decideMove(BotInput input) {
        PieceColor me = input.myColor();
        return NegamaxSearch.findBestMove(input.state().board(), me, input.legalMoves(),
                (board, side) -> BoardEvaluator.combined(board, side, 1.0, 1.0, 1.5),
                input.deadline(), MAX_DEPTH, BRANCHING_LIMIT);
    }
}
