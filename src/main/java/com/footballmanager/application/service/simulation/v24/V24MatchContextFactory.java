package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V24D5A: Factory that builds V24MatchContext from existing career/session data.
 *
 * <p>Provides a safe bridge between CareerSave/MatchFixture/SessionTeam data
 * and V24MatchContext. Fully isolated — no production simulation wiring.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>career, fixture, homeTeam, awayTeam must not be null</li>
 *   <li>starting XI must resolve to exactly 11 valid SessionPlayer objects per team</li>
 *   <li>player IDs must exist in CareerSave.playerManager</li>
 *   <li>duplicate starter IDs are rejected</li>
 *   <li>bench excludes starters; may be empty</li>
 * </ul>
 *
 * <p>Behavior on invalid input:
 * <ul>
 *   <li>{@link #build(Career, fixture, homeTeam, awayTeam, seed)} throws IllegalArgumentException</li>
 *   <li>{@link #canBuild(...)} returns false and never throws</li>
 * </ul>
 *
 * <p>No mutation: does not modify CareerSave, SessionPlayer, SessionTeam, or MatchFixture.
 */
public final class V24MatchContextFactory {

    /**
     * Builds a V24MatchContext from career/session data.
     *
     * @param career   non-null CareerSave with playerManager and teamStarting11
     * @param fixture  non-null MatchFixture providing matchId, homeTeamId, awayTeamId
     * @param homeTeam non-null SessionTeam providing formation and team identity
     * @param awayTeam non-null SessionTeam providing formation and team identity
     * @param seed     deterministic seed passed through to V24MatchContext
     * @return a new V24MatchContext (never null)
     * @throws IllegalArgumentException on validation failure
     */
    public V24MatchContext build(
            CareerSave career,
            MatchFixture fixture,
            SessionTeam homeTeam,
            SessionTeam awayTeam,
            long seed) {
        return buildWithStyles(career, fixture, homeTeam, awayTeam, null, null, seed);
    }

    /**
     * Builds a V24MatchContext with explicit TeamStyles.
     *
     * @param career    non-null CareerSave
     * @param fixture   non-null MatchFixture
     * @param homeTeam  non-null SessionTeam
     * @param awayTeam  non-null SessionTeam
     * @param homeStyle nullable; defaults to BALANCED if null
     * @param awayStyle nullable; defaults to BALANCED if null
     * @param seed      deterministic seed
     * @return a new V24MatchContext
     * @throws IllegalArgumentException on validation failure
     */
    public V24MatchContext buildWithStyles(
            CareerSave career,
            MatchFixture fixture,
            SessionTeam homeTeam,
            SessionTeam awayTeam,
            TeamStyle homeStyle,
            TeamStyle awayStyle,
            long seed) {

        validateInputs(career, fixture, homeTeam, awayTeam);

        String matchId = fixture.getMatchId();
        String homeTeamId = resolveTeamId(fixture.getHomeTeamId(), homeTeam);
        String awayTeamId = resolveTeamId(fixture.getAwayTeamId(), awayTeam);

        List<SessionPlayer> homeStarters = resolveStartingXI(
                career, homeTeamId, "home");
        List<SessionPlayer> awayStarters = resolveStartingXI(
                career, awayTeamId, "away");

        validateStarterCount(homeStarters, "home");
        validateStarterCount(awayStarters, "away");
        validateNoDuplicateStarters(homeStarters, "home");
        validateNoDuplicateStarters(awayStarters, "away");

        List<SessionPlayer> homeBench = deriveBench(career, homeTeamId, homeStarters);
        List<SessionPlayer> awayBench = deriveBench(career, awayTeamId, awayStarters);

        String homeFormation = homeTeam.getFormation();
        String awayFormation = awayTeam.getFormation();

        return new V24MatchContext(
                matchId,
                homeTeamId,
                awayTeamId,
                homeTeam,
                awayTeam,
                homeStarters,
                awayStarters,
                homeBench,
                awayBench,
                homeFormation,
                awayFormation,
                homeStyle,
                awayStyle);
    }

    /**
     * Returns true if buildWithStyles would succeed for the given inputs.
     * Never throws — returns false for any validation failure.
     */
    public boolean canBuild(
            CareerSave career,
            MatchFixture fixture,
            SessionTeam homeTeam,
            SessionTeam awayTeam) {
        try {
            build(career, fixture, homeTeam, awayTeam, 0L);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    // ========== Validation helpers ==========

    private void validateInputs(
            CareerSave career,
            MatchFixture fixture,
            SessionTeam homeTeam,
            SessionTeam awayTeam) {
        if (career == null) {
            throw new IllegalArgumentException("career must not be null");
        }
        if (fixture == null) {
            throw new IllegalArgumentException("fixture must not be null");
        }
        if (homeTeam == null) {
            throw new IllegalArgumentException("homeTeam must not be null");
        }
        if (awayTeam == null) {
            throw new IllegalArgumentException("awayTeam must not be null");
        }
    }

    private void validateStarterCount(List<SessionPlayer> starters, String teamLabel) {
        if (starters.size() != 11) {
            throw new IllegalArgumentException(
                    teamLabel + "StartingPlayers must contain exactly 11 players, got " + starters.size());
        }
    }

    private void validateNoDuplicateStarters(List<SessionPlayer> starters, String teamLabel) {
        Set<String> seen = new HashSet<>();
        for (SessionPlayer p : starters) {
            String id = p.getSessionPlayerId();
            if (!seen.add(id)) {
                throw new IllegalArgumentException(
                        teamLabel + "StartingPlayers contains duplicate playerId: " + id);
            }
        }
    }

    // ========== Resolution helpers ==========

    private String resolveTeamId(String fixtureTeamId, SessionTeam team) {
        if (fixtureTeamId != null && !fixtureTeamId.isBlank()) {
            return fixtureTeamId;
        }
        return team.getSessionTeamId();
    }

    private List<SessionPlayer> resolveStartingXI(
            CareerSave career, String teamId, String teamLabel) {
        Map<String, List<String>> starting11 = career.getTeamStarting11();
        if (starting11 == null || !starting11.containsKey(teamId)) {
            throw new IllegalArgumentException(
                    teamLabel + " starting XI not found in career for teamId: " + teamId);
        }
        List<String> playerIds = starting11.get(teamId);
        if (playerIds == null) {
            throw new IllegalArgumentException(
                    teamLabel + " starting XI list is null for teamId: " + teamId);
        }

        List<SessionPlayer> resolved = new ArrayList<>();
        for (String playerId : playerIds) {
            if (playerId == null || playerId.isBlank()) {
                throw new IllegalArgumentException(
                        teamLabel + " starting XI contains null/blank playerId");
            }
            SessionPlayer player = career.getSessionPlayer(playerId);
            if (player == null) {
                throw new IllegalArgumentException(
                        teamLabel + " starting XI playerId not found in playerManager: " + playerId);
            }
            resolved.add(player);
        }
        return resolved;
    }

    /**
     * Derives bench as all team players minus the starting XI.
     * Bench may be empty — that is acceptable.
     */
    private List<SessionPlayer> deriveBench(
            CareerSave career, String teamId, List<SessionPlayer> starters) {
        Set<String> starterIds = starters.stream()
                .map(SessionPlayer::getSessionPlayerId)
                .collect(Collectors.toSet());

        List<SessionPlayer> squad = career.getTeamSquad(teamId);
        if (squad == null || squad.isEmpty()) {
            return List.of();
        }

        return squad.stream()
                .filter(p -> !starterIds.contains(p.getSessionPlayerId()))
                .collect(Collectors.toList());
    }
}