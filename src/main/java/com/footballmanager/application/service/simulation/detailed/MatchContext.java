package com.footballmanager.application.service.simulation.detailed;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MatchContext {

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
    private final Map<String, LineupSlot> homeSlotsByPlayerId;
    private final Map<String, LineupSlot> awaySlotsByPlayerId;
    private final List<ScheduledSub> manualSubstitutions;

    public MatchContext(
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

    public MatchContext(
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
            Map<String, LineupSlot> homeSlotsByPlayerId,
            Map<String, LineupSlot> awaySlotsByPlayerId) {
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

    public MatchContext(
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
        int min = com.footballmanager.domain.service.LineupRules.MIN_AVAILABLE_PLAYERS;
        if (size < min || size > com.footballmanager.domain.service.LineupRules.MAX_LINEUP_PLAYERS) {
            throw new IllegalArgumentException(
                    label + " must contain between " + min + " and 11 players, got " + size);
        }
    }

    private static List<SessionPlayer> defensiveCopy(List<SessionPlayer> list) {
        if (list == null) return Collections.emptyList();
        return Collections.unmodifiableList(new java.util.ArrayList<>(list));
    }

    private static Map<String, LineupSlot> defensiveCopyMap(Map<String, LineupSlot> map) {
        if (map == null || map.isEmpty()) return Collections.emptyMap();
        Map<String, LineupSlot> copy = new LinkedHashMap<>();
        for (Map.Entry<String, LineupSlot> entry : map.entrySet()) {
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
    @JsonProperty("homeSlotsByPlayerId") public Map<String, LineupSlot> homeSlotsByPlayerId() { return homeSlotsByPlayerId; }
    @JsonProperty("awaySlotsByPlayerId") public Map<String, LineupSlot> awaySlotsByPlayerId() { return awaySlotsByPlayerId; }

    @JsonProperty("manualSubstitutions")
    public List<ScheduledSub> manualSubstitutions() {
        return Collections.unmodifiableList(manualSubstitutions);
    }

    public MatchContext withNewStyle(String teamId, TeamStyle newStyle) {
        if (newStyle == null) {
            throw new IllegalArgumentException("newStyle must not be null");
        }
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId must not be blank");
        }
        if (homeTeamId.equals(teamId)) {
            return new MatchContext(
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
            return new MatchContext(
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

    public MatchContext withNewFormation(String teamId, String newFormation) {
        if (newFormation == null || newFormation.isBlank()) {
            throw new IllegalArgumentException("newFormation must not be null or blank");
        }
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId must not be blank");
        }
        if (homeTeamId.equals(teamId)) {
            return new MatchContext(
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
            return new MatchContext(
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

    public MatchContext withSlots(String teamId, Map<String, LineupSlot> slotsByPlayerId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId must not be blank");
        }
        Map<String, LineupSlot> safeSlots = slotsByPlayerId != null ? slotsByPlayerId : Map.of();
        if (homeTeamId.equals(teamId)) {
            return new MatchContext(
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
            return new MatchContext(
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

    public MatchContext withManualSubstitution(String teamId,
                                                  String playerOffId,
                                                  String playerOnId,
                                                  int minute) {
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
        for (ScheduledSub existing : manualSubstitutions) {
            if (existing.teamId().equals(teamId)
                    && existing.playerOffId().equals(playerOffId)) {
                throw new IllegalArgumentException(
                    "playerOffId '" + playerOffId + "' already has a scheduled substitution for team '" + teamId + "'");
            }
        }
        List<ScheduledSub> nextManualSubs = new ArrayList<>(manualSubstitutions.size() + 1);
        nextManualSubs.addAll(manualSubstitutions);
        nextManualSubs.add(new ScheduledSub(teamId, playerOffId, playerOnId, minute));
        return new MatchContext(
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
