package io.github.ivarm1984.api.dto;

import io.github.ivarm1984.tournament.Standing;

import java.util.List;

public record StandingDto(String botId, double elo, int wins, int losses, int draws, int gamesPlayed) {

    public static StandingDto from(Standing standing) {
        return new StandingDto(
                standing.botId(), standing.elo(), standing.wins(), standing.losses(), standing.draws(),
                standing.gamesPlayed());
    }

    public static List<StandingDto> from(List<Standing> standings) {
        return standings.stream().map(StandingDto::from).toList();
    }
}
