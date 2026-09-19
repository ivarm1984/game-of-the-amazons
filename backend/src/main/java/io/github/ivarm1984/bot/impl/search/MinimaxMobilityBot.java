package io.github.ivarm1984.bot.impl.search;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.bot.impl.heuristic.BoardEvaluator;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.PieceColor;
import org.springframework.stereotype.Component;

@BotMetadata(id = "minimax-mobility", displayName = "Minimax Mobility Bot", difficulty = 5,
        description = "Alpha-beta minimax (iterative deepening, up to 2 plies) looking one move ahead "
                + "of the opponent's best reply, scored purely on mobility difference.")
@Component
public class MinimaxMobilityBot implements Bot {

    private static final int MAX_DEPTH = 2;
    private static final int BRANCHING_LIMIT = 16;

    @Override
    public Move decideMove(BotInput input) {
        PieceColor me = input.myColor();
        return NegamaxSearch.findBestMove(input.state().board(), me, input.legalMoves(),
                BoardEvaluator::mobilityDiff, input.deadline(), MAX_DEPTH, BRANCHING_LIMIT);
    }
}
