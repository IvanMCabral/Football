package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.List;

/**
 * Pure helper that applies form updates to CareerSave SessionPlayers
 * from V24 player match ratings.
 *
 * <p>This applier is isolated and has no dependencies on Redis, Spring, or IO.
 * It reads player ratings computed from the match result and mutates SessionPlayer.form.
 *
 * <p>Form update rules (V24D6E MVP):
 * <ul>
 *   <li>Discrete step: >=8.0 → +3, >=7.0 → +2, >=6.5 → +1, >=5.5 → 0, >=5.0 → -1, less than 5.0 → -2</li>
 *   <li>Clamp: form never below 1 or above 99</li>
 *   <li>Null form: treated as 50 on first mutation</li>
 *   <li>No extra red-card penalty: red-card impact already reflected in rating; suspension handled separately by discipline</li>
 *   <li>No team result modifier: form delta comes only from player rating</li>
 *   <li>Starting XI players only (from V24PlayerRatingsAssembler output)</li>
 * </ul>
 */
public final class V24FormMutationApplier {

    public static final int DEFAULT_FORM = 50;
    public static final int MIN_FORM = 1;
    public static final int MAX_FORM = 99;

    /**
     * Apply form updates from V24 player match ratings to CareerSave SessionPlayers.
     *
     * @param career the CareerSave to mutate; if null, returns 0
     * @param result the V24DetailedMatchResult providing starting XI and match context; if null, returns 0
     * @param policy the mutation policy; if null, returns 0
     * @return the number of players whose form was updated
     */
    public int applyForm(CareerSave career, V24DetailedMatchResult result, V24CareerMutationPolicy policy) {
        if (career == null) return 0;
        if (result == null) return 0;
        if (policy == null) return 0;
        if (!policy.isFormPersistenceEnabled()) return 0;

        // Assemble per-player ratings from starting XI + timeline
        List<V24PlayerMatchRatingDto> ratings = assembleRatings(career, result);
        if (ratings == null || ratings.isEmpty()) return 0;

        int count = 0;
        for (V24PlayerMatchRatingDto dto : ratings) {
            String playerId = dto.playerId();
            if (playerId == null || playerId.isBlank()) continue;

            SessionPlayer player = career.getPlayerManager().getSessionPlayer(playerId);
            if (player == null) continue;

            double rating = dto.rating();
            int delta = computeDelta(rating);

            Integer current = player.getForm();
            int base = (current != null) ? current : DEFAULT_FORM;
            int updated = Math.max(MIN_FORM, Math.min(MAX_FORM, base + delta));

            player.setForm(updated);
            count++;
        }
        return count;
    }

    /**
     * Compute form delta from player match rating using discrete step formula.
     */
    int computeDelta(double rating) {
        if (rating >= 8.0) return 3;
        if (rating >= 7.0) return 2;
        if (rating >= 6.5) return 1;
        if (rating >= 5.5) return 0;
        if (rating >= 5.0) return -1;
        return -2;
    }

    /**
     * Assemble player ratings directly from starting XI and timeline.
     * Uses V24PlayerMatchStatsModel directly (no modification to V24PlayerRatingsAssembler needed).
     */
    private List<V24PlayerMatchRatingDto> assembleRatings(CareerSave career, V24DetailedMatchResult result) {
        // Resolve starting XI using career playerManager (not teamStarting11)
        java.util.List<V24PlayerMatchState> allPlayers = new java.util.ArrayList<>();

        for (String teamId : java.util.List.of(result.homeTeamId(), result.awayTeamId())) {
            List<String> starterIds = career.getTeamStarting11().get(teamId);
            if (starterIds == null || starterIds.isEmpty()) continue;
            for (String playerId : starterIds) {
                SessionPlayer sp = career.getPlayerManager().getSessionPlayer(playerId);
                if (sp != null) {
                    allPlayers.add(V24PlayerMatchState.fromSessionPlayer(sp, teamId));
                }
            }
        }

        if (allPlayers.isEmpty()) return List.of();

        // Use V24PlayerMatchStatsModel directly — package-visible, no changes to assembler needed
        V24PlayerMatchStatsModel statsModel = new V24PlayerMatchStatsModel();
        return statsModel.computeRatings(allPlayers, result.timeline());
    }
}