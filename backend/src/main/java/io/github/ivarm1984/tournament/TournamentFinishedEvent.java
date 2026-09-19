package io.github.ivarm1984.tournament;

import java.util.List;

public record TournamentFinishedEvent(List<Standing> finalStandings) implements TournamentEvent {
}
