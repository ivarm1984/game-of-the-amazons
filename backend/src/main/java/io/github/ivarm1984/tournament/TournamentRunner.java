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

/**
 * Runs a round-robin tournament one game at a time: every bot plays every
 * other bot {@code gamesPerPairing} times, alternating colors, in a shuffled
 * play order (see {@link #buildSchedule}) so pairings interleave rather than
 * playing out one full pairing before the next. Each game is a normal match
 * (registered with {@link MatchRegistry} so it can be spectated at
 * /api/matches/{id}/stream exactly like a standalone match) run synchronously
 * on the caller's thread, so games are strictly sequential and the tournament
 * event stream always reflects "what's happening right now". Elo ratings
 * start at {@link Elo#STARTING_RATING} and are updated after each game in
 * play order.
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

        for (ScheduledGame game : schedule) {
            String matchId = UUID.randomUUID().toString();
            MatchHandle matchHandle = matchRegistry.createHandle(matchId);
            listener.onEvent(new GameStartedEvent(game.index(), matchId, game.whiteBotId(), game.blackBotId()));

            MatchConfig matchConfig = new MatchConfig(matchId, game.whiteBotId(), game.blackBotId(), config.softMoveBudget());
            MatchResult matchResult = matchRunner.runSync(matchConfig, matchHandle::publish);

            applyResult(standings, game, matchResult.result());
            listener.onEvent(new GameFinishedEvent(game.index(), matchId, matchResult.result(), sortedStandings(standings)));
        }

        listener.onEvent(new TournamentFinishedEvent(sortedStandings(standings)));
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
