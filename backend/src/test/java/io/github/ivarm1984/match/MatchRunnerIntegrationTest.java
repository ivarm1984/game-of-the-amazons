package io.github.ivarm1984.match;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.bot.BotRegistry;
import io.github.ivarm1984.bot.impl.trivial.RandomBot;
import io.github.ivarm1984.engine.GameStatus;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.Position;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link MatchRunner} directly through the service layer (no HTTP),
 * covering ordinary play plus all three forfeit paths from the bot timeout
 * contract: exception, hard timeout, and an illegal returned move.
 */
class MatchRunnerIntegrationTest {

    @Test
    void twoRandomBotsPlayToACleanFinish() {
        BotRegistry registry = new BotRegistry(List.of(new RandomBot()));
        MatchRunner runner = new MatchRunner(registry);
        MatchConfig config = new MatchConfig("m1", "random", "random", Duration.ofSeconds(2));

        MatchResult result = runner.runSync(config);

        assertTrue(result.result().status() == GameStatus.WHITE_WINS || result.result().status() == GameStatus.BLACK_WINS);
        assertEquals("NO_LEGAL_MOVES", result.result().reason());
    }

    @Test
    void botThatThrowsForfeitsTheGame() {
        BotRegistry registry = registryOf(new ThrowingBot(), new RandomBot());
        MatchRunner runner = new MatchRunner(registry);
        MatchConfig config = new MatchConfig("m2", "throwing", "random", Duration.ofSeconds(2));

        MatchResult result = runner.runSync(config);

        assertEquals(GameStatus.BLACK_WINS, result.result().status());
        assertTrue(result.result().reason().startsWith("BOT_THREW"), result.result().reason());
    }

    @Test
    void botThatBlowsItsTimeBudgetForfeitsTheGame() {
        BotRegistry registry = registryOf(new SlowBot(), new RandomBot());
        MatchRunner runner = new MatchRunner(registry);
        MatchConfig config = new MatchConfig("m3", "slow", "random", Duration.ofMillis(100));

        MatchResult result = runner.runSync(config);

        assertEquals(GameStatus.BLACK_WINS, result.result().status());
        assertEquals("TIMEOUT", result.result().reason());
    }

    @Test
    void botThatReturnsAnIllegalMoveForfeitsTheGame() {
        BotRegistry registry = registryOf(new IllegalMoveBot(), new RandomBot());
        MatchRunner runner = new MatchRunner(registry);
        MatchConfig config = new MatchConfig("m4", "illegal", "random", Duration.ofSeconds(2));

        MatchResult result = runner.runSync(config);

        assertEquals(GameStatus.BLACK_WINS, result.result().status());
        assertEquals("ILLEGAL_MOVE_RETURNED", result.result().reason());
    }

    @Test
    void botThatReturnsNullForfeitsTheGame() {
        BotRegistry registry = registryOf(new NullMoveBot(), new RandomBot());
        MatchRunner runner = new MatchRunner(registry);
        MatchConfig config = new MatchConfig("m5", "null-move", "random", Duration.ofSeconds(2));

        MatchResult result = runner.runSync(config);

        assertEquals(GameStatus.BLACK_WINS, result.result().status());
        assertEquals("ILLEGAL_MOVE_RETURNED", result.result().reason());
    }

    private static BotRegistry registryOf(Bot white, Bot black) {
        return new BotRegistry(List.of(white, black));
    }

    @BotMetadata(id = "throwing", displayName = "Throwing Bot (test only)", difficulty = 1)
    private static final class ThrowingBot implements Bot {
        @Override
        public Move decideMove(BotInput input) {
            throw new RuntimeException("boom");
        }
    }

    @BotMetadata(id = "slow", displayName = "Slow Bot (test only)", difficulty = 1)
    private static final class SlowBot implements Bot {
        @Override
        public Move decideMove(BotInput input) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
                // simulate a bot that ignores interruption, to exercise the hard-timeout backstop
            }
            return input.legalMoves().get(0);
        }
    }

    @BotMetadata(id = "illegal", displayName = "Illegal Move Bot (test only)", difficulty = 1)
    private static final class IllegalMoveBot implements Bot {
        @Override
        public Move decideMove(BotInput input) {
            return new Move(new Position(0, 0), new Position(0, 1), new Position(0, 2));
        }
    }

    @BotMetadata(id = "null-move", displayName = "Null Move Bot (test only)", difficulty = 1)
    private static final class NullMoveBot implements Bot {
        @Override
        public Move decideMove(BotInput input) {
            return null;
        }
    }
}
