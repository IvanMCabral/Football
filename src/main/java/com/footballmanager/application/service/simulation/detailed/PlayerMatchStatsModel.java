package com.footballmanager.application.service.simulation.detailed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 *
 * <p>Pure helper — no mutable state, no Random, no external dependencies.
 * Deterministic: same players + same timeline = same stat DTOs.
 *
 * <p>Combines MatchTimeline event scanning with PlayerRatingModel for ratings.
 * All players in the input collection are included in output, even if they had no events.
 */
public final class PlayerMatchStatsModel {

    private final PlayerRatingModel ratingModel;

    public PlayerMatchStatsModel() {
        this.ratingModel = new PlayerRatingModel();
    }

    /**
     * Compute stat bundles and ratings for all players given a match timeline.
     *
     * @param players  collection of PlayerMatchState from the match
     * @param timeline the match event timeline
     * @return list of PlayerMatchRatingDto, one per player (including those with no events)
     * @throws NullPointerException if players or timeline is null
     */
    public List<PlayerMatchRatingDto> computeRatings(
            Collection<PlayerMatchState> players,
            MatchTimeline timeline) {
        Objects.requireNonNull(players, "players must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");

        List<PlayerMatchRatingDto> result = new ArrayList<>();
        for (PlayerMatchState player : players) {
            result.add(buildRatingDto(player, timeline));
        }
        return result;
    }

    private PlayerMatchRatingDto buildRatingDto(
            PlayerMatchState player,
            MatchTimeline timeline) {
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

        for (DetailedMatchEvent event : timeline.events()) {
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

        return new PlayerMatchRatingDto(
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
