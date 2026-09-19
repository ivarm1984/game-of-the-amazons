package io.github.ivarm1984.tournament;

import io.github.ivarm1984.engine.GameResult;
import io.github.ivarm1984.match.MatchConfig;
import io.github.ivarm1984.match.MatchHandle;
import io.github.ivarm1984.match.MatchRegistry;
import io.github.ivarm1984.match.MatchResult;
import io.github.ivarm1984.match.MatchRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs a round-robin tournament: every bot plays every other bot
 * {@code gamesPerPairing} times, alternating colors, in a shuffled play order
 * (see {@link #buildSchedule}) so pairings interleave rather than playing out
 * one full pairing before the next. Each game is a normal match (registered
 * with {@link MatchRegistry} so it can be spectated at
 * /api/matches/{id}/stream exactly like a standalone match).
 *
 * <p>Up to {@link TournamentConfig#maxParallelGames} games are computed
 * concurrently on a fixed worker pool, since a single game is turn-based and
 * only ever keeps one CPU core busy at a time - running several games side by
 * side is what actually spreads work across the machine's cores. {@code
 * game-started}
 * events fire as each worker picks up its game, which - because a fixed pool
 * pulls queued tasks in submission (i.e. schedule) order - still arrives in
 * schedule order even though several fire in quick succession. Elo ratings,
 * however, are only ever updated by the single sequencing loop at the bottom
 * of {@link #run}, which walks the schedule in order and blocks on each
 * game's result before applying it: this guarantees {@code game-finished}
 * standings snapshots are always "as of exactly this point in schedule
 * order", never contaminated by a later game that happened to finish first,
 * which matters because the frontend replays games strictly in schedule
 * order and must not see a future game's effect on standings early.
 */
@Component
public class TournamentRunner {

    private final MatchRunner matchRunner;
    private final MatchRegistry matchRegistry;

    public TournamentRunner(MatchRunner matchRunner, MatchRegistry matchRegistry) {
        this.matchRunner = matchRunner;
        this.matchRegistry = matchRegistry;
    }

    public void run(TournamentConfig config, TournamentEventListener listener) {
        List<ScheduledGame> schedule = buildSchedule(config.botIds(), config.gamesPerPairing());

        Map<String, Standing> standings = new LinkedHashMap<>();
        for (String botId : config.botIds()) {
            standings.put(botId, Standing.initial(botId));
        }
        listener.onEvent(new ScheduleEvent(schedule, sortedStandings(standings)));

        int parallelism = Math.max(1, Math.min(config.maxParallelGames(),
                Math.min(schedule.size(), Runtime.getRuntime().availableProcessors())));
        ExecutorService gameExecutor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<MatchResult>> futures = new ArrayList<>(schedule.size());
            for (ScheduledGame game : schedule) {
                String matchId = UUID.randomUUID().toString();
                MatchHandle matchHandle = matchRegistry.createHandle(matchId);
                futures.add(gameExecutor.submit(() -> playGame(config, game, matchId, matchHandle, listener)));
            }

            for (int i = 0; i < schedule.size(); i++) {
                ScheduledGame game = schedule.get(i);
                MatchResult matchResult = await(futures.get(i));
                applyResult(standings, game, matchResult.result());
                listener.onEvent(new GameFinishedEvent(
                        game.index(), matchResult.matchId(), matchResult.result(), sortedStandings(standings)));
            }
        } finally {
            gameExecutor.shutdown();
        }

        listener.onEvent(new TournamentFinishedEvent(sortedStandings(standings)));
    }

    private MatchResult playGame(TournamentConfig config, ScheduledGame game, String matchId,
                                  MatchHandle matchHandle, TournamentEventListener listener) {
        listener.onEvent(new GameStartedEvent(game.index(), matchId, game.whiteBotId(), game.blackBotId()));
        MatchConfig matchConfig = new MatchConfig(matchId, game.whiteBotId(), game.blackBotId(), config.softMoveBudget());
        return matchRunner.runSync(matchConfig, matchHandle::publish);
    }

    private static MatchResult await(Future<MatchResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("tournament interrupted while waiting for a game", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("tournament game failed", e.getCause());
        }
    }

    private void applyResult(Map<String, Standing> standings, ScheduledGame game, GameResult result) {
        Standing white = standings.get(game.whiteBotId());
        Standing black = standings.get(game.blackBotId());

        double whiteScore = switch (result.status()) {
            case WHITE_WINS -> 1.0;
            case BLACK_WINS -> 0.0;
            default -> 0.5;
        };

        double[] updatedRatings = Elo.updateRatings(white.elo(), black.elo(), whiteScore);
        standings.put(white.botId(), white.afterGame(updatedRatings[0], whiteScore));
        standings.put(black.botId(), black.afterGame(updatedRatings[1], 1.0 - whiteScore));
    }

    private static List<ScheduledGame> buildSchedule(List<String> botIds, int gamesPerPairing) {
        List<ScheduledGame> pairings = new ArrayList<>();
        for (int i = 0; i < botIds.size(); i++) {
            for (int j = i + 1; j < botIds.size(); j++) {
                for (int g = 0; g < gamesPerPairing; g++) {
                    boolean firstBotIsWhite = g % 2 == 0;
                    String white = firstBotIsWhite ? botIds.get(i) : botIds.get(j);
                    String black = firstBotIsWhite ? botIds.get(j) : botIds.get(i);
                    pairings.add(new ScheduledGame(pairings.size(), white, black));
                }
            }
        }
        // Shuffled so pairings interleave across the whole tournament instead of playing out
        // pairing-by-pairing - otherwise early-indexed bots rack up games (and Elo swings) long
        // before later-indexed ones get their first game, which makes the standings and Elo chart
        // look lopsided/incomplete until the very last pairing finally starts.
        Collections.shuffle(pairings);
        List<ScheduledGame> schedule = new ArrayList<>(pairings.size());
        for (int i = 0; i < pairings.size(); i++) {
            ScheduledGame game = pairings.get(i);
            schedule.add(new ScheduledGame(i, game.whiteBotId(), game.blackBotId()));
        }
        return schedule;
    }

    private static List<Standing> sortedStandings(Map<String, Standing> standings) {
        return standings.values().stream()
                .sorted(Comparator.comparingDouble(Standing::elo).reversed())
                .toList();
    }
}
