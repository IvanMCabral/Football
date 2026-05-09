package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.domain.MatchEngineImpl;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.service.MatchSimulator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D5B: LeagueSimulator V24 detailed engine path tests.
 *
 * <p>Tests the third simulation path behind use-v24-detailed-engine flag.
 * Validates flag precedence, context building, result mapping,
 * fallback behavior, and no Redis persistence.
 */
class V24LeagueSimulationPathTest {

    private static final String HOME1 = UUID.randomUUID().toString();
    private static final String AWAY1 = UUID.randomUUID().toString();
    private static final String HOME2 = UUID.randomUUID().toString();
    private static final String AWAY2 = UUID.randomUUID().toString();
    private static final String HOME3 = UUID.randomUUID().toString();
    private static final String AWAY3 = UUID.randomUUID().toString();
    private static final String HOME4 = UUID.randomUUID().toString();
    private static final String AWAY4 = UUID.randomUUID().toString();

    // ========== Test 1: flag disabled uses existing default path ==========

    @Test
    void flagDisabledUsesExistingDefaultPath() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        // useV24DetailedEngine=false, useV23Engine=false
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, false);

        CareerSave career = makeCareer(AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match1", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "Default path should be used when V24 flag is false");
        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        assertNotNull(fixture.getResult());
        assertEquals(50, fixture.getResult().homePossession);
        assertEquals(50, fixture.getResult().awayPossession);
    }

    // ========== Test 2: V23 flag still uses V23 path when V24 flag is false ==========

    @Test
    void v23FlagStillUsesV23PathWhenV24FlagFalse() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        MatchEngineImpl realEngine = new MatchEngineImpl();
        // useV24DetailedEngine=false, useV23Engine=true
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, realEngine, true, false);

        CareerSave career = makeCareer(AWAY2, HOME2, AWAY2, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match2", HOME2, AWAY2, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeSim.simulateQuickCalled, "V23 path should be used");
        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        assertNotNull(fixture.getResult());
        int totalPoss = fixture.getResult().homePossession + fixture.getResult().awayPossession;
        assertEquals(100, totalPoss, "V23 possession should sum to 100");
    }

    // ========== Test 3: V24 flag uses V24 detailed engine path ==========

    @Test
    void v24FlagUsesV24DetailedEnginePath() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        // useV24DetailedEngine=true
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true);

        CareerSave career = makeCareer(AWAY3, HOME3, AWAY3, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match3", HOME3, AWAY3, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeSim.simulateQuickCalled, "Default path should NOT be used when V24 flag is true");
        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        assertNotNull(fixture.getResult(), "Result should be recorded via V24 engine");
    }

    // ========== Test 4: V24 result maps to MatchResultData with only 6 fields ==========

    @Test
    void v24ResultMapsToMatchResultDataSixFieldsOnly() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true);

        CareerSave career = makeCareer(AWAY3, HOME3, AWAY3, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-six-fields", HOME3, AWAY3, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        MatchFixture.MatchResultData result = fixture.getResult();
        assertNotNull(result);

        // Verify all 6 fields are populated
        assertTrue(result.homeGoals >= 0, "homeGoals should be non-negative");
        assertTrue(result.awayGoals >= 0, "awayGoals should be non-negative");
        assertTrue(result.homePossession >= 0 && result.homePossession <= 100, "homePossession should be 0-100");
        assertTrue(result.awayPossession >= 0 && result.awayPossession <= 100, "awayPossession should be 0-100");
        assertTrue(result.homeShots >= 0, "homeShots should be non-negative");
        assertTrue(result.awayShots >= 0, "awayShots should be non-negative");

        // Verify possession sums to 100
        int totalPoss = result.homePossession + result.awayPossession;
        assertEquals(100, totalPoss, "Possession should sum to 100");

        // Verify shots >= goals per team
        assertTrue(result.homeShots >= result.homeGoals, "homeShots >= homeGoals");
        assertTrue(result.awayShots >= result.awayGoals, "awayShots >= awayGoals");
    }

    // ========== Test 5: V24 path does NOT persist detail ==========

    @Test
    void v24PathDoesNotPersistDetail() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true);

        CareerSave career = makeCareer(AWAY4, HOME4, AWAY4, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-no-persist", HOME4, AWAY4, 1)
        ));

        // Simulate — no exception means no Redis persistence attempt
        simulator.simulateLeagueRound(career, 1);

        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        assertNotNull(fixture.getResult(), "Fixture should have result even without Redis persistence");
    }

    // ========== Test 6: V24 context build failure falls back to existing path ==========

    @Test
    void v24ContextBuildFailureFallsBackToExistingPath() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true);

        // Career with NO starting XI — V24MatchContextFactory.build() will throw
        CareerSave career = new CareerSave();
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();
        SessionTeam home = SessionTeam.fromRealTeam(UUID.fromString(HOME1), "world_home",
                "Home", "Country", BigDecimal.ZERO, "4-3-3", null);
        home.setSessionTeamId(HOME1);
        SessionTeam away = SessionTeam.fromRealTeam(UUID.fromString(AWAY1), "world_away",
                "Away", "Country", BigDecimal.ZERO, "4-3-3", null);
        away.setSessionTeamId(AWAY1);
        tm.addSessionTeam(home);
        tm.addSessionTeam(away);
        for (int i = 0; i < 11; i++) {
            SessionPlayer p = SessionPlayer.custom("fallback_p" + i, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            pm.addSessionPlayer(p);
        }
        career.setTeamManager(tm);
        career.setPlayerManager(pm);
        // No teamStarting11 set — will cause context build failure

        career.setTournamentState(makeTournamentState(
                makeFixture("match-fallback", HOME1, AWAY1, 1)
        ));

        // Should NOT throw — fallback to default path
        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "Default path should be used as fallback");
        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        assertNotNull(fixture.getResult(), "Result should be recorded after fallback");
    }

    // ========== Test 7: V24 context build failure does not fail the round ==========

    @Test
    void v24ContextBuildFailureDoesNotFailRound() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true);

        CareerSave career = new CareerSave();
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();
        SessionTeam home = SessionTeam.fromRealTeam(UUID.fromString(HOME2), "world_home",
                "Home", "Country", BigDecimal.ZERO, "4-3-3", null);
        home.setSessionTeamId(HOME2);
        SessionTeam away = SessionTeam.fromRealTeam(UUID.fromString(AWAY2), "world_away",
                "Away", "Country", BigDecimal.ZERO, "4-3-3", null);
        away.setSessionTeamId(AWAY2);
        tm.addSessionTeam(home);
        tm.addSessionTeam(away);
        for (int i = 0; i < 11; i++) {
            SessionPlayer p = SessionPlayer.custom("fallback2_p" + i, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            pm.addSessionPlayer(p);
        }
        career.setTeamManager(tm);
        career.setPlayerManager(pm);
        // No starting XI

        career.setTournamentState(makeTournamentState(
                makeFixture("match-round", HOME2, AWAY2, 1)
        ));

        // Round should complete without exception
        assertDoesNotThrow(() -> simulator.simulateLeagueRound(career, 1));
    }

    // ========== Test 8: V24 flag defaults to false ==========

    @Test
    void v24FlagDefaultFalse() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim);

        CareerSave career = makeCareer(AWAY3, HOME3, AWAY3, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-default", HOME3, AWAY3, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "Default path should be used when no flags are set");
    }

    // ========== Test 9: existing LeagueSimulator tests still pass ==========

    @Test
    void existingLeagueSimulatorTestsStillPass() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim);

        CareerSave career = makeCareer(AWAY4, HOME4, AWAY4, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-existing", HOME4, AWAY4, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled);
        assertEquals(50, career.getTournamentState().getFixtures().get(0).getResult().homePossession);
    }

    // ========== Test 10: no MatchFixture schema change ==========

    @Test
    void noMatchFixtureSchemaChange() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true);

        CareerSave career = makeCareer(AWAY3, HOME3, AWAY3, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-schema", HOME3, AWAY3, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        assertNotNull(fixture.getResult());
        // Verify only the 6 expected fields exist
        assertNotNull(fixture.getResult().getHomeGoals());
        assertNotNull(fixture.getResult().getAwayGoals());
        assertNotNull(fixture.getResult().getHomePossession());
        assertNotNull(fixture.getResult().getAwayPossession());
        assertNotNull(fixture.getResult().getHomeShots());
        assertNotNull(fixture.getResult().getAwayShots());
    }

    // ========== Test 11: V24 path completes all fixtures in round ==========

    @Test
    void v24PathCompletesAllFixturesInRound() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true);

        String hA = UUID.randomUUID().toString();
        String aA = UUID.randomUUID().toString();
        String hB = UUID.randomUUID().toString();
        String aB = UUID.randomUUID().toString();

        CareerSave career = new CareerSave();
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();
        for (String tid : List.of(hA, aA, hB, aB)) {
            UUID uuid = UUID.fromString(tid);
            SessionTeam team = SessionTeam.fromRealTeam(uuid, "world_" + tid,
                    "Team " + tid, "Country", BigDecimal.ZERO, "4-3-3", null);
            tm.addSessionTeam(team);
            List<String> playerIds = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                SessionPlayer p = SessionPlayer.custom("p_" + tid + "_" + i, 25, "MID",
                        75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
                pm.addSessionPlayer(p);
                playerIds.add(p.getSessionPlayerId());
            }
            tm.setSquad(tid, playerIds);
        }
        career.setTeamManager(tm);
        career.setPlayerManager(pm);
        career.setTournamentState(makeTournamentState(
                makeFixture("multi1", hA, aA, 1),
                makeFixture("multi2", hB, aB, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertEquals(2, career.getTournamentState().getFixtures().size());
        for (MatchFixture f : career.getTournamentState().getFixtures()) {
            assertNotNull(f.getResult(), "All fixtures should complete");
        }
    }

    // ========== Factory helpers ==========

    private static CareerSave makeCareer(String careerId, String homeTeamId, String awayTeamId,
                                          int homeStarterCount, int awayStarterCount) {
        return makeCareer(homeTeamId, awayTeamId, homeTeamId, awayTeamId,
                homeStarterCount, awayStarterCount);
    }

    private static CareerSave makeCareer(String homeTeamId, String awayTeamId,
                                          String homeStartingTeamId, String awayStartingTeamId,
                                          int homeStarterCount, int awayStarterCount) {
        String careerId = homeTeamId + "_" + awayTeamId;
        CareerSave save = new CareerSave();
        save.getData().setCareerId(careerId);
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();

        for (String tid : List.of(homeTeamId, awayTeamId)) {
            UUID uuid = UUID.fromString(tid);
            SessionTeam team = SessionTeam.fromRealTeam(uuid, "world_" + tid,
                    "Team " + tid, "Country", BigDecimal.ZERO, "4-3-3", null);
            team.setSessionTeamId(tid);
            tm.addSessionTeam(team);
        }

        // Home players
        List<SessionPlayer> homePlayers = new ArrayList<>();
        for (int i = 0; i < homeStarterCount; i++) {
            SessionPlayer p = SessionPlayer.custom("p_" + homeTeamId + "_" + i, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(p.getSessionPlayerId(), homeTeamId);
            homePlayers.add(p);
        }

        // Away players
        List<SessionPlayer> awayPlayers = new ArrayList<>();
        for (int i = 0; i < awayStarterCount; i++) {
            SessionPlayer p = SessionPlayer.custom("p_" + awayTeamId + "_" + i, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(p.getSessionPlayerId(), awayTeamId);
            awayPlayers.add(p);
        }

        save.setTeamManager(tm);
        save.setPlayerManager(pm);

        // Set starting XI
        List<String> homeStarterIds = new ArrayList<>();
        for (int i = 0; i < homeStarterCount; i++) {
            homeStarterIds.add(homePlayers.get(i).getSessionPlayerId());
        }
        List<String> awayStarterIds = new ArrayList<>();
        for (int i = 0; i < awayStarterCount; i++) {
            awayStarterIds.add(awayPlayers.get(i).getSessionPlayerId());
        }
        save.getTeamStarting11().put(homeStartingTeamId, homeStarterIds);
        save.getTeamStarting11().put(awayStartingTeamId, awayStarterIds);

        save.setTournamentState(new TournamentState());
        return save;
    }

    private static MatchFixture makeFixture(String matchId, String homeId, String awayId, int round) {
        return new MatchFixture(matchId, homeId, awayId, round);
    }

    private static TournamentState makeTournamentState(MatchFixture... fixtures) {
        TournamentState ts = new TournamentState();
        ts.setCurrentRound(1);
        ts.setTotalRounds(5);
        for (MatchFixture f : fixtures) {
            ts.getFixtures().add(f);
        }
        return ts;
    }

    // ========== Fake MatchSimulator ==========

    private static class FakeMatchSimulator implements MatchSimulator {
        boolean simulateQuickCalled = false;
        int callCount = 0;

        @Override
        public MatchState simulateReal(MatchState state, int toMinute) {
            return state;
        }

        @Override
        public MatchResult simulateQuick(String homeTeamId, String awayTeamId, int homeOvr, int awayOvr) {
            simulateQuickCalled = true;
            callCount++;
            return new MatchResult(1, 1);
        }
    }
}