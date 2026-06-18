package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *   <li>{@link #build(Career, fixture, homeTeam, awayTeam, seed) throws IllegalArgumentException</li>
 *   <li>{@link #canBuild(...)} returns false and never throws</li>
 * </ul>
 *
 * <p>No mutation: does not modify CareerSave, SessionPlayer, SessionTeam, or MatchFixture.
 */
@Component
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
     * @param awayTeam non-null SessionTeam
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

        List<SessionPlayer> homeStarters = resolveStartingXI(career, homeTeamId, "home");
        List<SessionPlayer> awayStarters = resolveStartingXI(career, awayTeamId, "away");

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
        if (career == null) throw new IllegalArgumentException("career must not be null");
        if (fixture == null) throw new IllegalArgumentException("fixture must not be null");
        if (homeTeam == null) throw new IllegalArgumentException("homeTeam must not be null");
        if (awayTeam == null) throw new IllegalArgumentException("awayTeam must not be null");
    }

    private void validateStarterCount(List<SessionPlayer> starters, String teamLabel) {
        // V24D6U2: accept short-handed lineups in [MIN, 11]
        int min = com.footballmanager.application.service.lineup.LineupRules.MIN_AVAILABLE_PLAYERS;
        if (starters.size() < min || starters.size() > 11) {
            throw new IllegalArgumentException(
                    teamLabel + "StartingPlayers must contain between " + min
                    + " and 11 players, got " + starters.size());
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

    /**
     * V24D6M11: Always use team.getSessionTeamId() — the career-internal ID.
     * The fixture's teamId may not match career storage format.
     */
    private String resolveTeamId(String fixtureTeamId, SessionTeam team) {
        return team.getSessionTeamId();
    }

    /**
     * Resolve starting XI via:
     * 1. CareerSave.teamStarting11 (HashMap written by LineupController).
     * 2. CareerTeamManager.teamSquads (written by CareerTeamManager.assignPlayerToSquad).
     * Either path must yield 11 valid SessionPlayer objects.
     */
    private List<SessionPlayer> resolveStartingXI(CareerSave career, String teamId, String teamLabel) {
        // Try CareerSave.teamStarting11 first (LineupController writes here)
        List<SessionPlayer> resolved = resolveFromStarting11OrNull(career, teamId, teamLabel);
        if (resolved != null) return resolved;

        // V24D6M11: Fallback — derive from squad
        resolved = deriveStartingXIfromSquad(career, teamId, teamLabel);
        int min = com.footballmanager.application.service.lineup.LineupRules.MIN_AVAILABLE_PLAYERS;
        if (resolved.size() >= min) return resolved;

        throw new IllegalArgumentException(
                teamLabel + " starting XI must contain at least " + min
                + " players, got " + resolved.size()
                + " for teamId: " + teamId);
    }

    /**
     * Try CareerSave.teamStarting11 (LineupController writes Map.Entry<teamId, List<playerId&gt;).
     * Returns null if not found or too few entries — signals fallback.
     * Throws if entries exist but contain null/blank/unknown playerId.
     *
     * <p>LIVE-MATCH-F5.2 BUG-003 defensive fix: if the teamStarting11 has
     * at least one stale player ID (player not found in CareerSave.playerManager
     * anymore), we treat the teamStarting11 as untrustworthy and return null
     * so the caller falls back to {@link #deriveStartingXIfromSquad}. This
     * scenario occurs when the orchestrator's per-round mutations
     * (suspensions, injuries, sales) remove a player from the playerManager
     * between rounds, leaving teamStarting11 with dangling references. The
     * old behaviour threw an IAE that bubbled up to a 422 LINEUP_VALIDATION_ERROR,
     * blocking Fecha 2+. The new behaviour recovers gracefully by using the
     * squad (the players currently in the team) as the source of truth.
     *
     * <p>Behaviour is unchanged for the happy path (teamStarting11 has all
     * valid IDs) and for fully missing teamStarting11 (returns null as
     * before, caller falls back to squad derivation).
     */
    private List<SessionPlayer> resolveFromStarting11OrNull(
            CareerSave career, String teamId, String teamLabel) {
        Map<String, List<String>> starting11 = career.getTeamStarting11();
        if (starting11 == null) return null;
        List<String> ids = starting11.get(teamId);
        if (ids == null || ids.isEmpty()) return null;
        if (ids.size() > 11) {
            throw new IllegalArgumentException(
                    teamLabel + " starting XI has " + ids.size()
                    + " entries — maximum is 11 for teamId: " + teamId);
        }
        // V24D6U2: short-handed entries flow through (used to fall back to squad
        // derivation, but that masks user intent; the user explicitly submitted
        // a short-handed XI and the engine should honour it).
        int min = com.footballmanager.application.service.lineup.LineupRules.MIN_AVAILABLE_PLAYERS;
        if (ids.size() < min) {
            throw new IllegalArgumentException(
                    teamLabel + " starting XI has " + ids.size()
                    + " entries — minimum is " + min + " for teamId: " + teamId);
        }

        List<SessionPlayer> resolved = new ArrayList<>();
        int staleCount = 0;
        for (String pid : ids) {
            if (pid == null || pid.isBlank()) {
                // Null/blank entries in teamStarting11 are clearly invalid
                // user data; preserve the original IAE so the user can fix
                // their lineup explicitly.
                throw new IllegalArgumentException(
                        teamLabel + " starting XI contains null/blank playerId for teamId: " + teamId);
            }
            SessionPlayer p = career.getSessionPlayer(pid);
            if (p == null) {
                // BUG-003 defensive fix: stale reference — the player was
                // removed from the playerManager between rounds. Count it
                // and continue; if ALL entries are stale we fall back to
                // the squad (via returning null), otherwise we accept the
                // partial lineup.
                staleCount++;
                continue;
            }
            resolved.add(p);
        }
        if (staleCount > 0) {
            org.slf4j.LoggerFactory.getLogger(V24MatchContextFactory.class).warn(
                "[BUG-003] teamStarting11 for teamId={} has {} stale playerId(s) "
                + "(player removed from playerManager between rounds). "
                + "Resolved {}/{} — falling back to squad derivation to ensure "
                + "a complete 11-player starting XI.",
                teamId, staleCount, resolved.size(), ids.size());
            // BUG-003 fix: ANY stale reference means the teamStarting11
            // is partially invalid; fall back to deriveStartingXIfromSquad
            // to ensure a complete 11-player starting XI. Partial lineups
            // (e.g. 10 valid + 1 stale) would short the team by 1 player,
            // which the engine would then complain about at runtime.
            return null;
        }
        if (resolved.size() < min) {
            // The teamStarting11 had too few entries (already validated
            // above for > min, so this means it's between 0 and min). Fall
            // back to the squad so the match can still start.
            return null;
        }
        return resolved;
    }

    /**
     * V24D6M11: Derive starting XI from squad (CareerTeamManager.teamSquads).
     * Handles fresh careers where LineupController has not been used yet.
     */
    private List<SessionPlayer> deriveStartingXIfromSquad(
            CareerSave career, String teamId, String teamLabel) {
        // Try CareerTeamManager.teamSquads (written by CareerTeamManager.assignPlayerToSquad)
        List<String> squadIds = career.getTeamManager().getSquadPlayerIds(teamId);
        int min = com.footballmanager.application.service.lineup.LineupRules.MIN_AVAILABLE_PLAYERS;
        if (squadIds == null || squadIds.size() < min) {
            throw new IllegalArgumentException(
                    teamLabel + " squad has only "
                    + (squadIds != null ? squadIds.size() : 0)
                    + " players for teamId: " + teamId
                    + " — need at least " + min + " for starting XI");
        }
        List<SessionPlayer> starters = new ArrayList<>();
        int take = Math.min(11, squadIds.size());
        for (int i = 0; i < take; i++) {
            SessionPlayer p = career.getSessionPlayer(squadIds.get(i));
            if (p == null) {
                throw new IllegalArgumentException(
                        teamLabel + " squad player not found: " + squadIds.get(i));
            }
            starters.add(p);
        }
        return starters;
    }

    /**
     * Derives bench as all team players minus the starting XI.
     * Bench may be empty — acceptable.
     */
    private List<SessionPlayer> deriveBench(
            CareerSave career, String teamId, List<SessionPlayer> starters) {
        Set<String> starterIds = starters.stream()
                .map(SessionPlayer::getSessionPlayerId)
                .collect(Collectors.toSet());
        List<SessionPlayer> squad = career.getTeamSquad(teamId);
        if (squad == null || squad.isEmpty()) return List.of();
        return squad.stream()
                .filter(p -> !starterIds.contains(p.getSessionPlayerId()))
                .collect(Collectors.toList());
    }
}