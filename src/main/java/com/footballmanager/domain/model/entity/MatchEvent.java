package com.footballmanager.domain.model.entity;

import java.util.Objects;

/**
 * MatchEvent - Evento occurred during a live SSE match.
 *
 * <p>Immutable snapshot. Includes optional playerId/teamId for event attribution
 *
 * for lossless event type preservation. No lossy mappings (no SHOT→GOAL,
 * no SHOT→CARD, no events dropped).
 *
 * for SUBSTITUTION events so the F3 UI can render "Salió X, entró Y" in the
 * timeline without resolving sessionPlayerId to a name. The existing
 * {@code playerName} field carries the OFF player (consistent with
 * {@code V24MatchEvent.playerName()}); {@code playerOnName} carries the ON
 * player (from {@code V24MatchEvent.relatedPlayerName()}). The matching
 * {@code relatedPlayerId} is also preserved so the UI can reconstruct live
 * lineups after a page reload without relying on player-name matching.
 * For non-SUBSTITUTION events {@code playerOnName} is {@code null}.
 */
public class MatchEvent {

    private final EventType eventType;
    private final int minute;
    private final String playerName;
    private final String description;
    private final String playerId;
    private final String teamId;
    private final String matchId;
    // V25: Optional secondary player id (SUBSTITUTION on-player, assist provider, etc.)
    private final String relatedPlayerId;
    private final String relatedPlayerName;
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
    private MatchEvent(EventType eventType, int minute, String playerName, String description,
                      String playerId, String teamId, String matchId) {
        this(eventType, minute, playerName, description, playerId, teamId, matchId, null);
    }

    /**
     * {@code playerOnName} for SUBSTITUTION events.
     */
    private MatchEvent(EventType eventType, int minute, String playerName, String description,
                      String playerId, String teamId, String matchId, String playerOnName) {
        this(eventType, minute, playerName, description, playerId, teamId, matchId,
                null, null, playerOnName);
    }

    /**
     * Full constructor including secondary-player attribution from V24 events.
     */
    private MatchEvent(EventType eventType, int minute, String playerName, String description,
                      String playerId, String teamId, String matchId,
                      String relatedPlayerId, String relatedPlayerName, String playerOnName) {
        this.eventType = Objects.requireNonNull(eventType, "Event type cannot be null");
        this.playerName = (playerName != null && !playerName.isBlank()) ? playerName : "Unknown";
        this.description = (description != null) ? description : "";
        this.playerId = playerId;
        this.teamId = teamId;
        this.matchId = matchId;
        this.relatedPlayerId = (relatedPlayerId != null && !relatedPlayerId.isBlank()) ? relatedPlayerId : null;
        this.relatedPlayerName = (relatedPlayerName != null && !relatedPlayerName.isBlank()) ? relatedPlayerName : null;
        String resolvedPlayerOnName = playerOnName;
        if ((resolvedPlayerOnName == null || resolvedPlayerOnName.isBlank())
                && eventType == EventType.SUBSTITUTION) {
            resolvedPlayerOnName = relatedPlayerName;
        }
        this.playerOnName = (resolvedPlayerOnName != null && !resolvedPlayerOnName.isBlank())
                ? resolvedPlayerOnName : null;

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

    /**
     * Factory preserving both primary and secondary V24 player attribution.
     *
     * <p>For SUBSTITUTION events, {@code playerId/playerName} is the player
     * leaving the pitch and {@code relatedPlayerId/relatedPlayerName} is the
     * player entering. Keeping the ID is critical for reload-safe live modal
     * reconstruction.
     */
    public static MatchEvent of(EventType eventType, int minute, String playerId, String playerName,
                               String teamId, String description, String matchId,
                               String relatedPlayerId, String relatedPlayerName, String playerOnName) {
        return new MatchEvent(eventType, minute, playerName, description, playerId, teamId, matchId,
                relatedPlayerId, relatedPlayerName, playerOnName);
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
    public String getPlayerId() {
        return playerId;
    }
    public String getTeamId() {
        return teamId;
    }
    public String getMatchId() {
        return matchId;
    }

    /**
     * Secondary player's sessionPlayerId, if available.
     *
     * <p>For SUBSTITUTION this is the player entering the match. For goals or
     * shots it may represent the assist/key-pass player.
     */
    public String getRelatedPlayerId() {
        return relatedPlayerId;
    }

    /**
     * Secondary player's display name, if available.
     */
    public String getRelatedPlayerName() {
        return relatedPlayerName;
    }

    /**
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
                Objects.equals(relatedPlayerId, that.relatedPlayerId) &&
                Objects.equals(relatedPlayerName, that.relatedPlayerName) &&
                Objects.equals(playerOnName, that.playerOnName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, minute, playerName, description, playerId, teamId, matchId,
                relatedPlayerId, relatedPlayerName, playerOnName);
    }

    @Override
    public String toString() {
        return String.format("%d' [%s] %s: %s", minute, eventType, playerName, description);
    }
}
