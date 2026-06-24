package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable input context for V24DetailedMatchEngine.
 * Built from CareerSave data externally.
 *
 * <p>LIVE-MATCH-F2-LIVE F2.5: deferred manual substitutions are tracked in
 * {@link #manualSubstitutions()}. The wire schedules a substitution by
 * appending a {@link ScheduledSub} (no immediate lineup mutation); the
 * engine applies the swap when it reaches the {@code effectiveMinute}
 * inside the per-minute loop. The list is kept sorted by
 * {@code (effectiveMinute ASC, teamId ASC, playerOffId ASC)} so iteration
 * during the engine loop is deterministic and supports an early-exit
 * optimization.
 */
public final class V24MatchContext {

    /**
     * LIVE-MATCH-F2-LIVE F2.5: a deferred manual substitution scheduled by
     * the manager through the live-match wire.
     *
     * <p>The engine applies the swap (move {@code playerOffId} to bench,
     * {@code playerOnId} to starting) at the start of the minute loop
     * iteration where {@code effectiveMinute == currentMinute}. The swap
     * uses the existing {@code V24SubstitutionEngine.manualSubstitute}
     * path so the per-team 5-sub cap, position compatibility, and
     * already-subbed checks are enforced.
     *
     * @param teamId          homeTeamId or awayTeamId (NOT NULL)
     * @param playerOffId     sessionPlayerId of the player going off (NOT NULL)
     * @param playerOnId      sessionPlayerId of the bench player coming on (NOT NULL)
     * @param effectiveMinute match minute when the swap is applied, in [0, 90]
     */
    public record ScheduledSub(
            String teamId,
            String playerOffId,
            String playerOnId,
            int effectiveMinute
    ) {}

    /**
     * Deterministic sort order for {@link #manualSubstitutions()}: by
     * effectiveMinute ASC, then teamId ASC, then playerOffId ASC. Used
     * by the constructor and by {@link #withManualSubstitution} so the
     * engine can early-exit the iteration.
     */
    private static final Comparator<ScheduledSub> SCHEDULED_SUB_ORDER =
            Comparator.comparingInt(ScheduledSub::effectiveMinute)
                    .thenComparing(ScheduledSub::teamId, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ScheduledSub::playerOffId, Comparator.nullsLast(Comparator.naturalOrder()));

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
    /**
     * LIVE-MATCH-F2-LIVE F2.5: scheduled (deferred) manual substitutions.
     * Internal storage is mutable for the constructor to build the sorted
     * copy; the public accessor returns an unmodifiable view.
     */
    private final List<ScheduledSub> manualSubstitutions;

    /**
     * LIVE-MATCH-F2-LIVE F2.5: primary constructor with the full set of
     * fields. The {@code manualSubstitutions} list is defensively copied
     * and sorted by {@link #SCHEDULED_SUB_ORDER} so iteration is
     * deterministic.
     */
    public V24MatchContext(
            @JsonProperty("matchId") String matchId,
            @JsonProperty("homeTeamId") String homeTeamId,
            @JsonProperty("awayTeamId") String awayTeamId,
            @JsonProperty("homeTeam") SessionTeam homeTeam,
            @JsonProperty("awayTeam") SessionTeam awayTeam,
            @JsonProperty("homeStartingPlayers") List<SessionPlayer> homeStartingPlayers,
            @JsonProperty("awayStartingPlayers") List<SessionPlayer> awayStartingPlayers,
            @JsonProperty("homeBenchPlayers") List<SessionPlayer> homeBenchPlayers,
            @JsonProperty("awayBenchPlayers") List<SessionPlayer> awayBenchPlayers,
            @JsonProperty("homeFormation") String homeFormation,
            @JsonProperty("awayFormation") String awayFormation,
            @JsonProperty("homeStyle") TeamStyle homeStyle,
            @JsonProperty("awayStyle") TeamStyle awayStyle,
            @JsonProperty("manualSubstitutions") List<ScheduledSub> manualSubstitutions) {
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
        this.manualSubstitutions = defensiveCopyAndSort(manualSubstitutions);

        validate();
    }

    /**
     * F1/F2/F5 compatibility constructor — defaults
     * {@link #manualSubstitutions} to an empty list. Used by every
     * existing call site (production wire + 32 test fixtures) that does
     * not need to schedule a deferred sub. Internally delegates to the
     * 14-arg primary constructor.
     */
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
        this(matchId, homeTeamId, awayTeamId, homeTeam, awayTeam,
                homeStartingPlayers, awayStartingPlayers,
                homeBenchPlayers, awayBenchPlayers,
                homeFormation, awayFormation, homeStyle, awayStyle,
                new ArrayList<>());
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

    /**
     * LIVE-MATCH-F2-LIVE F2.5: defensively copy the manualSubstitutions
     * list and sort it by the deterministic order. Returns an empty
     * unmodifiable list for null/empty input. The returned list is NOT
     * publicly exposed (the accessor wraps it in another
     * {@code unmodifiableList}).
     */
    private static List<ScheduledSub> defensiveCopyAndSort(List<ScheduledSub> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<ScheduledSub> copy = new ArrayList<>(list.size());
        for (ScheduledSub s : list) {
            if (s == null) {
                throw new IllegalArgumentException("manualSubstitutions must not contain null entries");
            }
            copy.add(s);
        }
        copy.sort(SCHEDULED_SUB_ORDER);
        return copy;
    }

    @JsonProperty("matchId") public String matchId() { return matchId; }
    @JsonProperty("homeTeamId") public String homeTeamId() { return homeTeamId; }
    @JsonProperty("awayTeamId") public String awayTeamId() { return awayTeamId; }
    @JsonProperty("homeTeam") public SessionTeam homeTeam() { return homeTeam; }
    @JsonProperty("awayTeam") public SessionTeam awayTeam() { return awayTeam; }
    @JsonProperty("homeStartingPlayers") public List<SessionPlayer> homeStartingPlayers() { return homeStartingPlayers; }
    @JsonProperty("awayStartingPlayers") public List<SessionPlayer> awayStartingPlayers() { return awayStartingPlayers; }
    @JsonProperty("homeBenchPlayers") public List<SessionPlayer> homeBenchPlayers() { return homeBenchPlayers; }
    @JsonProperty("awayBenchPlayers") public List<SessionPlayer> awayBenchPlayers() { return awayBenchPlayers; }
    @JsonProperty("homeFormation") public String homeFormation() { return homeFormation; }
    @JsonProperty("awayFormation") public String awayFormation() { return awayFormation; }
    @JsonProperty("homeStyle") public TeamStyle homeStyle() { return homeStyle; }
    @JsonProperty("awayStyle") public TeamStyle awayStyle() { return awayStyle; }

    /**
     * LIVE-MATCH-F2-LIVE F2.5: read-only view of the deferred manual
     * substitutions scheduled in this context. The list is sorted by
     * {@code (effectiveMinute ASC, teamId ASC, playerOffId ASC)}.
     *
     * <p>Returned as {@link Collections#unmodifiableList(java.util.List)};
     * mutating the returned list throws {@link UnsupportedOperationException}.
     */
    @JsonProperty("manualSubstitutions")
    public List<ScheduledSub> manualSubstitutions() {
        return Collections.unmodifiableList(manualSubstitutions);
    }

    // ========== LIVE-MATCH-F2-LIVE F5 (B4): tactical mutation helpers ==========

    /**
     * LIVE-MATCH-F2-LIVE F5 (B4): return a NEW {@link V24MatchContext} with
     * {@code teamId}'s tactical style replaced by {@code newStyle}. This
     * context is otherwise immutable (F1 design): the helper builds a fresh
     * instance rather than mutating in-place, so the replay path can compare
     * snapshots and the cache invalidation logic in F1 stays valid.
     *
     * <p>F2.5: the {@code manualSubstitutions} list is carried over to the
     * new context (it is shared by reference inside the unmodifiable view
     * — defensive copy in the constructor handles isolation).
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
                    newStyle, awayStyle,
                    manualSubstitutions);
        }
        if (awayTeamId.equals(teamId)) {
            return new V24MatchContext(
                    matchId, homeTeamId, awayTeamId,
                    homeTeam, awayTeam,
                    homeStartingPlayers, awayStartingPlayers,
                    homeBenchPlayers, awayBenchPlayers,
                    homeFormation, awayFormation,
                    homeStyle, newStyle,
                    manualSubstitutions);
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
     * <p>F2.5: the {@code manualSubstitutions} list is carried over to the
     * new context.
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
                    homeStyle, awayStyle,
                    manualSubstitutions);
        }
        if (awayTeamId.equals(teamId)) {
            return new V24MatchContext(
                    matchId, homeTeamId, awayTeamId,
                    homeTeam, awayTeam,
                    homeStartingPlayers, awayStartingPlayers,
                    homeBenchPlayers, awayBenchPlayers,
                    homeFormation, newFormation,
                    homeStyle, awayStyle,
                    manualSubstitutions);
        }
        throw new IllegalArgumentException(
                "teamId '" + teamId + "' does not match home ('"
                + homeTeamId + "') or away ('" + awayTeamId + "')");
    }

    // ========== LIVE-MATCH-F2-LIVE F2 (B1) / F2.5 (B1): deferred manual substitution helper ==========

    /**
     * LIVE-MATCH-F2-LIVE F2 (B1) + F2.5 (B1): return a NEW
     * {@link V24MatchContext} that records {@code playerOffId} → bench and
     * {@code playerOnId} → starting for {@code teamId} as a deferred
     * (scheduled) substitution. The swap is applied by the engine when
     * the minute loop reaches {@code minute}, NOT immediately in this
     * helper.
     *
     * <p><b>F2.5 contract change (vs F2):</b> the helper no longer
     * mutates the starting/bench lists synchronously. Instead it appends
     * a {@link ScheduledSub} to {@link #manualSubstitutions()}; the
     * engine reads that list at the start of each minute and calls
     * {@code V24SubstitutionEngine.manualSubstitute} for the entries
     * whose {@code effectiveMinute == currentMinute}. The starting and
     * bench lists returned by the new context are identical to the
     * input's (the lineup is "deferred" until the engine fires).
     *
     * <p>Validation (F2 rules, preserved):
     * <ul>
     *   <li>{@code teamId} must match {@code homeTeamId} or {@code awayTeamId}
     *       (else {@link IllegalArgumentException}).</li>
     *   <li>{@code playerOffId} must be in the starting lineup of
     *       {@code teamId} (else {@link IllegalArgumentException}
     *       "playerOffId not in starting XI").</li>
     *   <li>{@code playerOnId} must be in the bench of {@code teamId} (else
     *       {@link IllegalArgumentException} "playerOnId not on bench").</li>
     *   <li>{@code minute} must be in {@code [0, 90]} (else
     *       {@link IllegalArgumentException}).</li>
     *   <li>{@code playerOffId} must not equal {@code playerOnId} (else
     *       {@link IllegalArgumentException}).</li>
     * </ul>
     *
     * <p><b>F2.5 new validation:</b> a player may not have two
     * scheduled subs on the same team. If {@code manualSubstitutions}
     * already contains an entry with the same {@code teamId} and
     * {@code playerOffId}, throws {@link IllegalArgumentException}
     * "playerOffId '<id>' already has a scheduled substitution for
     * team '<teamId>'". O(n) with n ≤ 5.
     *
     * <p><b>Side effects:</b> NONE on the lineup. The helper returns a
     * new context carrying the appended {@link ScheduledSub} (the list
     * is re-sorted by {@link #SCHEDULED_SUB_ORDER}). The caller
     * (typically {@code SubstitutionCommandUseCaseImpl} via
     * {@code V24LiveSession.mutateContext}) is responsible for invoking
     * {@code replayFromMinute} to make the engine pick up the change.
     *
     * @param teamId        homeTeamId or awayTeamId of this match (NOT NULL, NOT BLANK)
     * @param playerOffId   sessionPlayerId of the player going off (NOT NULL, must be in starting)
     * @param playerOnId    sessionPlayerId of the player coming on (NOT NULL, must be in bench)
     * @param minute        the match minute the substitution is applied at (in [0, 90])
     * @return a new V24MatchContext with the scheduled sub appended; the
     *         starting/bench lists are identical to this context's
     * @throws IllegalArgumentException if any validation fails (including
     *         the F2.5 duplicate-scheduled-sub check)
     */
    public V24MatchContext withManualSubstitution(String teamId,
                                                  String playerOffId,
                                                  String playerOnId,
                                                  int minute) {
        // ---- Validation (F2 rules, preserved verbatim) ----
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

        // Validate playerOff is in starting XI (F2 rule). F2.5 still requires
        // this because the engine applies the swap via the same
        // V24SubstitutionEngine.manualSubstitute path, which would otherwise
        // throw IllegalStateException at apply time.
        boolean offInStarting = false;
        for (SessionPlayer p : currentStarting) {
            if (p != null && playerOffId.equals(p.getSessionPlayerId())) {
                offInStarting = true;
                break;
            }
        }
        if (!offInStarting) {
            throw new IllegalArgumentException(
                "playerOffId '" + playerOffId + "' not in starting XI of team '" + teamId + "'");
        }

        // Validate playerOn is on bench (F2 rule). Same rationale.
        boolean onInBench = false;
        for (SessionPlayer p : currentBench) {
            if (p != null && playerOnId.equals(p.getSessionPlayerId())) {
                onInBench = true;
                break;
            }
        }
        if (!onInBench) {
            throw new IllegalArgumentException(
                "playerOnId '" + playerOnId + "' not on bench of team '" + teamId + "'");
        }

        // F2.5 NEW: validate no duplicate scheduled sub for the same (teamId, playerOffId).
        // O(n) with n ≤ 5 per team — trivial.
        for (ScheduledSub existing : manualSubstitutions) {
            if (existing.teamId().equals(teamId)
                    && existing.playerOffId().equals(playerOffId)) {
                throw new IllegalArgumentException(
                    "playerOffId '" + playerOffId + "' already has a scheduled substitution for team '" + teamId + "'");
            }
        }

        // Build the new manualSubstitutions list with the appended entry.
        // The constructor will re-sort by SCHEDULED_SUB_ORDER.
        List<ScheduledSub> nextManualSubs = new ArrayList<>(manualSubstitutions.size() + 1);
        nextManualSubs.addAll(manualSubstitutions);
        nextManualSubs.add(new ScheduledSub(teamId, playerOffId, playerOnId, minute));

        // F2.5: return a new V24MatchContext with the SAME starting/bench
        // lists (no mutation). The constructor's defensive copy + sort
        // ensures the new list is unmodifiable and deterministically ordered.
        return new V24MatchContext(
                matchId, homeTeamId, awayTeamId,
                homeTeam, awayTeam,
                homeStartingPlayers, awayStartingPlayers,
                homeBenchPlayers, awayBenchPlayers,
                homeFormation, awayFormation,
                homeStyle, awayStyle,
                nextManualSubs);
    }
}
