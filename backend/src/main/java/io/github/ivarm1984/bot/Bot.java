package io.github.ivarm1984.bot;

import io.github.ivarm1984.engine.GameResult;
import io.github.ivarm1984.engine.Move;

/**
 * The interface every bot - including the field bots and your own - implements.
 * This is the entire surface a bot author needs to learn: the engine supplies
 * correct legal moves and handles all rules, a bot only decides which one to play.
 *
 * <p><b>Implementations must be stateless / thread-safe.</b> A single bot instance
 * (a Spring singleton bean) may be invoked concurrently across multiple simultaneous
 * matches, e.g. during a tournament. Any per-move working data - a transposition
 * table, a search tree, an RNG - must live in local variables inside
 * {@link #decideMove}, never in instance fields.
 *
 * <p>Time budget contract: {@link BotInput#deadline()} carries this move's soft
 * time budget. Search-based bots should run iterative deepening (or a
 * simulation-count loop) and poll {@link Deadline#hasExpired()} regularly,
 * returning their best move found so far once the budget is nearly spent. The
 * match runner separately enforces a hard timeout as a backstop: a bot that
 * throws, returns a move absent from {@code legalMoves}, or blows the hard
 * timeout forfeits the game immediately.
 */
public interface Bot {

    Move decideMove(BotInput input);

    default void onGameStart(BotContext context) {
    }

    default void onGameEnd(GameResult result) {
    }
}
