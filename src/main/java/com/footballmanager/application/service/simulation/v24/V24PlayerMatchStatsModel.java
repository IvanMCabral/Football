package com.footballmanager.application.service.simulation.v24;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * V24D4A: Helper to derive per-player stat bundles and ratings from match timeline.
 *
 * <p>Pure helper — no mutable state, no Random, no external dependencies.
 * Deterministic: same players + same timeline = same stat DTOs.
 *
 * <p>Combines V24MatchTimeline event scanning with V24PlayerRatingModel for ratings.
 * All players in the input collection are included in output, even if they had no events.
 */
public final class V24PlayerMatchStatsModel {

    private final V24PlayerRatingModel ratingModel;

    public V24PlayerMatchStatsModel() {
        this.ratingModel = new V24PlayerRatingModel();
    }

    /**
     * Compute stat bundles and ratings for all players given a match timeline.
     *
     * @param players  collection of V24PlayerMatchState from the match
     * @param timeline the match event timeline
     * @return list of V24PlayerMatchRatingDto, one per player (including those with no events)
     * @throws NullPointerException if players or timeline is null
     */
    public List<V24PlayerMatchRatingDto> computeRatings(
            Collection<V24PlayerMatchState> players,
            V24MatchTimeline timeline) {
        Objects.requireNonNull(players, "players must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");

        List<V24PlayerMatchRatingDto> result = new ArrayList<>();
        for (V24PlayerMatchState player : players) {
            result.add(buildRatingDto(player, timeline));
        }
        return result;
    }

    private V24PlayerMatchRatingDto buildRatingDto(
            V24PlayerMatchState player,
            V24MatchTimeline timeline) {
        String pid = player.sessionPlayerId();

        int goals = 0;
        int assists = 0;
        int keyPasses = 0;
        int shots = 0;
        int yellowCards = 0;
        int redCards = 0;
        int injuries = 0;
        int fouls = 0;
        boolean substitutedIn = false;
        boolean substitutedOut = false;

        for (V24MatchEvent event : timeline.events()) {
            if (pid.equals(event.playerId())) {
                switch (event.type()) {
                    case GOAL -> goals++;
                    case SHOT -> shots++;
                    case YELLOW_CARD -> yellowCards++;
                    case RED_CARD -> redCards++;
                    case INJURY -> injuries++;
                    case FOUL -> fouls++;
                    case SUBSTITUTION -> substitutedOut = true;
                    default -> { /* no stat impact */ }
                }
            }

            if (pid.equals(event.relatedPlayerId())) {
                switch (event.type()) {
                    case GOAL -> assists++;
                    case SHOT -> keyPasses++;
                    case SUBSTITUTION -> substitutedIn = true;
                    default -> { /* no stat impact */ }
                }
            }
        }

        double rating = ratingModel.computePlayerRating(pid, timeline);

        return new V24PlayerMatchRatingDto(
                pid,
                player.name(),
                player.teamId(),
                player.position(),
                rating,
                goals,
                assists,
                keyPasses,
                shots,
                yellowCards,
                redCards,
                injuries,
                fouls,
                substitutedIn,
                substitutedOut
        );
    }
}