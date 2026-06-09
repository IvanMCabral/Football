package com.footballmanager.domain.model.entity;

import java.util.Objects;

/**
 * MatchEvent - Evento occurred during a live SSE match.
 *
 * <p>Immutable snapshot. Includes optional playerId/teamId for event attribution
 * when real player data is available (V24D6M10).
 *
 * <p>V24D6M11: EventType enum extended to cover every V24MatchEventType
 * for lossless event type preservation. No lossy mappings (no SHOT→GOAL,
 * no SHOT→CARD, no events dropped).
 */
public class MatchEvent {

    private final EventType eventType;
    private final int minute;
    private final String playerName;
    private final String description;
    // V24D6M10: Optional player/team attribution
    private final String playerId;
    private final String teamId;
    // V24D6M10: Optional matchId for deterministic player selection
    private final String matchId;

    public enum EventType {
        GOAL,
        SHOT,
        SHOT_ON_TARGET,
        SAVE,
        MISS,
        BLOCK,
        CHANCE_CREATED,
        FOUL,
        YELLOW_CARD,
        RED_CARD,
        INJURY,
        CORNER,
        OFFSIDE,
        SUBSTITUTION,
        /**
         * Legacy domain type for backward compatibility with MatchEventGenerator
         * and MatchEngineImpl. Not mapped from V24MatchEventType (V24 uses YELLOW_CARD/RED_CARD).
         * Prefer YELLOW_CARD or RED_CARD in new code.
         */
        CARD
    }

    /**
     * Backward-compatible constructor without playerId/teamId/matchId.
     */
    private MatchEvent(EventType eventType, int minute, String playerName, String description) {
        this(eventType, minute, playerName, description, null, null, null);
    }

    /**
     * Backward-compatible constructor with playerId/teamId but no matchId.
     */
    private MatchEvent(EventType eventType, int minute, String playerName, String description,
                      String playerId, String teamId) {
        this(eventType, minute, playerName, description, playerId, teamId, null);
    }

    /**
     * Full constructor with playerId, teamId, and matchId for V24D6M10 player attribution.
     */
    private MatchEvent(EventType eventType, int minute, String playerName, String description,
                      String playerId, String teamId, String matchId) {
        this.eventType = Objects.requireNonNull(eventType, "Event type cannot be null");
        this.playerName = (playerName != null && !playerName.isBlank()) ? playerName : "Unknown";
        this.description = (description != null) ? description : "";
        this.playerId = playerId;
        this.teamId = teamId;
        this.matchId = matchId;

        validateMinute(minute);
        this.minute = minute;
    }

    /**
     * Backward-compatible factory method for events without player attribution.
     */
    public static MatchEvent of(EventType eventType, int minute, String playerName, String description) {
        return new MatchEvent(eventType, minute, playerName, description, null, null);
    }

    /**
     * V24D6M10: Factory method for events with player attribution.
     *
     * @param eventType  type of event
     * @param minute    match minute
     * @param playerId  sessionPlayerId of the player (may be null for backward compatibility)
     * @param playerName player name (used as fallback when playerId is null)
     * @param teamId    sessionTeamId of the player's team (may be null)
     * @param description event description
     */
    public static MatchEvent of(EventType eventType, int minute, String playerId, String playerName,
                               String teamId, String description) {
        return new MatchEvent(eventType, minute, playerName, description, playerId, teamId, null);
    }

    /**
     * V24D6M10: Factory method for events with player attribution AND matchId.
     *
     * @param eventType  type of event
     * @param minute    match minute
     * @param playerId  sessionPlayerId of the player (may be null for backward compatibility)
     * @param playerName player name (used as fallback when playerId is null)
     * @param teamId    sessionTeamId of the player's team (may be null)
     * @param description event description
     * @param matchId   matchId for deterministic player selection (may be null)
     */
    public static MatchEvent of(EventType eventType, int minute, String playerId, String playerName,
                               String teamId, String description, String matchId) {
        return new MatchEvent(eventType, minute, playerName, description, playerId, teamId, matchId);
    }

    private void validateMinute(int minute) {
        if (minute < 0 || minute > 120) {
            throw new IllegalArgumentException("Match minute must be between 0 and 120");
        }
    }

    public EventType getEventType() {
        return eventType;
    }

    public int getMinute() {
        return minute;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * V24D6M10: Player's sessionPlayerId, if available.
     */
    public String getPlayerId() {
        return playerId;
    }

    /**
     * V24D6M10: Player's team sessionTeamId, if available.
     */
    public String getTeamId() {
        return teamId;
    }

    /**
     * V24D6M10: MatchId for deterministic player selection, if available.
     */
    public String getMatchId() {
        return matchId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchEvent that = (MatchEvent) o;
        return minute == that.minute &&
                eventType == that.eventType &&
                Objects.equals(playerName, that.playerName) &&
                Objects.equals(description, that.description) &&
                Objects.equals(playerId, that.playerId) &&
                Objects.equals(teamId, that.teamId) &&
                Objects.equals(matchId, that.matchId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, minute, playerName, description, playerId, teamId, matchId);
    }

    @Override
    public String toString() {
        return String.format("%d' [%s] %s: %s", minute, eventType, playerName, description);
    }
}