package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;

import java.util.Map;

record MinuteTacticalState(
        int homeMaxPasser,
        int awayMaxPasser,
        Map<String, LineupSlot> homeEffectiveSlots,
        Map<String, LineupSlot> awayEffectiveSlots,
        TacticalShapeProfile homeShape,
        TacticalShapeProfile awayShape,
        double homeShare) {
}
