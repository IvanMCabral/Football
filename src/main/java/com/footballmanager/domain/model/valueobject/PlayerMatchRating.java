package com.footballmanager.domain.model.valueobject;

import java.io.Serializable;
import java.util.Objects;

/**
 * Observable per-player match rating for a live or persisted match snapshot.
 */
public record PlayerMatchRating(
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
    boolean substitutedOut
) implements Serializable {

    public PlayerMatchRating {
        Objects.requireNonNull(playerId, "playerId must not be null");
        playerName = (playerName != null && !playerName.isBlank()) ? playerName : "Unknown";
        if (rating < 1.0 || rating > 10.0) {
            throw new IllegalArgumentException("rating must be between 1.0 and 10.0, got " + rating);
        }
    }
}
