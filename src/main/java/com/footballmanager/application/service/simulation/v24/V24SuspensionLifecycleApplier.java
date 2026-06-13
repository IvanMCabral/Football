package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V24D6D6A: Applies suspension lifecycle decrement for pre-match suspended players
 * after a full round of fixtures has been processed.
 *
 * <p>A suspension is served only when ALL of the following are true:
 * <ul>
 *   <li>Player was suspended before the round started (in preMatchSuspendedPlayerIds)</li>
 *   <li>suspensionRemainingMatches > 0</li>
 *   <li>Player's team had an eligible fixture in the current round</li>
 *   <li>Player did NOT participate in the round (not in participatedPlayerIds)</li>
 *   <li>Player was NOT newly red-carded in this round (not in newlySuspendedPlayerIds)</li>
 * </ul>
 *
 * <p>If participation cannot be reliably verified (participatedPlayerIds is null),
 * no decrement occurs. The applier is safe by design and does not throw exceptions.
 */
public class V24SuspensionLifecycleApplier {

    /**
     * Apply suspension lifecycle decrement for pre-match suspended players
     * after a full round of fixtures has been processed.
     *
     * @param career Current CareerSave (mutated in-place); if null, returns 0
     * @param currentRound The round number that just completed
     * @param roundFixtures All fixtures for the current round; if null, returns 0
     * @param preMatchSuspendedPlayerIds Player IDs where suspended=true BEFORE the round started;
     *        if null or empty, returns 0
     * @param newlySuspendedPlayerIds Player IDs that received a RED_CARD in this round;
     *        if null, treated as empty set
     * @param participatedPlayerIds Player IDs who appeared in any fixture this round;
     *        if null, returns 0 (participation cannot be verified)
     * @param policy Mutation policy; if null, returns 0; if discipline not enabled, returns 0
     * @return the number of players whose suspension status changed
     */
    public int applyServedSuspensions(
            CareerSave career,
            int currentRound,
            List<MatchFixture> roundFixtures,
            Set<String> preMatchSuspendedPlayerIds,
            Set<String> newlySuspendedPlayerIds,
            Set<String> participatedPlayerIds,
            V24CareerMutationPolicy policy) {

        // Null / disabled guards
        if (career == null) return 0;
        if (policy == null) return 0;
        if (!policy.isDisciplinePersistenceEnabled()) return 0;
        if (preMatchSuspendedPlayerIds == null || preMatchSuspendedPlayerIds.isEmpty()) return 0;
        if (roundFixtures == null) return 0;
        // Cannot verify participation — safest behavior is to skip decrement
        if (participatedPlayerIds == null) return 0;

        // Normalize newlySuspendedPlayerIds to empty set if null
        Set<String> newSuspended = newlySuspendedPlayerIds != null
                ? newlySuspendedPlayerIds
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

        for (String playerId : preMatchSuspendedPlayerIds) {
            // Skip if player does not exist
            SessionPlayer player = career.getSessionPlayer(playerId);
            if (player == null) continue;

            // Skip if player is not currently suspended
            if (!Boolean.TRUE.equals(player.getSuspended())) continue;

            // Skip if player was newly red-carded this round
            if (newSuspended.contains(playerId)) continue;

            // Skip if player participated this round.
            // V24D6T2 (bug #7): a currently-suspended player cannot have actually
            // participated even if their ID appears in participatedPlayerIds
            // (e.g. they were in the starting XI but did not play because of
            // their suspension). In that case the participation tracker is
            // wrong and the suspension decrement must still fire. Trust the
            // suspension status over the tracker.
            if (participatedPlayerIds.contains(playerId)
                    && !Boolean.TRUE.equals(player.getSuspended())) {
                continue;
            }

            // Check team fixture eligibility
            String teamId = playerToTeam.get(playerId);
            if (teamId == null) continue;  // Cannot determine team — skip
            if (!teamsWithFixtureThisRound.contains(teamId)) continue;

            // Decrement suspension counter
            Integer remaining = player.getSuspensionRemainingMatches();
            if (remaining == null || remaining <= 0) {
                // suspended=true but remaining=0 — no change needed
                continue;
            }

            int newRemaining = remaining - 1;
            if (newRemaining <= 0) {
                player.setSuspended(false);
                player.setSuspensionRemainingMatches(0);
            } else {
                player.setSuspensionRemainingMatches(newRemaining);
                // suspended stays true
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