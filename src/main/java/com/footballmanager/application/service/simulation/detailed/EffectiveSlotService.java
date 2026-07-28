package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class EffectiveSlotService {

    Map<String, LineupSlot> effectiveSlotsForMinute(
            Map<String, LineupSlot> baseSlotsByPlayerId,
            List<MatchContext.ScheduledSub> substitutions,
            String teamId,
            int minute) {
        if (baseSlotsByPlayerId == null || baseSlotsByPlayerId.isEmpty()
            || substitutions == null || substitutions.isEmpty()
            || teamId == null) {
            return baseSlotsByPlayerId;
        }
        Map<String, LineupSlot> effective = null;
        for (MatchContext.ScheduledSub sub : substitutions) {
            if (sub == null
                || !teamId.equals(sub.teamId())
                || sub.effectiveMinute() > minute) {
                continue;
            }
            LineupSlot offSlot = baseSlotsByPlayerId.get(sub.playerOffId());
            if (offSlot == null) {
                continue;
            }
            if (effective == null) {
                effective = new HashMap<>(baseSlotsByPlayerId);
            }
            effective.put(sub.playerOnId(), offSlot);
        }
        return effective != null ? effective : baseSlotsByPlayerId;
    }
}
