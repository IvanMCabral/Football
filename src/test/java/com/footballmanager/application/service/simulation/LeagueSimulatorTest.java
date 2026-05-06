package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.domain.MatchEngineImpl;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.service.MatchSimulator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10C4: LeagueSimulator integration tests.
 * Validates dual-path simulation (DefaultMatchSimulator vs V23 engine).
 * Tests only — no production code changes.
 */
class LeagueSimulatorTest {

    // Valid UUID strings for test teams — buildMinimalTeam uses UUID.fromString()
    private static final String HOME1 = UUID.randomUUID().toString();
    private static final String AWAY1 = UUID.randomUUID().toString();
    private static final String HOME2 = UUID.randomUUID().toString();
    private static final String AWAY2 = UUID.randomUUID().toString();
    private static final String HOME3 = UUID.randomUUID().toString();
    private static final String AWAY3 = UUID.randomUUID().toString();
    private static final String HOME4 = UUID.randomUUID().toString();
    private static final String AWAY4 = UUID.randomUUID().toString();
    private static final String HOME5 = UUID.randomUUID().toString();
    private static final String AWAY5 = UUID.randomUUID().toString();
    private static final String HOME6 = UUID.randomUUID().toString();
    private static final String AWAY6 = UUID.randomUUID().toString();
    private static final String HA = UUID.randomUUID().toString();
    private static final String AA = UUID.randomUUID().toString();
    private static final String HB = UUID.randomUUID().toString();
    private static final String AB = UUID.randomUUID().toString();

    // ========== Test Fixtures ==========

    private static CareerSave makeCareer(String homeId, String awayId) {
        CareerSave save = new CareerSave();
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();
        for (String tid : List.of(homeId, awayId)) {
            UUID uuid = UUID.fromString(tid);
            SessionTeam team = SessionTeam.fromRealTeam(uuid, "world_" + tid,
                    "Team " + tid, "Country", BigDecimal.ZERO, "4-3-3", null);
            tm.addSessionTeam(team);
            List<String> playerIds = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                SessionPlayer p = makePlayer("p_" + tid + "_" + i, 75);
                pm.addSessionPlayer(p);
                playerIds.add(p.getSessionPlayerId());
            }
            tm.setSquad(tid, playerIds);
        }
        save.setTeamManager(tm);
        save.setPlayerManager(pm);
        save.setTournamentState(new TournamentState());
        return save;
    }

    private static CareerSave makeCareer4() {
        return makeCareer(HOME4, AWAY4);
    }

    private static SessionPlayer makePlayer(String id, int ovr) {
        SessionPlayer p = SessionPlayer.custom(id, 25, "MID",
                ovr, ovr, ovr, ovr, ovr, ovr, BigDecimal.valueOf(1000));
        p.setSessionPlayerId(id);
        return p;
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

    private static MatchFixture.MatchResultData makeResultData(int hg, int ag, int hp, int ap, int hs, int as) {
        return new MatchFixture.MatchResultData(hg, ag, hp, ap, hs, as);
    }

    // ========== Test 1: default constructor uses simulateQuick with hardcoded 50/50 possession, 5/5 shots ==========

    @Test
    void defaultConstructorUsesDefaultMatchSimulatorPath() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim);

        CareerSave career = makeCareer(HOME1, AWAY1);
        career.setTournamentState(makeTournamentState(
                makeFixture("match1", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "simulateQuick should be called");
        assertEquals(HOME1, fakeSim.lastHomeTeamId, "home team id passed to simulateQuick");
        assertEquals(AWAY1, fakeSim.lastAwayTeamId, "away team id passed to simulateQuick");

        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        assertNotNull(fixture.getResult(), "Result should be recorded");
        assertEquals(50, fixture.getResult().homePossession, "Home possession hardcoded 50");
        assertEquals(50, fixture.getResult().awayPossession, "Away possession hardcoded 50");
        assertEquals(5, fixture.getResult().homeShots, "Home shots hardcoded 5");
        assertEquals(5, fixture.getResult().awayShots, "Away shots hardcoded 5");
    }

    // ========== Test 2: explicit flag false uses default path ==========

    @Test
    void flagFalseUsesDefaultMatchSimulatorPath() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false);

        CareerSave career = makeCareer(HOME2, AWAY2);
        career.setTournamentState(makeTournamentState(
                makeFixture("match2", HOME2, AWAY2, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "simulateQuick should be called with flag=false");
        assertEquals(50, career.getTournamentState().getFixtures().get(0).getResult().homePossession);
        assertEquals(50, career.getTournamentState().getFixtures().get(0).getResult().awayPossession);
        assertEquals(5, career.getTournamentState().getFixtures().get(0).getResult().homeShots);
        assertEquals(5, career.getTournamentState().getFixtures().get(0).getResult().awayShots);
    }

    // ========== Test 3: flag true uses V23 engine, computed possession/shots ==========

    @Test
    void flagTrueUsesV23Path_computedPossessionShots() {
        MatchEngineImpl realEngine = new MatchEngineImpl();
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, realEngine, true);

        CareerSave career = makeCareer(HOME3, AWAY3);
        career.setTournamentState(makeTournamentState(
                makeFixture("match3", HOME3, AWAY3, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeSim.simulateQuickCalled, "simulateQuick should NOT be called when flag=true");

        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        assertNotNull(fixture.getResult(), "Result should be recorded via V23 engine");

        int homePoss = fixture.getResult().homePossession;
        int awayPoss = fixture.getResult().awayPossession;
        assertEquals(100, homePoss + awayPoss, "Possession should sum to 100");

        int homeShots = fixture.getResult().homeShots;
        int awayShots = fixture.getResult().awayShots;
        assertTrue(homeShots >= 3, "Home shots >= 3 (V23 floor)");
        assertTrue(awayShots >= 3, "Away shots >= 3 (V23 floor)");

        assertTrue(fixture.getResult().homeGoals >= 0, "Home goals non-negative");
        assertTrue(fixture.getResult().awayGoals >= 0, "Away goals non-negative");

        assertTrue(homeShots >= fixture.getResult().homeGoals, "Home shots >= home goals");
        assertTrue(awayShots >= fixture.getResult().awayGoals, "Away shots >= away goals");
    }

    // ========== Test 4: V23 path is deterministic for same fixture ==========

    @Test
    void v23PathIsDeterministicForSameFixture() {
        MatchEngineImpl realEngine = new MatchEngineImpl();
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, realEngine, true);

        CareerSave career = makeCareer4();
        career.setTournamentState(makeTournamentState(
                makeFixture("match4", HOME4, AWAY4, 1)
        ));

        simulator.simulateLeagueRound(career, 1);
        MatchFixture f1 = career.getTournamentState().getFixtures().get(0);
        int r1HomePoss = f1.getResult().homePossession;
        int r1AwayPoss = f1.getResult().awayPossession;
        int r1HomeGoals = f1.getResult().homeGoals;
        int r1AwayGoals = f1.getResult().awayGoals;

        // Reset with same matchId — same seed
        career.setTournamentState(makeTournamentState(
                makeFixture("match4", HOME4, AWAY4, 1)
        ));
        simulator.simulateLeagueRound(career, 1);
        MatchFixture f2 = career.getTournamentState().getFixtures().get(0);
        int r2HomePoss = f2.getResult().homePossession;
        int r2AwayPoss = f2.getResult().awayPossession;
        int r2HomeGoals = f2.getResult().homeGoals;
        int r2AwayGoals = f2.getResult().awayGoals;

        assertEquals(r1HomePoss, r2HomePoss, "Home possession deterministic");
        assertEquals(r1AwayPoss, r2AwayPoss, "Away possession deterministic");
        assertEquals(r1HomeGoals, r2HomeGoals, "Home goals deterministic");
        assertEquals(r1AwayGoals, r2AwayGoals, "Away goals deterministic");
    }

    // ========== Test 5: completed fixtures are skipped ==========

    @Test
    void completedFixturesAreSkipped() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim);

        MatchFixture completed = makeFixture("match_completed", HOME5, AWAY5, 1);
        completed.complete(makeResultData(2, 1, 50, 50, 6, 4));

        CareerSave career = makeCareer(HOME5, AWAY5);
        career.setTournamentState(makeTournamentState(completed));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeSim.simulateQuickCalled, "Completed fixture should be skipped");
        assertEquals(2, career.getTournamentState().getFixtures().get(0).getResult().homeGoals);
        assertEquals(1, career.getTournamentState().getFixtures().get(0).getResult().awayGoals);
    }

    // ========== Test 6: wrong round fixtures are skipped ==========

    @Test
    void wrongRoundFixturesAreSkipped() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim);

        CareerSave career = makeCareer(HOME6, AWAY6);
        career.setTournamentState(makeTournamentState(
                makeFixture("match_r2", HOME6, AWAY6, 2)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeSim.simulateQuickCalled, "Round 2 fixture skipped when simulating round 1");
    }

    // ========== Test 7: all fixtures in round are simulated ==========

    @Test
    void allFixturesInRoundAreSimulated() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim);

        CareerSave save = new CareerSave();
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();
        for (String tid : List.of(HA, AA, HB, AB)) {
            UUID uuid = UUID.fromString(tid);
            SessionTeam team = SessionTeam.fromRealTeam(uuid, "world_" + tid,
                    "Team " + tid, "Country", BigDecimal.ZERO, "4-3-3", null);
            tm.addSessionTeam(team);
            List<String> playerIds = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                SessionPlayer p = makePlayer("p_" + tid + "_" + i, 75);
                pm.addSessionPlayer(p);
                playerIds.add(p.getSessionPlayerId());
            }
            tm.setSquad(tid, playerIds);
        }
        save.setTeamManager(tm);
        save.setPlayerManager(pm);
        save.setTournamentState(makeTournamentState(
                makeFixture("multi1", HA, AA, 1),
                makeFixture("multi2", HB, AB, 1)
        ));

        simulator.simulateLeagueRound(save, 1);

        assertEquals(2, fakeSim.callCount, "Both round-1 fixtures should be simulated");
    }

    // ========== Fake MatchSimulator ==========

    private static class FakeMatchSimulator implements MatchSimulator {
        boolean simulateQuickCalled = false;
        int callCount = 0;
        String lastHomeTeamId = null;
        String lastAwayTeamId = null;

        @Override
        public MatchState simulateReal(MatchState state, int toMinute) {
            return state;
        }

        @Override
        public MatchResult simulateQuick(String homeTeamId, String awayTeamId, int homeOvr, int awayOvr) {
            simulateQuickCalled = true;
            callCount++;
            lastHomeTeamId = homeTeamId;
            lastAwayTeamId = awayTeamId;
            return new MatchResult(1, 1);
        }
    }
}