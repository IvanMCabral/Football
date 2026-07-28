package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.valueobject.LineupSlot;

import java.util.Map;

record PositionPlan(
    String playerId,
    String playerName,
    double xPercent,
    double yPercent,
    Map<String, LineupSlot> slotsByPlayerId
) {
}
