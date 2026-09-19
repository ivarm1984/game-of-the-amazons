package io.github.ivarm1984.api.dto;

import java.util.List;

public record GameFinishedEventDto(int gameIndex, String matchId, GameResultDto result, List<StandingDto> standings) {
}
