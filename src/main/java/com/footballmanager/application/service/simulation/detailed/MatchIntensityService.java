package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.List;

final class MatchIntensityService {

    double computeTeamAvgOverall(List<SessionPlayer> players) {
        if (players == null || players.isEmpty()) return 50.0;
        int sum = 0;
        int count = 0;
        for (SessionPlayer player : players) {
            if (player != null) {
                Integer overall = player.calculateOverall();
                if (overall != null) {
                    sum += overall;
                    count++;
                }
            }
        }
        return count > 0 ? (double) sum / count : 50.0;
    }

    double computeOverallDiffRatio(double homeOvr, double awayOvr) {
        double max = Math.max(homeOvr, awayOvr);
        if (max <= 0.0) return 0.0;
        return Math.abs(homeOvr - awayOvr) / max;
    }

    double computeMatchIntensity(double diffRatio) {
        final double evenIntensity = 0.60;
        final double unevenIntensity = 1.00;
        final double evenThreshold = 0.05;
        final double unevenThreshold = 0.30;
        if (diffRatio <= evenThreshold) return evenIntensity;
        if (diffRatio >= unevenThreshold) return unevenIntensity;
        double t = (diffRatio - evenThreshold) / (unevenThreshold - evenThreshold);
        return evenIntensity + (unevenIntensity - evenIntensity) * t;
    }
}
