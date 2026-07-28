package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;

/**
 * Pure helper that applies detailed INJURY events from a match result to CareerSave SessionPlayers.
 *
 * <p>This applier is isolated and has no dependencies on Redis, Spring, or IO.
 *
 * <p>Default injury values:
 * <ul>
 *   <li>injuryType = "MATCH_INJURY"</li>
 *   <li>injuryRemainingMatches = 2</li>
 * </ul>
 */
public class InjuryMutationApplier {

    public static final String DEFAULT_INJURY_TYPE = "MATCH_INJURY";
    public static final int DEFAULT_INJURY_DURATION_MATCHES = 2;

    /**
     * Apply detailed INJURY events from the match result to CareerSave SessionPlayers.
     *
     * @param career the CareerSave to mutate; if null, returns 0
     * @param policy the mutation policy; if null, returns 0
     * @return the number of players newly marked as injured
     */
    public int applyInjuries(CareerSave career, DetailedMatchResult result, CareerMutationPolicy policy) {
        if (career == null) return 0;
        if (result == null) return 0;
        if (policy == null) return 0;
        if (!policy.isInjuryPersistenceEnabled()) return 0;

        int appliedCount = 0;

        for (DetailedMatchEvent event : result.timeline().events()) {
            if (event.type() != DetailedMatchEventType.INJURY) continue;

            String playerId = event.playerId();
            if (playerId == null || playerId.isBlank()) continue;

            SessionPlayer player = career.getSessionPlayer(playerId);
            if (player == null) continue;

            // Do not overwrite existing injury
            if (Boolean.TRUE.equals(player.getInjured())) continue;

            player.setInjured(true);
            player.setInjuryType(DEFAULT_INJURY_TYPE);
            player.setInjuryRemainingMatches(DEFAULT_INJURY_DURATION_MATCHES);
            appliedCount++;
        }

        return appliedCount;
    }
}
