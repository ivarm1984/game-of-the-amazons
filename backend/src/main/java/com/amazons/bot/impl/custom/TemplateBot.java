package com.amazons.bot.impl.custom;

import com.amazons.bot.Bot;
import com.amazons.bot.BotInput;
import com.amazons.bot.BotMetadata;
import com.amazons.engine.Move;
import org.springframework.stereotype.Component;

/**
 * Copy this class (rename it, give it a new @BotMetadata id) to write your own
 * bot. {@code input.legalMoves()} is always non-empty and pre-validated by the
 * engine - just pick one. {@code input.deadline()} carries your per-move time
 * budget; for anything beyond a simple heuristic, poll {@code deadline.hasExpired()}
 * inside an iterative-deepening or simulation loop (see the search/mcts bots for
 * examples) and return your best move so far once it fires.
 */
@BotMetadata(id = "template", displayName = "Template Bot", difficulty = 1,
        description = "Starting point for your own bot - currently just plays the first legal move.")
@Component
public class TemplateBot implements Bot {

    @Override
    public Move decideMove(BotInput input) {
        // TODO: replace with your own decision logic.
        return input.legalMoves().get(0);
    }
}
