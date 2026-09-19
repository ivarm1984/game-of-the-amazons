package io.github.ivarm1984.api;

import io.github.ivarm1984.tournament.TournamentRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private static final Duration DEFAULT_SOFT_MOVE_BUDGET = Duration.ofSeconds(3);
    private static final int DEFAULT_GAMES_PER_PAIRING = 2;
    private static final int DEFAULT_MAX_PARALLEL_GAMES = 4;

    private final TournamentRegistry tournamentRegistry;

    public TournamentController(TournamentRegistry tournamentRegistry) {
        this.tournamentRegistry = tournamentRegistry;
    }

    public record CreateTournamentRequest(
            List<String> botIds, Integer gamesPerPairing, Long softMoveBudgetMs, Integer maxParallelGames) {
    }

    public record CreateTournamentResponse(String tournamentId) {
    }

    @PostMapping
    public CreateTournamentResponse createTournament(@RequestBody CreateTournamentRequest request) {
        int gamesPerPairing = request.gamesPerPairing() != null ? request.gamesPerPairing() : DEFAULT_GAMES_PER_PAIRING;
        Duration budget = request.softMoveBudgetMs() != null
                ? Duration.ofMillis(request.softMoveBudgetMs())
                : DEFAULT_SOFT_MOVE_BUDGET;
        int maxParallelGames = request.maxParallelGames() != null ? request.maxParallelGames() : DEFAULT_MAX_PARALLEL_GAMES;
        String tournamentId =
                tournamentRegistry.startTournament(request.botIds(), gamesPerPairing, budget, maxParallelGames);
        return new CreateTournamentResponse(tournamentId);
    }

    @GetMapping(path = "/{id}/stream", produces = "text/event-stream")
    public SseEmitter stream(@PathVariable String id) {
        return tournamentRegistry.get(id).subscribe();
    }
}
