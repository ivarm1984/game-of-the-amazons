package com.amazons.bot.impl.heuristic;

import com.amazons.bot.Bot;
import com.amazons.bot.BotInput;
import com.amazons.bot.BotMetadata;
import com.amazons.engine.Board;
import com.amazons.engine.Move;
import com.amazons.engine.MoveGenerator;
import com.amazons.engine.PieceColor;
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
