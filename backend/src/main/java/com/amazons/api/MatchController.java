package com.amazons.api;

import com.amazons.match.MatchRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private static final Duration DEFAULT_SOFT_MOVE_BUDGET = Duration.ofSeconds(3);

    private final MatchRegistry matchRegistry;

    public MatchController(MatchRegistry matchRegistry) {
        this.matchRegistry = matchRegistry;
    }

    public record CreateMatchRequest(String whiteBotId, String blackBotId, Long softMoveBudgetMs) {
    }

    public record CreateMatchResponse(String matchId) {
    }

    @PostMapping
    public CreateMatchResponse createMatch(@RequestBody CreateMatchRequest request) {
        Duration budget = request.softMoveBudgetMs() != null
                ? Duration.ofMillis(request.softMoveBudgetMs())
                : DEFAULT_SOFT_MOVE_BUDGET;
        String matchId = matchRegistry.startMatch(request.whiteBotId(), request.blackBotId(), budget);
        return new CreateMatchResponse(matchId);
    }

    @GetMapping(path = "/{id}/stream", produces = "text/event-stream")
    public SseEmitter stream(@PathVariable String id) {
        return matchRegistry.get(id).subscribe();
    }
}
