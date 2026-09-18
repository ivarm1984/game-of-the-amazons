package com.amazons.match;

public sealed interface MatchEvent permits MoveEvent, MatchFinishedEvent {
}
