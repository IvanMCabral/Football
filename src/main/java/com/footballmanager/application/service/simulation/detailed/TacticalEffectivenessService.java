package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.PositionEffectivenessCalculator;
import com.footballmanager.domain.model.valueobject.SubdivisionEffectivenessCalculator;

import java.util.Map;

final class TacticalEffectivenessService {

    private final TacticalPositionService tacticalPositionService;

    TacticalEffectivenessService(TacticalPositionService tacticalPositionService) {
        this.tacticalPositionService = tacticalPositionService;
    }

    double tacticalEffectiveness(
            PlayerMatchState player,
            Map<String, LineupSlot> slotsByPlayerId) {
        if (player == null) {
            return 1.0;
        }
        LineupSlot slot = tacticalPositionService.slotFor(player, slotsByPlayerId);
        if (slot == null) {
            return PositionEffectivenessCalculator.effectiveness(
                player.naturalPosition(), player.position());
        }
        double x = tacticalPositionService.tacticalXPercent(player, slotsByPlayerId);
        double y = tacticalPositionService.tacticalYPercent(player, slotsByPlayerId);
        return SubdivisionEffectivenessCalculator.effectiveness(
            player.naturalPosition(),
            x,
            y,
            player.position());
    }

    double forwardIntentMultiplier(
            PlayerMatchState player,
            Map<String, LineupSlot> slotsByPlayerId) {
        if (player == null) {
            return 1.0;
        }
        LineupSlot slot = tacticalPositionService.slotFor(player, slotsByPlayerId);
        if (slot == null || slot.customYPercent() == null || Double.isNaN(slot.customYPercent())) {
            return 1.0;
        }
        double y = slot.customYPercent();
        if ("ATT".equals(player.position())) {
            double forward = clamp((22.0 - y) / 18.0, 0.0, 1.0);
            double width = clamp(Math.abs(tacticalPositionService.tacticalXPercent(player, slotsByPlayerId) - 50.0) / 50.0, 0.0, 1.0);
            return 1.0 + (0.12 * forward) + (0.05 * width);
        }
        double forward = clamp((55.0 - y) / 40.0, 0.0, 1.0);
        return 1.0 + (0.25 * forward);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
