package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;

import java.util.ArrayList;
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

    // ========== LIVE-MATCH-F2-LIVE F2 (B1): manual substitution helper ==========

    /**
     * LIVE-MATCH-F2-LIVE F2 (B1): return a NEW {@link V24MatchContext} with
     * {@code playerOffId} moved out of the starting lineup into the bench and
     * {@code playerOnId} moved from the bench into the starting lineup for
     * {@code teamId}. This is the canonical "wire" of a manager-applied
     * substitution through the F1 replay path: after {@code mutateContext}
     * calls this helper, the next {@code engine.simulate(...)} rebuild will
     * pick up the new lineup (the on player is now in starting, the off
     * player is now in bench) and the match result from {@code minute}
     * onward will reflect the substitution.
     *
     * <p>Unlike {@link #withNewStyle} and {@link #withNewFormation} (which
     * replace a single field), this helper swaps two players between the
     * starting and bench lists of the target team. The helper does NOT
     * mutate the input lists — it returns a fresh context with new
     * {@code ArrayList} instances for the two affected lists. The other
     * lists (and the other team's lists) are passed by reference (the
     * constructor's defensive copy handles isolation).
     *
     * <p>Validation (defense in depth with the engine's
     * {@code V24SubstitutionEngine.manualSubstitute} which validates the
     * same things against the {@code V24TeamMatchState}):
     * <ul>
     *   <li>{@code teamId} must match {@code homeTeamId} or {@code awayTeamId}
     *       (else {@link IllegalArgumentException}).</li>
     *   <li>{@code playerOffId} must be in the starting lineup of
     *       {@code teamId} (else {@link IllegalArgumentException}
     *       "playerOffId not in starting XI").</li>
     *   <li>{@code playerOnId} must be in the bench of {@code teamId} (else
     *       {@link IllegalArgumentException} "playerOnId not on bench").</li>
     *   <li>{@code minute} must be in {@code [0, 90]} (else
     *       {@link IllegalArgumentException}). The minute is NOT used by this
     *       helper itself — it is a forward-compatibility field for future
     *       validators (e.g. "no subs before kickoff") and is validated
     *       upstream by the {@code V24SubstitutionEngine}.</li>
     *   <li>{@code playerOffId} must not equal {@code playerOnId} (else
     *       {@link IllegalArgumentException}).</li>
     * </ul>
     *
     * <p>Side effects: NONE. The helper only returns a new context. The
     * caller (typically {@code SubstitutionCommandUseCaseImpl} via
     * {@code V24LiveSession.mutateContext}) is responsible for invoking
     * {@code replayFromMinute} to make the engine pick up the change.
     *
     * @param teamId        homeTeamId or awayTeamId of this match (NOT NULL, NOT BLANK)
     * @param playerOffId   sessionPlayerId of the player going off (NOT NULL, must be in starting)
     * @param playerOnId    sessionPlayerId of the player coming on (NOT NULL, must be in bench)
     * @param minute        the match minute the substitution is applied at
     *                      (NOT validated strictly by the engine; this helper
     *                      enforces {@code [0, 90]} for safety)
     * @return a new V24MatchContext with playerOff moved to bench and playerOn
     *         moved to starting for the given teamId
     * @throws IllegalArgumentException if any validation fails
     */
    public V24MatchContext withManualSubstitution(String teamId,
                                                  String playerOffId,
                                                  String playerOnId,
                                                  int minute) {
        // ---- Validation ----
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId must not be blank");
        }
        if (playerOffId == null || playerOffId.isBlank()) {
            throw new IllegalArgumentException("playerOffId must not be blank");
        }
        if (playerOnId == null || playerOnId.isBlank()) {
            throw new IllegalArgumentException("playerOnId must not be blank");
        }
        if (playerOffId.equals(playerOnId)) {
            throw new IllegalArgumentException(
                "playerOffId and playerOnId must be different (got '" + playerOffId + "')");
        }
        if (minute < 0 || minute > 90) {
            throw new IllegalArgumentException(
                "minute must be in [0, 90], got " + minute);
        }

        boolean isHome = homeTeamId.equals(teamId);
        boolean isAway = awayTeamId.equals(teamId);
        if (!isHome && !isAway) {
            throw new IllegalArgumentException(
                "teamId '" + teamId + "' does not match home ('"
                + homeTeamId + "') or away ('" + awayTeamId + "')");
        }

        List<SessionPlayer> currentStarting = isHome ? homeStartingPlayers : awayStartingPlayers;
        List<SessionPlayer> currentBench = isHome ? homeBenchPlayers : awayBenchPlayers;

        // Find playerOff in starting (must exist).
        SessionPlayer offPlayer = null;
        List<SessionPlayer> newStarting = new ArrayList<>(currentStarting.size());
        for (SessionPlayer p : currentStarting) {
            if (offPlayer == null && p != null && playerOffId.equals(p.getSessionPlayerId())) {
                offPlayer = p;
                // skip — playerOff moves to bench
                continue;
            }
            newStarting.add(p);
        }
        if (offPlayer == null) {
            throw new IllegalArgumentException(
                "playerOffId '" + playerOffId + "' not in starting XI of team '" + teamId + "'");
        }

        // Find playerOn in bench (must exist and not already be in the new starting list).
        SessionPlayer onPlayer = null;
        List<SessionPlayer> newBench = new ArrayList<>(currentBench.size());
        for (SessionPlayer p : currentBench) {
            if (onPlayer == null && p != null && playerOnId.equals(p.getSessionPlayerId())) {
                onPlayer = p;
                // skip — playerOn moves to starting
                continue;
            }
            newBench.add(p);
        }
        if (onPlayer == null) {
            throw new IllegalArgumentException(
                "playerOnId '" + playerOnId + "' not on bench of team '" + teamId + "'");
        }

        // Append the swapped players to their new lists. The on player goes
        // to the END of starting (preserving the relative order of the
        // remaining starters); the off player goes to the END of bench.
        newStarting.add(onPlayer);
        newBench.add(offPlayer);

        // Return a new V24MatchContext with the swapped lists.
        if (isHome) {
            return new V24MatchContext(
                    matchId, homeTeamId, awayTeamId,
                    homeTeam, awayTeam,
                    newStarting, awayStartingPlayers,
                    newBench, awayBenchPlayers,
                    homeFormation, awayFormation,
                    homeStyle, awayStyle);
        } else {
            return new V24MatchContext(
                    matchId, homeTeamId, awayTeamId,
                    homeTeam, awayTeam,
                    homeStartingPlayers, newStarting,
                    homeBenchPlayers, newBench,
                    homeFormation, awayFormation,
                    homeStyle, awayStyle);
        }
    }
}