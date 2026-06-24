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
 *
 * <p>LIVE-MATCH-F3-UI-LIVE BE2: added optional {@code playerOnName} field
 * for SUBSTITUTION events so the F3 UI can render "Salió X, entró Y" in the
 * timeline without resolving sessionPlayerId to a name. The existing
 * {@code playerName} field carries the OFF player (consistent with
 * {@code V24MatchEvent.playerName()}); {@code playerOnName} carries the ON
 * player (from {@code V24MatchEvent.relatedPlayerName()}). For non-SUBSTITUTION
 * events {@code playerOnName} is {@code null}.
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
    // LIVE-MATCH-F3-UI-LIVE BE2: optional ON-player name for SUBSTITUTION events
    private final String playerOnName;

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
        CARD,
        /**
         * LIVE-MATCH-F2-LIVE F5: manager-initiated style/formation change.
         * Mapped 1:1 from {@code V24MatchEventType.TACTICAL_CHANGE}.
         */
        TACTICAL_CHANGE
    }

    /**
     * Backward-compatible constructor without playerId/teamId/matchId.
     */
    private MatchEvent(EventType eventType, int minute, String playerName, String description) {
        this(eventType, minute, playerName, description, null, null, null, null);
    }

    /**
     * Backward-compatible constructor with playerId/teamId but no matchId.
     */
    private MatchEvent(EventType eventType, int minute, String playerName, String description,
                      String playerId, String teamId) {
        this(eventType, minute, playerName, description, playerId, teamId, null, null);
    }

    /**
     * Full constructor with playerId, teamId, and matchId for V24D6M10 player attribution.
     */
    private MatchEvent(EventType eventType, int minute, String playerName, String description,
                      String playerId, String teamId, String matchId) {
        this(eventType, minute, playerName, description, playerId, teamId, matchId, null);
    }

    /**
     * LIVE-MATCH-F3-UI-LIVE BE2: full constructor including the optional
     * {@code playerOnName} for SUBSTITUTION events.
     */
    private MatchEvent(EventType eventType, int minute, String playerName, String description,
                      String playerId, String teamId, String matchId, String playerOnName) {
        this.eventType = Objects.requireNonNull(eventType, "Event type cannot be null");
        this.playerName = (playerName != null && !playerName.isBlank()) ? playerName : "Unknown";
        this.description = (description != null) ? description : "";
        this.playerId = playerId;
        this.teamId = teamId;
        this.matchId = matchId;
        this.playerOnName = (playerOnName != null && !playerOnName.isBlank()) ? playerOnName : null;

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
        return new MatchEvent(eventType, minute, playerName, description, playerId, teamId, null, null);
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
        return new MatchEvent(eventType, minute, playerName, description, playerId, teamId, matchId, null);
    }

    /**
     * LIVE-MATCH-F3-UI-LIVE BE2: factory method that also carries the optional
     * {@code playerOnName} for SUBSTITUTION events. The value is a no-op for
     * non-SUBSTITUTION events (it's still stored, but the UI only displays it
     * for SUBSTITUTION).
     *
     * @param eventType    type of event
     * @param minute       match minute
     * @param playerId     sessionPlayerId of the OFF player (may be null)
     * @param playerName   OFF player name (existing field, primary attribution)
     * @param teamId       sessionTeamId of the player's team (may be null)
     * @param description  event description
     * @param matchId      matchId for deterministic player selection (may be null)
     * @param playerOnName ON player name for SUBSTITUTION events (may be null)
     */
    public static MatchEvent of(EventType eventType, int minute, String playerId, String playerName,
                               String teamId, String description, String matchId, String playerOnName) {
        return new MatchEvent(eventType, minute, playerName, description, playerId, teamId, matchId, playerOnName);
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

    /**
     * LIVE-MATCH-F3-UI-LIVE BE2: ON player name for SUBSTITUTION events.
     * Returns {@code null} for non-SUBSTITUTION events or when the
     * V24MatchEvent did not carry a {@code relatedPlayerName}.
     */
    public String getPlayerOnName() {
        return playerOnName;
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
                Objects.equals(matchId, that.matchId) &&
                Objects.equals(playerOnName, that.playerOnName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, minute, playerName, description, playerId, teamId, matchId, playerOnName);
    }

    @Override
    public String toString() {
        return String.format("%d' [%s] %s: %s", minute, eventType, playerName, description);
    }
}