package io.github.ivarm1984.bot.impl.heuristic;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.engine.Board;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.MoveGenerator;
import io.github.ivarm1984.engine.PieceColor;
import org.springframework.stereotype.Component;

import java.util.List;

@BotMetadata(id = "greedy-mobility", displayName = "Greedy Mobility Bot", difficulty = 3,
        description = "One-ply lookahead: plays the move that maximizes (my mobility - opponent mobility) afterwards.")
@Component
public class GreedyMobilityBot implements Bot {

    @Override
    public Move decideMove(BotInput input) {
        PieceColor me = input.myColor();
        PieceColor opponent = me.opposite();
        List<Move> legalMoves = input.legalMoves();

        Move best = legalMoves.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (Move move : legalMoves) {
            Board resulting = input.state().board().applyMove(move, me);
            int myMobility = MoveGenerator.generateLegalMoves(resulting, me).size();
            int oppMobility = MoveGenerator.generateLegalMoves(resulting, opponent).size();
            int score = myMobility - oppMobility;
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best;
    }
}
