package io.github.ivarm1984.api.dto;

import java.util.List;

public record TournamentFinishedEventDto(List<StandingDto> standings) {
}
