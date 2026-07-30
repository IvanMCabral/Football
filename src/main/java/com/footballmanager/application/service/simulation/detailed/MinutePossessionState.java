package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;

import java.util.Map;

record MinutePossessionState(
        boolean homeHasPossession,
        TeamMatchState possessor,
        TeamMatchState opponent,
        PlayerSelector selector,
        String teamRole,
        String formation,
        String opponentFormation,
        TacticalShapeProfile possessorShape,
        TacticalShapeProfile opponentShape,
        Map<String, LineupSlot> possessorSlots,
        Map<String, LineupSlot> opponentSlots) {
}
