package io.github.ivarm1984.bot.impl.search;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.bot.impl.heuristic.BoardEvaluator;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.PieceColor;
import org.springframework.stereotype.Component;

/**
 * Alpha-beta minimax like {@link AlphaBetaMasterBot}, but searched with {@link TranspositionSearch}
 * instead of the plain {@link NegamaxSearch}: a Zobrist-hashed transposition table caches positions
 * reached by different move orders and remembers each node's best move to sharpen alpha-beta
 * cutoffs, letting the search reach further within the same per-move deadline.
 */
@BotMetadata(id = "alpha-beta-tt", displayName = "Transposition Alpha-Beta Bot", difficulty = 10,
        description = "Alpha-beta minimax backed by a Zobrist-hashed transposition table for position "
                + "caching and hash-move ordering, reaching deeper search than a plain alpha-beta bot "
                + "in the same time budget.")
@Component
public class AlphaBetaTranspositionBot implements Bot {

    private static final int MAX_DEPTH = 10;
    private static final int BRANCHING_LIMIT = 6;

    @Override
    public Move decideMove(BotInput input) {
        PieceColor me = input.myColor();
        return TranspositionSearch.findBestMove(input.state().board(), me, input.legalMoves(),
                BoardEvaluator::phaseAwareCombined, input.deadline(), MAX_DEPTH, BRANCHING_LIMIT);
    }
}
