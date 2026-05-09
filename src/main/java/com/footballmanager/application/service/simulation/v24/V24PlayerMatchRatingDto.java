package com.footballmanager.application.service.simulation.v24;

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

    public V24PlayerMatchRatingDto(
            String playerId,
            String playerName,
            String teamId,
            String position,
            double rating,
            int goals,
            int assists,
            int keyPasses,
            int shots,
            int yellowCards,
            int redCards,
            int injuries,
            int fouls,
            boolean substitutedIn,
            boolean substitutedOut) {
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

    public String playerId() { return playerId; }
    public String playerName() { return playerName; }
    public String teamId() { return teamId; }
    public String position() { return position; }
    public double rating() { return rating; }
    public int goals() { return goals; }
    public int assists() { return assists; }
    public int keyPasses() { return keyPasses; }
    public int shots() { return shots; }
    public int yellowCards() { return yellowCards; }
    public int redCards() { return redCards; }
    public int injuries() { return injuries; }
    public int fouls() { return fouls; }
    public boolean substitutedIn() { return substitutedIn; }
    public boolean substitutedOut() { return substitutedOut; }

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