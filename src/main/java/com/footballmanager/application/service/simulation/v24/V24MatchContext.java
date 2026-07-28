package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class V24MatchContext {

    public record ScheduledSub(
            String teamId,
            String playerOffId,
            String playerOnId,
            int effectiveMinute
    ) {}

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
    private final Map<String, LineupSlotDTO> homeSlotsByPlayerId;
    private final Map<String, LineupSlotDTO> awaySlotsByPlayerId;
    private final List<ScheduledSub> manualSubstitutions;

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
        this(matchId, homeTeamId, awayTeamId, homeTeam, awayTeam,
                homeStartingPlayers, awayStartingPlayers, homeBenchPlayers, awayBenchPlayers,
                homeFormation, awayFormation, homeStyle, awayStyle,
                manualSubstitutions, Collections.emptyMap(), Collections.emptyMap());
    }

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
            TeamStyle awayStyle,
            List<ScheduledSub> manualSubstitutions,
            Map<String, LineupSlotDTO> homeSlotsByPlayerId,
            Map<String, LineupSlotDTO> awaySlotsByPlayerId) {
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
        this.homeSlotsByPlayerId = defensiveCopyMap(homeSlotsByPlayerId);
        this.awaySlotsByPlayerId = defensiveCopyMap(awaySlotsByPlayerId);

        validate();
    }

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

    private static Map<String, LineupSlotDTO> defensiveCopyMap(Map<String, LineupSlotDTO> map) {
        if (map == null || map.isEmpty()) return Collections.emptyMap();
        Map<String, LineupSlotDTO> copy = new LinkedHashMap<>();
        for (Map.Entry<String, LineupSlotDTO> entry : map.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

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
    @JsonProperty("homeSlotsByPlayerId") public Map<String, LineupSlotDTO> homeSlotsByPlayerId() { return homeSlotsByPlayerId; }
    @JsonProperty("awaySlotsByPlayerId") public Map<String, LineupSlotDTO> awaySlotsByPlayerId() { return awaySlotsByPlayerId; }

    @JsonProperty("manualSubstitutions")
    public List<ScheduledSub> manualSubstitutions() {
        return Collections.unmodifiableList(manualSubstitutions);
    }

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
                    manualSubstitutions,
                    homeSlotsByPlayerId, awaySlotsByPlayerId);
        }
        if (awayTeamId.equals(teamId)) {
            return new V24MatchContext(
                    matchId, homeTeamId, awayTeamId,
                    homeTeam, awayTeam,
                    homeStartingPlayers, awayStartingPlayers,
                    homeBenchPlayers, awayBenchPlayers,
                    homeFormation, awayFormation,
                    homeStyle, newStyle,
                    manualSubstitutions,
                    homeSlotsByPlayerId, awaySlotsByPlayerId);
        }
        throw new IllegalArgumentException(
                "teamId '" + teamId + "' does not match home ('"
                + homeTeamId + "') or away ('" + awayTeamId + "')");
    }

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
                    manualSubstitutions,
                    homeSlotsByPlayerId, awaySlotsByPlayerId);
        }
        if (awayTeamId.equals(teamId)) {
            return new V24MatchContext(
                    matchId, homeTeamId, awayTeamId,
                    homeTeam, awayTeam,
                    homeStartingPlayers, awayStartingPlayers,
                    homeBenchPlayers, awayBenchPlayers,
                    homeFormation, newFormation,
                    homeStyle, awayStyle,
                    manualSubstitutions,
                    homeSlotsByPlayerId, awaySlotsByPlayerId);
        }
        throw new IllegalArgumentException(
                "teamId '" + teamId + "' does not match home ('"
                + homeTeamId + "') or away ('" + awayTeamId + "')");
    }

    public V24MatchContext withSlots(String teamId, Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId must not be blank");
        }
        Map<String, LineupSlotDTO> safeSlots = slotsByPlayerId != null ? slotsByPlayerId : Map.of();
        if (homeTeamId.equals(teamId)) {
            return new V24MatchContext(
                    matchId, homeTeamId, awayTeamId,
                    homeTeam, awayTeam,
                    homeStartingPlayers, awayStartingPlayers,
                    homeBenchPlayers, awayBenchPlayers,
                    homeFormation, awayFormation,
                    homeStyle, awayStyle,
                    manualSubstitutions,
                    safeSlots, awaySlotsByPlayerId);
        }
        if (awayTeamId.equals(teamId)) {
            return new V24MatchContext(
                    matchId, homeTeamId, awayTeamId,
                    homeTeam, awayTeam,
                    homeStartingPlayers, awayStartingPlayers,
                    homeBenchPlayers, awayBenchPlayers,
                    homeFormation, awayFormation,
                    homeStyle, awayStyle,
                    manualSubstitutions,
                    homeSlotsByPlayerId, safeSlots);
        }
        throw new IllegalArgumentException(
                "teamId '" + teamId + "' does not match home ('"
                + homeTeamId + "') or away ('" + awayTeamId + "')");
    }

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
        // O(n) with n â‰¤ 5 per team â€” trivial.
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
                nextManualSubs,
                homeSlotsByPlayerId, awaySlotsByPlayerId);
    }
}
