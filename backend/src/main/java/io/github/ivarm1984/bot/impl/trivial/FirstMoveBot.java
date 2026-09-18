package io.github.ivarm1984.bot.impl.trivial;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.engine.Move;
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
