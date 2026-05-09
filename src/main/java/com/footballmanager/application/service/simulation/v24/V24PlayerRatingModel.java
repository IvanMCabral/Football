package com.footballmanager.application.service.simulation.v24;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * V24D3B: Pure helper for computing per-player ratings from a match timeline.
 *
 * <p>Ratings are deterministic from the timeline alone — no randomness, no mutable state,
 * no external dependencies. The same timeline always produces the same ratings.
 *
 * <h2>Rating rules</h2>
 * <ul>
 *   <li>Base rating: 6.0</li>
 *   <li>GOAL as scorer (playerId): +0.8</li>
 *   <li>GOAL as assist provider (relatedPlayerId): +0.5</li>
 *   <li>SHOT as shooter (playerId): +0.10 per shot</li>
 *   <li>SHOT as key-pass provider (relatedPlayerId): +0.30 per key pass</li>
 *   <li>SHOT with xG >= 0.30 as shooter: +0.05 bonus (high-quality shot)</li>
 *   <li>YELLOW_CARD (playerId): -0.3 per card</li>
 *   <li>RED_CARD (playerId): -1.5 per red</li>
 *   <li>INJURY (playerId): -0.2 per injury</li>
 *   <li>FOUL (playerId): -0.05 per foul</li>
 *   <li>SUBSTITUTION as incoming player (relatedPlayerId): +0.05 (appearance bonus)</li>
 * </ul>
 *
 * <h2>Double-counting policy</h2>
 * GOAL and SHOT are separate events and both count. If both SHOT and GOAL are emitted
 * for the same attempt, the shot bonus applies to the shot event and the goal bonus
 * applies to the goal event — they are distinct contributions.
 *
 * <h2>Clamping</h2>
 * Final rating is clamped to [1.0, 10.0].
 */
public final class V24PlayerRatingModel {

    public static final double BASE_RATING = 6.0;
    public static final double GOAL_BONUS = 0.8;
    public static final double ASSIST_BONUS = 0.5;
    public static final double SHOT_BONUS = 0.10;
    public static final double KEY_PASS_BONUS = 0.30;
    public static final double HIGH_XG_SHOT_BONUS = 0.05;
    public static final double YELLOW_CARD_PENALTY = 0.3;
    public static final double RED_CARD_PENALTY = 1.5;
    public static final double INJURY_PENALTY = 0.2;
    public static final double FOUL_PENALTY = 0.05;
    public static final double SUBSTITUTION_IN_BONUS = 0.05;

    private static final double HIGH_XG_THRESHOLD = 0.30;

    /**
     * Compute the rating for a single player given the match timeline.
     *
     * @param playerId  the player to rate; must not be null
     * @param timeline  the match timeline; must not be null
     * @return rating clamped to [1.0, 10.0]
     * @throws NullPointerException if playerId or timeline is null
     */
    public double computePlayerRating(String playerId, V24MatchTimeline timeline) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");

        double rating = BASE_RATING;

        for (V24MatchEvent event : timeline.events()) {
            if (playerId.equals(event.playerId())) {
                switch (event.type()) {
                    case GOAL -> rating += GOAL_BONUS;
                    case SHOT -> {
                        rating += SHOT_BONUS;
                        if (event.xg() >= HIGH_XG_THRESHOLD) {
                            rating += HIGH_XG_SHOT_BONUS;
                        }
                    }
                    case YELLOW_CARD -> rating -= YELLOW_CARD_PENALTY;
                    case RED_CARD -> rating -= RED_CARD_PENALTY;
                    case INJURY -> rating -= INJURY_PENALTY;
                    case FOUL -> rating -= FOUL_PENALTY;
                    default -> { /* no rating effect */ }
                }
            }

            if (playerId.equals(event.relatedPlayerId())) {
                switch (event.type()) {
                    case GOAL -> rating += ASSIST_BONUS;
                    case SHOT -> rating += KEY_PASS_BONUS;
                    case SUBSTITUTION -> rating += SUBSTITUTION_IN_BONUS;
                    default -> { /* no rating effect */ }
                }
            }
        }

        return clampRating(rating);
    }

    /**
     * Compute ratings for a collection of players.
     *
     * @param playerIds collection of player IDs to rate; must not be null
     * @param timeline   the match timeline; must not be null
     * @return map of playerId → rating; every requested player is in the map
     * @throws NullPointerException if playerIds or timeline is null
     */
    public Map<String, Double> computeRatings(Collection<String> playerIds, V24MatchTimeline timeline) {
        Objects.requireNonNull(playerIds, "playerIds must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");

        Map<String, Double> result = new HashMap<>();
        for (String pid : playerIds) {
            result.put(pid, computePlayerRating(pid, timeline));
        }
        return result;
    }

    /**
     * Clamp a raw rating value to the valid [1.0, 10.0] range.
     */
    public double clampRating(double rating) {
        return Math.max(1.0, Math.min(10.0, rating));
    }
}