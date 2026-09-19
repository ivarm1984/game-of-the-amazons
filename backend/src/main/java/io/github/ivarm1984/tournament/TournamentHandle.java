package io.github.ivarm1984.tournament;

import io.github.ivarm1984.api.dto.GameFinishedEventDto;
import io.github.ivarm1984.api.dto.GameResultDto;
import io.github.ivarm1984.api.dto.GameStartedEventDto;
import io.github.ivarm1984.api.dto.ScheduleEventDto;
import io.github.ivarm1984.api.dto.ScheduledGameDto;
import io.github.ivarm1984.api.dto.StandingDto;
import io.github.ivarm1984.api.dto.TournamentFinishedEventDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds one tournament's event backlog plus any live SSE subscribers, exactly
 * like {@link io.github.ivarm1984.match.MatchHandle} does for a single match.
 */
public final class TournamentHandle {

    private final List<TournamentEvent> events = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();
    private volatile boolean finished = false;

    public synchronized void publish(TournamentEvent event) {
        events.add(event);
        for (SseEmitter emitter : subscribers) {
            send(emitter, event);
        }
        if (event instanceof TournamentFinishedEvent) {
            finished = true;
            for (SseEmitter emitter : subscribers) {
                emitter.complete();
            }
            subscribers.clear();
        }
    }

    public synchronized SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        if (finished) {
            // See MatchHandle.subscribe(): completing synchronously here (before this
            // method even returns the emitter to Spring MVC) races the container's
            // async request lifecycle. Defer so the response commits first.
            List<TournamentEvent> backlog = List.copyOf(events);
            CompletableFuture.runAsync(() -> {
                for (TournamentEvent event : backlog) {
                    send(emitter, event);
                }
                emitter.complete();
            });
            return emitter;
        }
        for (TournamentEvent event : events) {
            send(emitter, event);
        }
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(e -> subscribers.remove(emitter));
        return emitter;
    }

    private void send(SseEmitter emitter, TournamentEvent event) {
        try {
            if (event instanceof ScheduleEvent scheduleEvent) {
                emitter.send(SseEmitter.event().name("schedule").data(toDto(scheduleEvent)));
            } else if (event instanceof GameStartedEvent startedEvent) {
                emitter.send(SseEmitter.event().name("game-started").data(toDto(startedEvent)));
            } else if (event instanceof GameFinishedEvent finishedEvent) {
                emitter.send(SseEmitter.event().name("game-finished").data(toDto(finishedEvent)));
            } else if (event instanceof TournamentFinishedEvent tournamentFinishedEvent) {
                emitter.send(SseEmitter.event().name("finished").data(toDto(tournamentFinishedEvent)));
            }
        } catch (IOException e) {
            subscribers.remove(emitter);
        }
    }

    private static ScheduleEventDto toDto(ScheduleEvent event) {
        List<ScheduledGameDto> games = event.games().stream().map(ScheduledGameDto::from).toList();
        return new ScheduleEventDto(games, StandingDto.from(event.startingStandings()));
    }

    private static GameStartedEventDto toDto(GameStartedEvent event) {
        return new GameStartedEventDto(event.gameIndex(), event.matchId(), event.whiteBotId(), event.blackBotId());
    }

    private static GameFinishedEventDto toDto(GameFinishedEvent event) {
        return new GameFinishedEventDto(
                event.gameIndex(), event.matchId(), GameResultDto.from(event.result()),
                StandingDto.from(event.standings()));
    }

    private static TournamentFinishedEventDto toDto(TournamentFinishedEvent event) {
        return new TournamentFinishedEventDto(StandingDto.from(event.finalStandings()));
    }
}
