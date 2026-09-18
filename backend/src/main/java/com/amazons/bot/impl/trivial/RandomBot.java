package com.amazons.bot.impl.trivial;

import com.amazons.bot.Bot;
import com.amazons.bot.BotInput;
import com.amazons.bot.BotMetadata;
import com.amazons.engine.Move;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@BotMetadata(id = "random", displayName = "Random Bot", difficulty = 1,
        description = "Plays a uniformly random legal move.")
@Component
public class RandomBot implements Bot {

    @Override
    public Move decideMove(BotInput input) {
        List<Move> moves = input.legalMoves();
        return moves.get(ThreadLocalRandom.current().nextInt(moves.size()));
    }
}
