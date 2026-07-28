package com.footballmanager.application.service.match;

public record TacticalFormationSlot(
    String playerId,
    String position,
    Integer slotIndex,
    Double customXPercent,
    Double customYPercent
) {
    public TacticalFormationSlot(String playerId, String position) {
        this(playerId, position, null, null, null);
    }
}
