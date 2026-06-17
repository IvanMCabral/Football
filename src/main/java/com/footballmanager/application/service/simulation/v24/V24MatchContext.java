package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable input context for V24DetailedMatchEngine.
 * Built from CareerSave data externally.
 */
public final class V24MatchContext {

    private final String matchId;
    private final String homeTeamId;
    private final String awayTeamId;
    private final SessionTeam homeTeam;
    private final SessionTeam awayTeam;
    private final List<SessionPlayer> homeStartingPlayers;
    private final List<SessionPlayer> awayStartingPlayers;
    private final List<SessionPlayer> homeBenchPlayers;
    private final List<SessionPlayer> awayBenchPlayers;
    private final String homeFormation;
    private final String awayFormation;
    private final TeamStyle homeStyle;
    private final TeamStyle awayStyle;

    public V24MatchContext(
            String matchId,
            String homeTeamId,
            String awayTeamId,
            SessionTeam homeTeam,
            SessionTeam awayTeam,
            List<SessionPlayer> homeStartingPlayers,
            List<SessionPlayer> awayStartingPlayers,
            List<SessionPlayer> homeBenchPlayers,
            List<SessionPlayer> awayBenchPlayers,
            String homeFormation,
            String awayFormation,
            TeamStyle homeStyle,
            TeamStyle awayStyle) {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        this.matchId = matchId;
        this.homeTeamId = Objects.requireNonNull(homeTeamId, "homeTeamId must not be null");
        this.awayTeamId = Objects.requireNonNull(awayTeamId, "awayTeamId must not be null");
        this.homeTeam = Objects.requireNonNull(homeTeam, "homeTeam must not be null");
        this.awayTeam = Objects.requireNonNull(awayTeam, "awayTeam must not be null");
        this.homeStartingPlayers = defensiveCopy(homeStartingPlayers);
        this.awayStartingPlayers = defensiveCopy(awayStartingPlayers);
        this.homeBenchPlayers = defensiveCopy(homeBenchPlayers);
        this.awayBenchPlayers = defensiveCopy(awayBenchPlayers);
        this.homeFormation = homeFormation;
        this.awayFormation = awayFormation;
        this.homeStyle = (homeStyle != null) ? homeStyle : TeamStyle.BALANCED;
        this.awayStyle = (awayStyle != null) ? awayStyle : TeamStyle.BALANCED;

        validate();
    }

    private void validate() {
        validateStarterCount(homeStartingPlayers, "homeStartingPlayers");
        validateStarterCount(awayStartingPlayers, "awayStartingPlayers");
    }

    /**
     * V24D6U2: Short-handed lineups are now permitted. The engine accepts
     * any starting-XI size in {@code [MIN, 11]} inclusive. Below MIN the
     * team cannot field a match.
     */
    private static void validateStarterCount(List<SessionPlayer> starters, String label) {
        int size = starters.size();
        int min = com.footballmanager.application.service.lineup.LineupRules.MIN_AVAILABLE_PLAYERS;
        if (size < min || size > com.footballmanager.application.service.lineup.LineupRules.MAX_LINEUP_PLAYERS) {
            throw new IllegalArgumentException(
                    label + " must contain between " + min + " and 11 players, got " + size);
        }
    }

    private static List<SessionPlayer> defensiveCopy(List<SessionPlayer> list) {
        if (list == null) return Collections.emptyList();
        return Collections.unmodifiableList(new java.util.ArrayList<>(list));
    }

    public String matchId() { return matchId; }
    public String homeTeamId() { return homeTeamId; }
    public String awayTeamId() { return awayTeamId; }
    public SessionTeam homeTeam() { return homeTeam; }
    public SessionTeam awayTeam() { return awayTeam; }
    public List<SessionPlayer> homeStartingPlayers() { return homeStartingPlayers; }
    public List<SessionPlayer> awayStartingPlayers() { return awayStartingPlayers; }
    public List<SessionPlayer> homeBenchPlayers() { return homeBenchPlayers; }
    public List<SessionPlayer> awayBenchPlayers() { return awayBenchPlayers; }
    public String homeFormation() { return homeFormation; }
    public String awayFormation() { return awayFormation; }
    public TeamStyle homeStyle() { return homeStyle; }
    public TeamStyle awayStyle() { return awayStyle; }

    // ========== LIVE-MATCH-F2-LIVE F5 (B4): tactical mutation helpers ==========

    /**
     * LIVE-MATCH-F2-LIVE F5 (B4): return a NEW {@link V24MatchContext} with
     * {@code teamId}'s tactical style replaced by {@code newStyle}. This
     * context is otherwise immutable (F1 design): the helper builds a fresh
     * instance rather than mutating in-place, so the replay path can compare
     * snapshots and the cache invalidation logic in F1 stays valid.
     *
     * <p>Validation: {@code newStyle} must be non-null, {@code teamId} must
     * match the home or away team of this context. The F5 spec restricts
     * tactical changes to the manager's team (home), but the helper is
     * generic — the policy lives in {@code TacticalChangeService}.
     *
     * @param teamId   homeTeamId or awayTeamId of this match
     * @param newStyle the new tactical style (NOT NULL)
     * @return a new V24MatchContext with the style replaced
     * @throws IllegalArgumentException if teamId is unknown or newStyle is null
     */
    public V24MatchContext withNewStyle(String teamId, TeamStyle newStyle) {
        if (newStyle == null) {
            throw new IllegalArgumentException("newStyle must not be null");
        }
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId must not be blank");
        }
        if (homeTeamId.equals(teamId)) {
            return new V24MatchContext(
                    matchId, homeTeamId, awayTeamId,
                    homeTeam, awayTeam,
                    homeStartingPlayers, awayStartingPlayers,
                    homeBenchPlayers, awayBenchPlayers,
                    homeFormation, awayFormation,
                    newStyle, awayStyle);
        }
        if (awayTeamId.equals(teamId)) {
            return new V24MatchContext(
                    matchId, homeTeamId, awayTeamId,
                    homeTeam, awayTeam,
                    homeStartingPlayers, awayStartingPlayers,
                    homeBenchPlayers, awayBenchPlayers,
                    homeFormation, awayFormation,
                    homeStyle, newStyle);
        }
        throw new IllegalArgumentException(
                "teamId '" + teamId + "' does not match home ('"
                + homeTeamId + "') or away ('" + awayTeamId + "')");
    }

    /**
     * LIVE-MATCH-F2-LIVE F5 (B4): return a NEW {@link V24MatchContext} with
     * {@code teamId}'s formation string replaced by {@code newFormation}.
     * Like {@link #withNewStyle}, this returns a fresh instance.
     *
     * <p>Validation is delegated to {@link V24TeamMatchState#setFormation(String)}
     * (which the tactical-change service invokes after {@code mutateContext}
     * rebuilds the {@code V24TeamMatchState}). The helper itself only checks
     * the identity constraint (teamId must match home/away) and non-blank.
     *
     * @param teamId        homeTeamId or awayTeamId of this match
     * @param newFormation  the new formation code (NOT NULL, NOT BLANK)
     * @return a new V24MatchContext with the formation replaced
     * @throws IllegalArgumentException if teamId is unknown or formation is null/blank
     */
    public V24MatchContext withNewFormation(String teamId, String newFormation) {
        if (newFormation == null || newFormation.isBlank()) {
            throw new IllegalArgumentException("newFormation must not be null or blank");
        }
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId must not be blank");
        }
        if (homeTeamId.equals(teamId)) {
            return new V24MatchContext(
                    matchId, homeTeamId, awayTeamId,
                    homeTeam, awayTeam,
                    homeStartingPlayers, awayStartingPlayers,
                    homeBenchPlayers, awayBenchPlayers,
                    newFormation, awayFormation,
                    homeStyle, awayStyle);
        }
        if (awayTeamId.equals(teamId)) {
            return new V24MatchContext(
                    matchId, homeTeamId, awayTeamId,
                    homeTeam, awayTeam,
                    homeStartingPlayers, awayStartingPlayers,
                    homeBenchPlayers, awayBenchPlayers,
                    homeFormation, newFormation,
                    homeStyle, awayStyle);
        }
        throw new IllegalArgumentException(
                "teamId '" + teamId + "' does not match home ('"
                + homeTeamId + "') or away ('" + awayTeamId + "')");
    }
}