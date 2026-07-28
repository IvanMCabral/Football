package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.LineupSlot;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class V24AttackContributionService {

    private final V24TacticalEffectivenessService tacticalEffectivenessService;

    V24AttackContributionService(V24TacticalEffectivenessService tacticalEffectivenessService) {
        this.tacticalEffectivenessService = tacticalEffectivenessService;
    }

    double aggregateAttackerStat(
            List<V24PlayerMatchState> players,
            Map<String, LineupSlot> slotsByPlayerId) {
        if (players.isEmpty()) {
            return 70.0;
        }
        return players.stream()
            .filter(V24PlayerMatchState::onPitch)
            .sorted((a, b) -> Integer.compare(b.attack(), a.attack()))
            .limit(7)
            .mapToDouble(p -> p.attack()
                * tacticalEffectivenessService.tacticalEffectiveness(p, slotsByPlayerId)
                * tacticalEffectivenessService.forwardIntentMultiplier(p, slotsByPlayerId))
            .average()
            .orElse(70.0);
    }

    double aggregateCollectiveStat(
            List<V24PlayerMatchState> players,
            Map<String, LineupSlot> slotsByPlayerId) {
        if (players == null || players.isEmpty()) {
            return 70.0;
        }
        return players.stream()
            .filter(V24PlayerMatchState::onPitch)
            .mapToDouble(p -> {
                double outfieldBase = "GK".equals(p.position())
                    ? ((p.defense() + p.mentality()) / 2.0)
                    : ((p.attack() + p.defense() + p.mentality()) / 3.0);
                return outfieldBase * tacticalEffectivenessService.tacticalEffectiveness(p, slotsByPlayerId);
            })
            .average()
            .orElse(70.0);
    }

    double scheduledSubAttackVolumeMultiplier(
            V24TeamMatchState team,
            List<V24MatchContext.ScheduledSub> substitutions,
            String teamId,
            int minute) {
        if (team == null || substitutions == null || substitutions.isEmpty() || teamId == null) {
            return 1.0;
        }
        double delta = 0.0;
        for (V24MatchContext.ScheduledSub sub : substitutions) {
            if (sub == null
                || !teamId.equals(sub.teamId())
                || sub.effectiveMinute() > minute) {
                continue;
            }
            V24PlayerMatchState off = findPlayerForSubImpact(team, sub.playerOffId());
            V24PlayerMatchState on = findPlayerForSubImpact(team, sub.playerOnId());
            if (off == null || on == null) {
                continue;
            }
            delta += substitutionAttackFootprint(on) - substitutionAttackFootprint(off);
        }
        if (Math.abs(delta) < 0.001) {
            return 1.0;
        }
        return clamp(1.0 + (delta / 700.0), 0.86, 1.14);
    }

    private V24PlayerMatchState findPlayerForSubImpact(V24TeamMatchState team, String playerId) {
        if (team == null || playerId == null || playerId.isBlank()) {
            return null;
        }
        for (V24PlayerMatchState player : team.startingPlayers()) {
            if (player != null && playerId.equals(player.sessionPlayerId())) {
                return player;
            }
        }
        for (V24PlayerMatchState player : team.benchPlayers()) {
            if (player != null && playerId.equals(player.sessionPlayerId())) {
                return player;
            }
        }
        return null;
    }

    private double substitutionAttackFootprint(V24PlayerMatchState player) {
        if (player == null) {
            return 0.0;
        }
        String pos = player.naturalPosition() != null ? player.naturalPosition().toUpperCase(Locale.ROOT) : "";
        double roleWeight = switch (pos) {
            case "ATT", "ST", "CF" -> 1.18;
            case "WINGER", "LW", "RW" -> 1.12;
            case "MID", "CM", "CAM", "AM", "LM", "RM" -> 0.96;
            case "DEF", "CB", "LB", "RB", "LWB", "RWB" -> 0.70;
            default -> 0.88;
        };
        return roleWeight * (
            player.attack() * 2.6
                + player.technique() * 1.5
                + player.speed() * 1.1
                + player.mentality() * 0.8
                + player.stamina() * 0.4);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
