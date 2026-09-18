package io.github.ivarm1984.match;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

/**
 * Kicks off matches asynchronously (each on its own virtual thread, separate
 * from the per-bot-call executor inside {@link MatchRunner}, so one stuck bot
 * can't starve other matches) and keeps a handle per match for SSE streaming.
 */
@Component
public class MatchRegistry {

    private final MatchRunner matchRunner;
    private final ExecutorService matchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, MatchHandle> handles = new ConcurrentHashMap<>();

    public MatchRegistry(MatchRunner matchRunner) {
        this.matchRunner = matchRunner;
    }

    public String startMatch(String whiteBotId, String blackBotId, Duration softMoveBudget) {
        String matchId = UUID.randomUUID().toString();
        MatchHandle handle = new MatchHandle();
        handles.put(matchId, handle);
        MatchConfig config = new MatchConfig(matchId, whiteBotId, blackBotId, softMoveBudget);
        matchExecutor.submit(() -> matchRunner.runSync(config, handle::publish));
        return matchId;
    }

    public MatchHandle get(String matchId) {
        MatchHandle handle = handles.get(matchId);
        if (handle == null) {
            throw new NoSuchElementException("unknown match id: " + matchId);
        }
        return handle;
    }
}
