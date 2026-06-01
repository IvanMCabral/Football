package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * V24D4A: DTO for V24MatchEvent in storage/API layers.
 * Immutable snapshot — not tied to internal V24MatchEvent.
 */
public final class V24MatchEventDto {

    private final int minute;
    private final String type;
    private final String teamId;
    private final String playerId;
    private final String playerName;
    private final String relatedPlayerId;
    private final String relatedPlayerName;
    private final double xg;
    private final String description;
    private final V24ShotCoordinateDto shotCoordinate;

    @JsonCreator
    public V24MatchEventDto(
            @JsonProperty("minute") int minute,
            @JsonProperty("type") String type,
            @JsonProperty("teamId") String teamId,
            @JsonProperty("playerId") String playerId,
            @JsonProperty("playerName") String playerName,
            @JsonProperty("relatedPlayerId") String relatedPlayerId,
            @JsonProperty("relatedPlayerName") String relatedPlayerName,
            @JsonProperty("xg") double xg,
            @JsonProperty("description") String description,
            @JsonProperty("shotCoordinate") V24ShotCoordinateDto shotCoordinate) {
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

    public static V24MatchEventDto fromEvent(V24MatchEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        V24ShotCoordinateDto coordDto = null;
        if (event.shotCoordinate() != null) {
            coordDto = V24ShotCoordinateDto.fromCoordinate(event.shotCoordinate());
        }
        return new V24MatchEventDto(
                event.minute(),
                event.type().name(),
                event.teamId(),
                event.playerId(),
                event.playerName(),
                event.relatedPlayerId(),
                event.relatedPlayerName(),
                event.xg(),
                event.description(),
                coordDto
        );
    }

    public int minute() { return minute; }
    public String type() { return type; }
    public String teamId() { return teamId; }
    public String playerId() { return playerId; }
    public String playerName() { return playerName; }
    public String relatedPlayerId() { return relatedPlayerId; }
    public String relatedPlayerName() { return relatedPlayerName; }
    public double xg() { return xg; }
    public String description() { return description; }
    public V24ShotCoordinateDto shotCoordinate() { return shotCoordinate; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof V24MatchEventDto that)) return false;
        return minute == that.minute
                && Double.compare(that.xg, xg) == 0
                && Objects.equals(type, that.type)
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
        return Objects.hash(minute, type, teamId, playerId, playerName,
                relatedPlayerId, relatedPlayerName, xg, description, shotCoordinate);
    }

    @Override
    public String toString() {
        return "V24MatchEventDto{minute=%d, type=%s, player='%s', xg=%.3f}"
                .formatted(minute, type, playerName, xg);
    }
}