package io.github.ivarm1984.tournament;

public sealed interface TournamentEvent
        permits ScheduleEvent, GameStartedEvent, GameFinishedEvent, TournamentFinishedEvent {
}
