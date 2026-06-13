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
}