package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * V24D4A: DTO for per-player match rating and stat bundle.
 * Immutable snapshot — not tied to V24PlayerMatchState.
 */
public final class V24PlayerMatchRatingDto {

    private final String playerId;
    private final String playerName;
    private final String teamId;
    private final String position;
    private final double rating;
    private final int goals;
    private final int assists;
    private final int keyPasses;
    private final int shots;
    private final int yellowCards;
    private final int redCards;
    private final int injuries;
    private final int fouls;
    private final boolean substitutedIn;
    private final boolean substitutedOut;

    @JsonCreator
    public V24PlayerMatchRatingDto(
            @JsonProperty("playerId") String playerId,
            @JsonProperty("playerName") String playerName,
            @JsonProperty("teamId") String teamId,
            @JsonProperty("position") String position,
            @JsonProperty("rating") double rating,
            @JsonProperty("goals") int goals,
            @JsonProperty("assists") int assists,
            @JsonProperty("keyPasses") int keyPasses,
            @JsonProperty("shots") int shots,
            @JsonProperty("yellowCards") int yellowCards,
            @JsonProperty("redCards") int redCards,
            @JsonProperty("injuries") int injuries,
            @JsonProperty("fouls") int fouls,
            @JsonProperty("substitutedIn") boolean substitutedIn,
            @JsonProperty("substitutedOut") boolean substitutedOut) {
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.playerName = (playerName != null && !playerName.isBlank()) ? playerName : "Unknown";
        this.teamId = teamId;
        this.position = position;
        this.rating = validateRating(rating);
        this.goals = goals;
        this.assists = assists;
        this.keyPasses = keyPasses;
        this.shots = shots;
        this.yellowCards = yellowCards;
        this.redCards = redCards;
        this.injuries = injuries;
        this.fouls = fouls;
        this.substitutedIn = substitutedIn;
        this.substitutedOut = substitutedOut;
    }

    private static double validateRating(double rating) {
        if (rating < 1.0 || rating > 10.0) {
            throw new IllegalArgumentException("rating must be between 1.0 and 10.0, got " + rating);
        }
        return rating;
    }

    @JsonProperty("playerId") public String playerId() { return playerId; }
    @JsonProperty("playerName") public String playerName() { return playerName; }
    @JsonProperty("teamId") public String teamId() { return teamId; }
    @JsonProperty("position") public String position() { return position; }
    @JsonProperty("rating") public double rating() { return rating; }
    @JsonProperty("goals") public int goals() { return goals; }
    @JsonProperty("assists") public int assists() { return assists; }
    @JsonProperty("keyPasses") public int keyPasses() { return keyPasses; }
    @JsonProperty("shots") public int shots() { return shots; }
    @JsonProperty("yellowCards") public int yellowCards() { return yellowCards; }
    @JsonProperty("redCards") public int redCards() { return redCards; }
    @JsonProperty("injuries") public int injuries() { return injuries; }
    @JsonProperty("fouls") public int fouls() { return fouls; }
    @JsonProperty("substitutedIn") public boolean substitutedIn() { return substitutedIn; }
    @JsonProperty("substitutedOut") public boolean substitutedOut() { return substitutedOut; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof V24PlayerMatchRatingDto that)) return false;
        return Double.compare(that.rating, rating) == 0
                && goals == that.goals
                && assists == that.assists
                && keyPasses == that.keyPasses
                && shots == that.shots
                && yellowCards == that.yellowCards
                && redCards == that.redCards
                && injuries == that.injuries
                && fouls == that.fouls
                && substitutedIn == that.substitutedIn
                && substitutedOut == that.substitutedOut
                && Objects.equals(playerId, that.playerId)
                && Objects.equals(playerName, that.playerName)
                && Objects.equals(teamId, that.teamId)
                && Objects.equals(position, that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, playerName, teamId, position, rating,
                goals, assists, keyPasses, shots, yellowCards, redCards,
                injuries, fouls, substitutedIn, substitutedOut);
    }

    @Override
    public String toString() {
        return "V24PlayerMatchRatingDto{player='%s', team=%s, rating=%.2f, G=%d, A=%d}"
                .formatted(playerName, teamId, rating, goals, assists);
    }
}