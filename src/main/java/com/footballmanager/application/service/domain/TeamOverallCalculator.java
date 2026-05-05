package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.entity.CareerSave;

import java.util.List;
import java.util.function.Function;

/**
 * Pure utility for computing real team OVR from SessionPlayer data.
 *
 * Phase 10B: Centralizes OVR calculation logic that was previously duplicated
 * across LeagueSimulator and CareerSave. Delegates to CareerTeamManager where
 * possible, provides starting XI support, and includes fallback formula matching
 * MatchEngineImpl.calculateTeamOverall().
 *
 * This utility has NO side effects, no repository injection, and is thread-safe.
 * It does NOT call simulateWithStrength() — Phase 10C integration is separate.
 */
public final class TeamOverallCalculator {

    private TeamOverallCalculator() {} // utility class

    /**
     * Calculate team OVR from a sessionTeamId using CareerTeamManager and CareerPlayerManager.
     * Delegates to CareerTeamManager.calculateTeamOVR(sessionTeamId, playerProvider).
     *
     * @return average OVR of all players in the squad, clamped to [1, 100]
     */
    public static int calculateFromSessionTeam(
            String sessionTeamId,
            CareerTeamManager teamManager,
            CareerPlayerManager playerManager) {

        int ovr = teamManager.calculateTeamOVR(
                sessionTeamId,
                playerManager::getSessionPlayer
        );
        return clampOv(ovr);
    }

    /**
     * Calculate team OVR using the starting XI from CareerSave.
     * Falls back to squad average if starting XI is empty or absent.
     *
     * @return average OVR of starting XI players, clamped to [1, 100]
     */
    public static int calculateFromStartingXI(
            String sessionTeamId,
            CareerSave career) {

        List<String> startingIds = career.getTeamStarting11()
                .getOrDefault(sessionTeamId, List.of());

        if (startingIds.isEmpty()) {
            return calculateFromSessionTeam(
                    sessionTeamId,
                    career.getTeamManager(),
                    career.getPlayerManager()
            );
        }

        int ovr = calculateFromPlayerIds(startingIds, career.getPlayerManager()::getSessionPlayer);
        return clampOv(ovr);
    }

    /**
     * Calculate team OVR from a list of player IDs and a lookup function.
     * Missing/null players are ignored. Empty list returns 50 (matches LeagueSimulator behavior).
     *
     * @return average OVR of found players, clamped to [1, 100]
     */
    public static int calculateFromPlayerIds(
            List<String> playerIds,
            Function<String, SessionPlayer> playerProvider) {

        if (playerIds == null || playerIds.isEmpty()) {
            return 50; // matches LeagueSimulator behavior for empty squad
        }

        int total = 0;
        int count = 0;
        for (String pid : playerIds) {
            SessionPlayer p = playerProvider.apply(pid);
            if (p != null) {
                int playerOvr = p.calculateOverall();
                if (playerOvr > 0) {
                    total += playerOvr;
                    count++;
                }
            }
        }

        if (count == 0) {
            return 50;
        }

        return clampOv(total / count);
    }

    /**
     * Fallback OVR calculation from squad size only.
     * Formula exactly matches MatchEngineImpl.calculateTeamOverall():
     * 70 + min(20, squadSize / 2)
     *
     * @return estimated OVR based on squad size, clamped to [1, 100]
     */
    public static int calculateFallbackFromSquadSize(int squadSize) {
        if (squadSize <= 0) {
            return 70; // baseline
        }
        int ovr = 70 + Math.min(20, squadSize / 2);
        return clampOv(ovr);
    }

    /**
     * Clamp OVR to valid range [1, 100].
     */
    private static int clampOv(int ovr) {
        return Math.max(1, Math.min(100, ovr));
    }
}
