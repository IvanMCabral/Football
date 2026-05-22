package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * V24D6I2: Applies injury recovery lifecycle decrement for pre-match injured players
 * after a full round of fixtures has been processed.
 *
 * <p>An injury is recovered only when ALL of the following are true:
 * <ul>
 *   <li>Player was injured before the round started (in preRoundInjuredPlayerIds)</li>
 *   <li>injuryRemainingMatches > 0</li>
 *   <li>Player's team had an eligible fixture in the current round</li>
 *   <li>Player did NOT participate in the round (not in participatedPlayerIds)</li>
 *   <li>Player was NOT newly injured in this round (not in newlyInjuredPlayerIds)</li>
 * </ul>
 *
 * <p>If participation cannot be reliably verified (participatedPlayerIds is null),
 * no decrement occurs. The applier is safe by design and does not throw exceptions.
 */
public final class V24InjuryRecoveryLifecycleApplier {

    /**
     * Apply injury recovery lifecycle decrement for pre-match injured players
     * after a full round of fixtures has been processed.
     *
     * @param career Current CareerSave (mutated in-place); if null, returns 0
     * @param currentRound The round number that just completed
     * @param roundFixtures All fixtures for the current round; if null, returns 0
     * @param preRoundInjuredPlayerIds Player IDs where injured=true BEFORE the round started;
     *        if null or empty, returns 0
     * @param newlyInjuredPlayerIds Player IDs that received a new INJURY event this round;
     *        if null, treated as empty set
     * @param participatedPlayerIds Player IDs who appeared in any fixture this round;
     *        if null, returns 0 (participation cannot be verified)
     * @param policy Mutation policy; if null, returns 0; if injury persistence not enabled, returns 0
     * @return the number of players whose injury state changed
     */
    public int applyRecovery(
            CareerSave career,
            int currentRound,
            List<MatchFixture> roundFixtures,
            Set<String> preRoundInjuredPlayerIds,
            Set<String> newlyInjuredPlayerIds,
            Set<String> participatedPlayerIds,
            V24CareerMutationPolicy policy) {

        // Null / disabled guards
        if (career == null) return 0;
        if (policy == null) return 0;
        if (!policy.isInjuryPersistenceEnabled()) return 0;
        if (preRoundInjuredPlayerIds == null || preRoundInjuredPlayerIds.isEmpty()) return 0;
        if (roundFixtures == null) return 0;
        // Cannot verify participation — safest behavior is to skip decrement
        if (participatedPlayerIds == null) return 0;

        // Normalize newlyInjuredPlayerIds to empty set if null
        Set<String> newInjured = newlyInjuredPlayerIds != null
                ? newlyInjuredPlayerIds
                : new HashSet<>();

        // Build set of team IDs that had a fixture in currentRound
        Set<String> teamsWithFixtureThisRound = roundFixtures.stream()
                .filter(f -> f.getRound() == currentRound)
                .flatMap(f -> {
                    Set<String> ids = new HashSet<>();
                    if (f.getHomeTeamId() != null) ids.add(f.getHomeTeamId());
                    if (f.getAwayTeamId() != null) ids.add(f.getAwayTeamId());
                    return ids.stream();
                })
                .collect(Collectors.toSet());

        // Build playerId -> teamId map from CareerSave
        Map<String, String> playerToTeam = buildPlayerToTeamMap(career);

        int appliedCount = 0;

        for (String playerId : preRoundInjuredPlayerIds) {
            // Skip if player does not exist
            SessionPlayer player = career.getSessionPlayer(playerId);
            if (player == null) continue;

            // Skip if player is not currently injured
            if (!Boolean.TRUE.equals(player.getInjured())) continue;

            // Skip if player was newly injured this round
            if (newInjured.contains(playerId)) continue;

            // Skip if player participated this round
            if (participatedPlayerIds.contains(playerId)) continue;

            // Check team fixture eligibility
            String teamId = playerToTeam.get(playerId);
            if (teamId == null) continue;  // Cannot determine team — skip
            if (!teamsWithFixtureThisRound.contains(teamId)) continue;

            // Apply recovery
            Integer remaining = player.getInjuryRemainingMatches();
            if (remaining == null || remaining <= 0) {
                // injured=true but remaining <= 0 — no change needed
                continue;
            }

            if (remaining > 1) {
                player.setInjuryRemainingMatches(remaining - 1);
                // injured stays true, injuryType unchanged
            } else {
                player.setInjured(false);
                player.setInjuryRemainingMatches(0);
                player.setInjuryType(null);  // clear on full recovery
            }
            appliedCount++;
        }

        return appliedCount;
    }

    /**
     * Builds a Map of playerId -> sessionTeamId from CareerSave.
     * Uses CareerTeamManager.getSquadPlayerIds to iterate each team.
     */
    private Map<String, String> buildPlayerToTeamMap(CareerSave career) {
        return career.getAllSessionTeams().stream()
                .flatMap(team -> {
                    List<String> playerIds = career.getSquadPlayerIds(team.getSessionTeamId());
                    return playerIds.stream()
                            .map(pid -> Map.entry(pid, team.getSessionTeamId()));
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1  // In case of duplicates, keep first
                ));
    }
}