package io.github.ivarm1984.bot.impl.search;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.bot.impl.heuristic.BoardEvaluator;
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
                BoardEvaluator::phaseAwareCombined, input.deadline(), MAX_DEPTH, BRANCHING_LIMIT);
    }
}
