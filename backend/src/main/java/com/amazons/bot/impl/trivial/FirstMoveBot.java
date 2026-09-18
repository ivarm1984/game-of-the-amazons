package com.amazons.bot.impl.trivial;

import com.amazons.bot.Bot;
import com.amazons.bot.BotInput;
import com.amazons.bot.BotMetadata;
import com.amazons.engine.Move;
import org.springframework.stereotype.Component;

@BotMetadata(id = "first-move", displayName = "First Move Bot", difficulty = 1,
        description = "Always plays the first legal move it is given. Floor baseline.")
@Component
public class FirstMoveBot implements Bot {

    @Override
    public Move decideMove(BotInput input) {
        return input.legalMoves().get(0);
    }
}
