package io.github.ivarm1984.match;

import io.github.ivarm1984.api.dto.BoardDto;
import io.github.ivarm1984.api.dto.GameOverEventDto;
import io.github.ivarm1984.api.dto.GameResultDto;
import io.github.ivarm1984.api.dto.MoveDto;
import io.github.ivarm1984.api.dto.MoveEventDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds one match's event backlog plus any live SSE subscribers. A subscriber
 * that (re)connects gets the backlog replayed first, then live-tails - so a
 * refreshed browser tab never misses moves that happened before it connected.
 */
public final class MatchHandle {

    private final List<MatchEvent> events = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();
    private volatile boolean finished = false;

    public synchronized void publish(MatchEvent event) {
        events.add(event);
        for (SseEmitter emitter : subscribers) {
            send(emitter, event);
        }
        if (event instanceof MatchFinishedEvent) {
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
            // A match that already finished by the time this request landed (routine
            // for near-instant bot pairings, e.g. in a tournament) must not be
            // replayed-and-completed synchronously here: emitter.complete() before
            // this method returns the emitter to Spring MVC races the container's
            // async request lifecycle and can wedge the connection (observed as a
            // Tomcat CoyoteAdapter$RecycleRequiredException under rapid-fire matches).
            // Deferring lets the response commit first.
            List<MatchEvent> backlog = List.copyOf(events);
            CompletableFuture.runAsync(() -> {
                for (MatchEvent event : backlog) {
                    send(emitter, event);
                }
                emitter.complete();
            });
            return emitter;
        }
        for (MatchEvent event : events) {
            send(emitter, event);
        }
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(e -> subscribers.remove(emitter));
        return emitter;
    }

    private void send(SseEmitter emitter, MatchEvent event) {
        try {
            if (event instanceof MoveEvent move) {
                emitter.send(SseEmitter.event().name("move").data(toDto(move)));
            } else if (event instanceof MatchFinishedEvent finishedEvent) {
                emitter.send(SseEmitter.event().name("finished").data(toDto(finishedEvent)));
            }
        } catch (IOException e) {
            subscribers.remove(emitter);
        }
    }

    private static MoveEventDto toDto(MoveEvent event) {
        return new MoveEventDto(
                event.ply(),
                event.mover().name(),
                MoveDto.from(event.move()),
                BoardDto.from(event.boardAfter()),
                event.nextToMove().name(),
                event.evaluation());
    }

    private static GameOverEventDto toDto(MatchFinishedEvent event) {
        return new GameOverEventDto(GameResultDto.from(event.result()), BoardDto.from(event.finalBoard()));
    }
}
