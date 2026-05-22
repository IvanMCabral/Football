package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * V24D6J4: Applies post-round energy recovery to non-participating players.
 *
 * <p>Rule: Players who did NOT appear in any fixture this round recover energy.
 * Participating players are not modified (they already drained through V24FatigueMutationApplier).
 *
 * <p>Recovery is gated by {@code policy.isFatiguePersistenceEnabled()}.
 * This applier is safe by design and does not throw exceptions.
 */
public final class V24EnergyRecoveryLifecycleApplier {

    /**
     * Default energy recovery amount per round for non-participating players.
     */
    public static final int DEFAULT_RECOVERY_PER_ROUND = 8;

    /**
     * Maximum energy cap.
     */
    public static final int MAX_ENERGY = 100;

    /**
     * Apply post-round energy recovery to non-participating players.
     *
     * @param career Current CareerSave (mutated in-place); if null, returns 0
     * @param participatedPlayerIds Player IDs who appeared in any fixture this round;
     *        if null, returns 0 (participation cannot be verified)
     * @param policy Mutation policy; if null returns 0; if fatigue persistence not enabled, returns 0
     * @return the number of players whose energy value changed
     */
    public int applyRecovery(
            CareerSave career,
            Set<String> participatedPlayerIds,
            V24CareerMutationPolicy policy) {

        // Null / disabled guards
        if (career == null) return 0;
        if (policy == null) return 0;
        if (!policy.isFatiguePersistenceEnabled()) return 0;
        if (participatedPlayerIds == null) return 0;

        Set<String> processedPlayerIds = new HashSet<>();
        int changedCount = 0;

        for (var team : career.getAllSessionTeams()) {
            String teamId = team.getSessionTeamId();
            if (teamId == null) continue;

            var squadPlayerIds = career.getSquadPlayerIds(teamId);
            if (squadPlayerIds == null) continue;

            for (String playerId : squadPlayerIds) {
                if (playerId == null) continue;

                // Skip if already processed (player in multiple squads)
                if (!processedPlayerIds.add(playerId)) continue;

                // Skip if participated this round
                if (participatedPlayerIds.contains(playerId)) continue;

                SessionPlayer player = career.getSessionPlayer(playerId);
                if (player == null) continue;

                // Get current energy — null means 100
                int currentEnergy = player.getEnergy() != null ? player.getEnergy() : MAX_ENERGY;

                // Skip if already at cap
                if (currentEnergy >= MAX_ENERGY) continue;

                // Apply recovery
                int newEnergy = Math.min(MAX_ENERGY, currentEnergy + DEFAULT_RECOVERY_PER_ROUND);
                player.setEnergy(newEnergy);
                changedCount++;
            }
        }

        return changedCount;
    }
}