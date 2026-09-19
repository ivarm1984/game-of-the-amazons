package io.github.ivarm1984.bot;

import io.github.ivarm1984.bot.impl.heuristic.GreedyMobilityBot;
import io.github.ivarm1984.bot.impl.heuristic.TerritoryBot;
import io.github.ivarm1984.bot.impl.mcts.InformedMonteCarloBot;
import io.github.ivarm1984.bot.impl.mcts.MonteCarloBot;
import io.github.ivarm1984.bot.impl.search.AlphaBetaMasterBot;
import io.github.ivarm1984.bot.impl.search.AlphaBetaTranspositionBot;
import io.github.ivarm1984.bot.impl.search.IterativeDeepeningCombinedBot;
import io.github.ivarm1984.bot.impl.search.MinimaxKingDistanceBot;
import io.github.ivarm1984.bot.impl.search.MinimaxMobilityBot;
import io.github.ivarm1984.bot.impl.search.MinimaxTerritoryBot;
import io.github.ivarm1984.bot.impl.trivial.FirstMoveBot;
import io.github.ivarm1984.bot.impl.trivial.RandomBot;
import io.github.ivarm1984.engine.GameStatus;
import io.github.ivarm1984.match.MatchConfig;
import io.github.ivarm1984.match.MatchResult;
import io.github.ivarm1984.match.MatchRunner;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every field bot, played to completion against {@link RandomBot} on both
 * colors under a tight time budget. Guards against the real risk with
 * search-based bots: that a deeper/slower configuration blows its deadline
 * and forfeits instead of actually playing.
 */
class AllBotsPlayCleanlyTest {

    private static final Duration TIGHT_BUDGET = Duration.ofMillis(300);

    static Stream<String> fieldBotIds() {
        return Stream.of(
                "random", "first-move", "greedy-mobility", "territory",
                "minimax-mobility", "minimax-territory", "minimax-king-distance",
                "iterative-deepening-combined", "mcts", "mcts-informed", "alpha-beta-master", "alpha-beta-tt");
    }

    @ParameterizedTest
    @MethodSource("fieldBotIds")
    void playsWhiteAgainstRandomWithoutForfeiting(String botId) {
        assertClean(runMatch(botId, "random"));
    }

    @ParameterizedTest
    @MethodSource("fieldBotIds")
    void playsBlackAgainstRandomWithoutForfeiting(String botId) {
        assertClean(runMatch("random", botId));
    }

    private static MatchResult runMatch(String whiteId, String blackId) {
        BotRegistry registry = new BotRegistry(List.of(
                new RandomBot(), new FirstMoveBot(), new GreedyMobilityBot(), new TerritoryBot(),
                new MinimaxMobilityBot(), new MinimaxTerritoryBot(), new MinimaxKingDistanceBot(),
                new IterativeDeepeningCombinedBot(), new MonteCarloBot(), new InformedMonteCarloBot(),
                new AlphaBetaMasterBot(), new AlphaBetaTranspositionBot()));
        MatchRunner runner = new MatchRunner(registry);
        MatchConfig config = new MatchConfig("smoke-" + whiteId + "-vs-" + blackId, whiteId, blackId, TIGHT_BUDGET);
        return runner.runSync(config);
    }

    private static void assertClean(MatchResult result) {
        var outcome = result.result();
        assertTrue(outcome.status() == GameStatus.WHITE_WINS
                        || outcome.status() == GameStatus.BLACK_WINS
                        || outcome.status() == GameStatus.DRAW,
                "unexpected status: " + outcome.status());
        assertEquals("NO_LEGAL_MOVES", outcome.reason(), "bot forfeited: " + outcome.reason());
    }
}
