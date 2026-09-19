package io.github.ivarm1984.tournament;

import java.util.List;

/** Emitted once, right after the round-robin schedule is generated. */
public record ScheduleEvent(List<ScheduledGame> games, List<Standing> startingStandings) implements TournamentEvent {
}
