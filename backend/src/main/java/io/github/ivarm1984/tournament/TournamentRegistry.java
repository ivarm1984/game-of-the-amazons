package io.github.ivarm1984.tournament;

import io.github.ivarm1984.bot.BotRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kicks off tournaments asynchronously (each on its own virtual thread, so a
 * long-running tournament can't block the request or other tournaments) and
 * keeps a handle per tournament for SSE streaming.
 */
@Component
public class TournamentRegistry {

    private final TournamentRunner tournamentRunner;
    private final BotRegistry botRegistry;
    private final ExecutorService tournamentExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, TournamentHandle> handles = new ConcurrentHashMap<>();

    public TournamentRegistry(TournamentRunner tournamentRunner, BotRegistry botRegistry) {
        this.tournamentRunner = tournamentRunner;
        this.botRegistry = botRegistry;
    }

    public String startTournament(List<String> botIds, int gamesPerPairing, Duration softMoveBudget) {
        Set<String> distinctBotIds = new HashSet<>(botIds);
        if (distinctBotIds.size() < 2) {
            throw new IllegalArgumentException("a tournament needs at least 2 distinct bots");
        }
        if (gamesPerPairing < 1) {
            throw new IllegalArgumentException("gamesPerPairing must be at least 1");
        }
        for (String botId : distinctBotIds) {
            if (botRegistry.find(botId).isEmpty()) {
                throw new IllegalArgumentException("unknown bot id: " + botId);
            }
        }

        String tournamentId = UUID.randomUUID().toString();
        TournamentHandle handle = new TournamentHandle();
        handles.put(tournamentId, handle);
        TournamentConfig config = new TournamentConfig(tournamentId, List.copyOf(botIds), gamesPerPairing, softMoveBudget);
        tournamentExecutor.submit(() -> tournamentRunner.run(config, handle::publish));
        return tournamentId;
    }

    public TournamentHandle get(String tournamentId) {
        TournamentHandle handle = handles.get(tournamentId);
        if (handle == null) {
            throw new NoSuchElementException("unknown tournament id: " + tournamentId);
        }
        return handle;
    }
}
