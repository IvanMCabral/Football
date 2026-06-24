package com.footballmanager.application.service.query;

import com.footballmanager.application.service.query.FixtureQueryDtos.AllRoundsWithBye;
import com.footballmanager.application.service.query.FixtureQueryDtos.MatchInfo;
import com.footballmanager.application.service.query.FixtureQueryDtos.RoundFixturesWithBye;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D24.3-FIX: Coverage for {@link UserDivisionFixtureQueryService} cross-division team name resolution.
 *
 * <p>Before the fix, {@code FixtureQueryHelper.buildTeamNamesMap} only received
 * {@code userDivision.getTeamIds()}. When {@code replaceFixtures} (test-harness)
 * injected fixtures from another division, those {@code homeTeamId}/{@code awayTeamId}
 * were missing from the map and the fallback returned the raw UUID, leaking it to
 * Panel C (BUG_FIXTURES_TEAM_NAMES_UUID_V2).
 *
 * <p>The contract verified here:
 * <ul>
 *   <li>{@code getRoundWithBye} resolves names for ALL teams in the round, including cross-division.</li>
 *   <li>{@code getAllRoundsWithBye} resolves names for cross-division teams in any round.</li>
 *   <li>{@code getByRound} resolves names for cross-division teams.</li>
 *   <li>{@code getAll} resolves names for cross-division teams in any fixture of the career.</li>
 *   <li>Regression: user-division-only fixtures still resolve to real names (not UUIDs).</li>
 * </ul>
 */
@DisplayName("UserDivisionFixtureQueryService — V24D24.3 cross-division team names")
class UserDivisionFixtureQueryServiceTest {

    private static final String USER_TEAM = "user-team-uuid-001";
    private static final String USER_TEAM_NAME = "River Plate FC";

    private static final String DIV_A_TEAM = "div-a-team-uuid-002";
    private static final String DIV_A_TEAM_NAME = "Boca Juniors";

    private static final String DIV_B_TEAM_1 = "div-b-team-uuid-101";
    private static final String DIV_B_TEAM_1_NAME = "Atletico Naranja";

    private static final String DIV_B_TEAM_2 = "div-b-team-uuid-102";
    private static final String DIV_B_TEAM_2_NAME = "Deportivo Limon";

    private UserDivisionFixtureQueryService service;

    @BeforeEach
    void setUp() {
        service = new UserDivisionFixtureQueryService();
    }

    /**
     * Build a CareerSave with two divisions:
     *   - User division (1): [USER_TEAM, DIV_A_TEAM]
     *   - Other division (2): [DIV_B_TEAM_1, DIV_B_TEAM_2]
     * Plus a TournamentState populated with the given fixtures.
     */
    private CareerSave buildCareerWithTwoDivisions(List<MatchFixture> fixtures) {
        CareerSave save = new CareerSave();

        CareerTeamManager tm = new CareerTeamManager();
        addTeam(tm, USER_TEAM, USER_TEAM_NAME);
        addTeam(tm, DIV_A_TEAM, DIV_A_TEAM_NAME);
        addTeam(tm, DIV_B_TEAM_1, DIV_B_TEAM_1_NAME);
        addTeam(tm, DIV_B_TEAM_2, DIV_B_TEAM_2_NAME);
        save.setTeamManager(tm);

        CareerPlayerManager pm = new CareerPlayerManager();
        for (String tid : List.of(USER_TEAM, DIV_A_TEAM, DIV_B_TEAM_1, DIV_B_TEAM_2)) {
            for (int i = 0; i < 11; i++) {
                SessionPlayer p = makePlayer("p_" + tid + "_" + i, 70);
                pm.addSessionPlayer(p);
            }
        }
        save.setPlayerManager(pm);

        Division userDiv = new Division("Primera", 1);
        userDiv.setTeamIds(new ArrayList<>(List.of(USER_TEAM, DIV_A_TEAM)));
        Division otherDiv = new Division("Segunda", 2);
        otherDiv.setTeamIds(new ArrayList<>(List.of(DIV_B_TEAM_1, DIV_B_TEAM_2)));
        save.getSeasonManager().setDivisions(List.of(userDiv, otherDiv));

        save.setUserSessionTeamId(USER_TEAM);

        TournamentState ts = new TournamentState();
        ts.setCurrentRound(1);
        ts.setTotalRounds(2);
        for (MatchFixture f : fixtures) {
            ts.getFixtures().add(f);
        }
        save.setTournamentState(ts);

        return save;
    }

    private void addTeam(CareerTeamManager tm, String id, String name) {
        SessionTeam t = SessionTeam.fromRealTeam(
                java.util.UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", Math.abs(id.hashCode()) % 1_000_000_000_000L)),
                "world_" + id,
                name,
                "Argentina",
                BigDecimal.ZERO,
                "4-3-3",
                null);
        t.setSessionTeamId(id);
        tm.addSessionTeam(t);
    }

    private SessionPlayer makePlayer(String id, int ovr) {
        SessionPlayer p = SessionPlayer.custom(id, 25, "MID",
                ovr, ovr, ovr, ovr, ovr, ovr, BigDecimal.valueOf(1000));
        p.setSessionPlayerId(id);
        return p;
    }

    private MatchFixture fixture(String matchId, String homeId, String awayId, int round) {
        return new MatchFixture(matchId, homeId, awayId, round);
    }

    // ========== getRoundWithBye ==========

    @Test
    @DisplayName("getRoundWithBye — user-division-only fixtures: real names (regression)")
    void getRoundWithBye_userDivisionOnly_returnsRealNames() {
        CareerSave save = buildCareerWithTwoDivisions(List.of(
                fixture("m1", USER_TEAM, DIV_A_TEAM, 1)
        ));

        RoundFixturesWithBye result = service.getRoundWithBye(save, 1).block();

        assertNotNull(result);
        assertEquals(1, result.matches().size());
        MatchInfo match = result.matches().get(0);
        assertEquals(USER_TEAM_NAME, match.homeTeamName(),
                "User-division team must resolve to its real name (regression for user-division case)");
        assertEquals(DIV_A_TEAM_NAME, match.awayTeamName(),
                "User-division team must resolve to its real name (regression for user-division case)");
    }

    @Test
    @DisplayName("getRoundWithBye — cross-division fixture (replaceFixtures scenario): real names, NOT UUIDs")
    void getRoundWithBye_withCrossDivisionFixture_returnsRealNames() {
        // Simulates test-harness replaceFixtures: round 1 gets an extra match with teams from
        // division 2 (not in userDivision). Before fix, these leaked UUIDs in homeTeamName/awayTeamName.
        CareerSave save = buildCareerWithTwoDivisions(List.of(
                fixture("m1", USER_TEAM, DIV_A_TEAM, 1),
                fixture("m2", DIV_B_TEAM_1, DIV_B_TEAM_2, 1)  // cross-division: B teams NOT in userDiv
        ));

        RoundFixturesWithBye result = service.getRoundWithBye(save, 1).block();

        assertNotNull(result);
        assertEquals(2, result.matches().size(), "Round 1 must contain both matches");

        MatchInfo crossMatch = result.matches().stream()
                .filter(m -> DIV_B_TEAM_1.equals(m.homeTeamId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected cross-division match m2 not found"));

        assertEquals(DIV_B_TEAM_1_NAME, crossMatch.homeTeamName(),
                "Cross-division homeTeamId must resolve to real name (NOT UUID). "
              + "Before V24D24.3-FIX this was: " + DIV_B_TEAM_1);
        assertEquals(DIV_B_TEAM_2_NAME, crossMatch.awayTeamName(),
                "Cross-division awayTeamId must resolve to real name (NOT UUID). "
              + "Before V24D24.3-FIX this was: " + DIV_B_TEAM_2);
    }

    // ========== getAllRoundsWithBye ==========

    @Test
    @DisplayName("getAllRoundsWithBye — mixed rounds with cross-division fixture: real names everywhere")
    void getAllRoundsWithBye_withMixedFixtures_returnsRealNames() {
        // Round 1 has only user-division fixtures; round 2 has a cross-division fixture
        // (replaceFixtures scenario for round 2 only).
        CareerSave save = buildCareerWithTwoDivisions(List.of(
                fixture("m1", USER_TEAM, DIV_A_TEAM, 1),
                fixture("m2", USER_TEAM, DIV_A_TEAM, 2),
                fixture("m3", DIV_B_TEAM_1, DIV_B_TEAM_2, 2)  // cross-division in round 2
        ));

        AllRoundsWithBye result = service.getAllRoundsWithBye(save).block();

        assertNotNull(result);
        assertEquals(2, result.rounds().size());

        // Round 2 has the cross-division match — verify it resolves
        var round2 = result.rounds().stream()
                .filter(r -> r.round() == 2)
                .findFirst()
                .orElseThrow();
        MatchInfo crossMatch = round2.matches().stream()
                .filter(m -> DIV_B_TEAM_1.equals(m.homeTeamId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cross-division match m3 not found in round 2"));

        assertEquals(DIV_B_TEAM_1_NAME, crossMatch.homeTeamName(),
                "Round 2 cross-division homeTeamId must resolve to real name");
        assertEquals(DIV_B_TEAM_2_NAME, crossMatch.awayTeamName(),
                "Round 2 cross-division awayTeamId must resolve to real name");
    }

    // ========== getByRound ==========

    @Test
    @DisplayName("getByRound — cross-division fixture: real names, NOT UUIDs")
    void getByRound_withCrossDivisionFixture_returnsRealNames() {
        CareerSave save = buildCareerWithTwoDivisions(List.of(
                fixture("m1", USER_TEAM, DIV_A_TEAM, 1),
                fixture("m2", DIV_B_TEAM_1, DIV_B_TEAM_2, 1)
        ));

        List<MatchInfo> matches = service.getByRound(save, 1).block();

        assertNotNull(matches);
        assertEquals(2, matches.size());
        MatchInfo crossMatch = matches.stream()
                .filter(m -> DIV_B_TEAM_1.equals(m.homeTeamId()))
                .findFirst()
                .orElseThrow();
        assertEquals(DIV_B_TEAM_1_NAME, crossMatch.homeTeamName());
        assertEquals(DIV_B_TEAM_2_NAME, crossMatch.awayTeamName());
    }

    // ========== getAll ==========

    @Test
    @DisplayName("getAll — cross-division fixtures across the career: real names in every round")
    void getAll_withCrossDivisionFixture_returnsRealNames() {
        // Career has cross-division fixtures only in round 2; round 1 is user-division-only.
        CareerSave save = buildCareerWithTwoDivisions(List.of(
                fixture("m1", USER_TEAM, DIV_A_TEAM, 1),
                fixture("m2", USER_TEAM, DIV_A_TEAM, 2),
                fixture("m3", DIV_B_TEAM_1, DIV_B_TEAM_2, 2)
        ));

        var response = service.getAll(save).block();

        assertNotNull(response);
        // The career-wide teamNames map must contain BOTH division-B team names so any
        // round that surfaces them can resolve them.
        assertEquals(DIV_B_TEAM_1_NAME, response.teamNames().get(DIV_B_TEAM_1),
                "getAll() teamNames map must include cross-division teams for ALL rounds");
        assertEquals(DIV_B_TEAM_2_NAME, response.teamNames().get(DIV_B_TEAM_2),
                "getAll() teamNames map must include cross-division teams for ALL rounds");
    }
}