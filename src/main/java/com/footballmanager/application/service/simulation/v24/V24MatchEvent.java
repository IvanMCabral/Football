package com.footballmanager.application.service.simulation.v24;

import java.util.Objects;

/**
 * Immutable single event in the V24 match timeline.
 * playerId and playerName are real values from SessionPlayer.
 * shotCoordinate is nullable — only SHOT/GOAL events carry coordinates (V24D3C).
 */
public final class V24MatchEvent {

    private final int minute;
    private final V24MatchEventType type;
    private final String teamId;
    private final String playerId;
    private final String playerName;
    private final String relatedPlayerId;
    private final String relatedPlayerName;
    private final double xg;
    private final String description;
    private final V24ShotCoordinate shotCoordinate;

    public V24MatchEvent(
            int minute,
            V24MatchEventType type,
            String teamId,
            String playerId,
            String playerName,
            String relatedPlayerId,
            String relatedPlayerName,
            double xg,
            String description) {
        this(minute, type, teamId, playerId, playerName, relatedPlayerId, relatedPlayerName, xg, description, null);
    }

    public V24MatchEvent(
            int minute,
            V24MatchEventType type,
            String teamId,
            String playerId,
            String playerName,
            String relatedPlayerId,
            String relatedPlayerName,
            double xg,
            String description,
            V24ShotCoordinate shotCoordinate) {
        if (minute < 1 || minute > 130) {
            throw new IllegalArgumentException("minute must be between 1 and 130, got " + minute);
        }
        this.minute = minute;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.teamId = teamId;
        this.playerId = playerId;
        this.playerName = (playerName != null && !playerName.isBlank()) ? playerName : "Unknown";
        this.relatedPlayerId = relatedPlayerId;
        this.relatedPlayerName = (relatedPlayerName != null && !relatedPlayerName.isBlank()) ? relatedPlayerName : null;
        if (!Double.isFinite(xg) || xg < 0) {
            throw new IllegalArgumentException("xg must be >= 0 and finite, got " + xg);
        }
        this.xg = xg;
        this.description = (description != null) ? description : "";
        this.shotCoordinate = shotCoordinate;
    }

    /**
     * Returns a new V24MatchEvent with the given shotCoordinate attached.
     * Used by V24DetailedMatchEngine to attach coordinates to SHOT/GOAL events.
     */
    public V24MatchEvent withShotCoordinate(V24ShotCoordinate coord) {
        return new V24MatchEvent(
                minute, type, teamId, playerId, playerName,
                relatedPlayerId, relatedPlayerName, xg, description, coord);
    }

    public int minute() { return minute; }
    public V24MatchEventType type() { return type; }
    public String teamId() { return teamId; }
    public String playerId() { return playerId; }
    public String playerName() { return playerName; }
    public String relatedPlayerId() { return relatedPlayerId; }
    public String relatedPlayerName() { return relatedPlayerName; }
    public double xg() { return xg; }
    public String description() { return description; }
    public V24ShotCoordinate shotCoordinate() { return shotCoordinate; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof V24MatchEvent that)) return false;
        return minute == that.minute
                && type == that.type
                && Double.compare(that.xg, xg) == 0
                && Objects.equals(teamId, that.teamId)
                && Objects.equals(playerId, that.playerId)
                && Objects.equals(playerName, that.playerName)
                && Objects.equals(relatedPlayerId, that.relatedPlayerId)
                && Objects.equals(relatedPlayerName, that.relatedPlayerName)
                && Objects.equals(description, that.description)
                && Objects.equals(shotCoordinate, that.shotCoordinate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minute, type, teamId, playerId, playerName, relatedPlayerId, relatedPlayerName, xg, description, shotCoordinate);
    }

    @Override
    public String toString() {
        return "V24MatchEvent{minute=%d, type=%s, player='%s', xg=%.3f, desc='%s', coord=%s}"
                .formatted(minute, type, playerName, xg, description, shotCoordinate);
    }
}