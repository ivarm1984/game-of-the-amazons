package io.github.ivarm1984.api.dto;

import java.util.List;

public record ScheduleEventDto(List<ScheduledGameDto> games, List<StandingDto> standings) {
}
