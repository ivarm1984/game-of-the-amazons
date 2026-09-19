package io.github.ivarm1984.bot.impl.heuristic;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.engine.Board;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.PieceColor;
import org.springframework.stereotype.Component;

import java.util.List;

@BotMetadata(id = "territory", displayName = "Territory Bot", difficulty = 4,
        description = "One-ply lookahead: plays the move that maximizes queen-move territory "
                + "(squares it can reach before the opponent) afterwards.")
@Component
public class TerritoryBot implements Bot {

    @Override
    public Move decideMove(BotInput input) {
        PieceColor me = input.myColor();
        List<Move> legalMoves = input.legalMoves();

        Move best = legalMoves.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (Move move : legalMoves) {
            Board resulting = input.state().board().applyMove(move, me);
            int score = BoardEvaluator.queenTerritoryDiff(resulting, me);
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best;
    }
}
