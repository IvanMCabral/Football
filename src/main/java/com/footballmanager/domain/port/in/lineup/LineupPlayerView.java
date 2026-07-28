package com.footballmanager.domain.port.in.lineup;

public record LineupPlayerView(
    String playerId,
    String name,
    String position,
    Integer overall,
    Integer energy,
    Boolean injured,
    Integer age,
    Integer yellowCards,
    Integer redCards,
    Boolean suspended,
    Integer suspensionRemainingMatches
) {
}
