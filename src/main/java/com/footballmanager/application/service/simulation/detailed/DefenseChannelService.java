package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;

import java.util.List;
import java.util.Map;

final class DefenseChannelService {

    private final TacticalPositionService tacticalPositionService;
    private final TacticalEffectivenessService tacticalEffectivenessService;

    DefenseChannelService(
            TacticalPositionService tacticalPositionService,
            TacticalEffectivenessService tacticalEffectivenessService) {
        this.tacticalPositionService = tacticalPositionService;
        this.tacticalEffectivenessService = tacticalEffectivenessService;
    }

    double aggregateDefenderStat(
            List<PlayerMatchState> players,
            Map<String, LineupSlot> slotsByPlayerId) {
        if (players.isEmpty()) {
            return 70.0;
        }
        List<PlayerMatchState> defenders = players.stream()
            .filter(PlayerMatchState::onPitch)
            .filter(p -> p.position().equals("DEF") || p.position().equals("GK"))
            .toList();
        if (defenders.isEmpty()) {
            return players.stream()
                .filter(PlayerMatchState::onPitch)
                .mapToInt(PlayerMatchState::defense)
                .average()
                .orElse(70.0);
        }
        double avg = defenders.stream()
            .mapToDouble(p -> defenderStat(p, slotsByPlayerId))
            .average()
            .orElse(70.0);
        if (slotsByPlayerId == null || slotsByPlayerId.isEmpty()) {
            return avg;
        }
        double weakestLink = defenders.stream()
            .mapToDouble(p -> defenderStat(p, slotsByPlayerId))
            .min()
            .orElse(avg);
        return (avg * 0.45) + (weakestLink * 0.55);
    }

    double aggregateDefenderStatForLocation(
            List<PlayerMatchState> players,
            Map<String, LineupSlot> slotsByPlayerId,
            ShotLocation location,
            ShotCoordinate shotCoordinate,
            double fallbackGlobalDefense) {
        if (players == null || players.isEmpty() || slotsByPlayerId == null || slotsByPlayerId.isEmpty()) {
            return fallbackGlobalDefense;
        }
        List<PlayerMatchState> defenders = players.stream()
            .filter(PlayerMatchState::onPitch)
            .filter(p -> p.position().equals("DEF") || p.position().equals("GK"))
            .toList();
        if (defenders.isEmpty()) {
            return fallbackGlobalDefense;
        }

        double weighted = 0.0;
        double totalWeight = 0.0;
        for (PlayerMatchState defender : defenders) {
            double x = tacticalPositionService.tacticalXPercent(defender, slotsByPlayerId);
            double y = tacticalPositionService.tacticalYPercent(defender, slotsByPlayerId);
            double channelWeight = defenderChannelWeight(location, x, shotCoordinate);
            double depthWeight = "GK".equals(defender.position()) ? 1.05 : clamp(y / 82.0, 0.45, 1.18);
            double weight = channelWeight * depthWeight;
            if (weight <= 0.0) {
                continue;
            }
            weighted += defenderStat(defender, slotsByPlayerId) * weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0.0) {
            return fallbackGlobalDefense;
        }
        double channelDefense = weighted / totalWeight;
        return (channelDefense * 0.80) + (fallbackGlobalDefense * 0.20);
    }

    double defenderRosterChanceVolumeMultiplier(double defenderStat) {
        double delta = (70.0 - defenderStat) / 55.0;
        return clamp(1.0 + delta, 0.78, 1.35);
    }

    private double defenderStat(PlayerMatchState player, Map<String, LineupSlot> slotsByPlayerId) {
        double eff = tacticalEffectivenessService.tacticalEffectiveness(player, slotsByPlayerId);
        return ((player.defense() + player.mentality()) / 2.0) * eff;
    }

    double defenderChannelWeight(
            ShotLocation location,
            double xPercent,
            ShotCoordinate shotCoordinate) {
        double distanceFromCenter = Math.abs(xPercent - 50.0) / 50.0;
        double leftAffinity = tacticalPositionService.laneLeftWeight(xPercent);
        double centerAffinity = tacticalPositionService.laneCenterWeight(xPercent);
        double rightAffinity = tacticalPositionService.laneRightWeight(xPercent);
        double wideAffinity = Math.max(leftAffinity, rightAffinity);
        return switch (location) {
            case PENALTY_AREA_WIDE -> {
                if (shotCoordinate == null) {
                    yield clamp(0.30 + wideAffinity * 1.15 + distanceFromCenter * 0.18, 0.30, 1.58);
                }
                boolean shotLeft = shotCoordinate.y() < 50.0;
                double sameSideAffinity = shotLeft ? leftAffinity : rightAffinity;
                double oppositeSideAffinity = shotLeft ? rightAffinity : leftAffinity;
                yield clamp(0.30
                        + sameSideAffinity * 1.35
                        + oppositeSideAffinity * 0.05
                        + distanceFromCenter * 0.08,
                    0.30, 1.68);
            }
            case SIX_YARD_BOX, PENALTY_AREA_CENTER -> clamp(0.45 + centerAffinity * 0.75, 0.45, 1.20);
            case OUTSIDE_BOX -> clamp(0.85 + centerAffinity * 0.10, 0.85, 0.95);
            case LONG_RANGE -> 0.70;
        };
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
