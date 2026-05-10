package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.domain.MatchEngineImpl;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.MatchResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * V24D5C: Detail persistence tests for V24 simulation path.
 *
 * <p>Tests that V24DetailedMatchStoragePort.save(...) is called only when
 * persistDetail=true and V24 simulation succeeds.
 * Tests that save failure does not fail the round.
 */
class V24LeagueDetailPersistenceTest {

    private static final String HOME1 = UUID.randomUUID().toString();
    private static final String AWAY1 = UUID.randomUUID().toString();

    // ========== Test 1: persistDetail false does not save ==========

    @Test
    void persistDetailFalseDoesNotSaveDetail() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=true, persistDetail=false
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, false, fakeStorage);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-no-persist", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeStorage.saveCalled, "save should NOT be called when persistDetail=false");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult());
    }

    // ========== Test 2: persistDetail true saves detail ==========

    @Test
    void persistDetailTrueSavesDetail() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=true, persistDetail=true
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, true, fakeStorage);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-persist", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeStorage.saveCalled, "save SHOULD be called when persistDetail=true");
        assertNotNull(fakeStorage.savedDetail, "saved detail should not be null");
        // careerId is homeTeamId + "_" + awayTeamId from makeCareer factory
        assertNotNull(fakeStorage.savedCareerId, "saved careerId should not be null");
        assertNotNull(fakeStorage.savedCareerId, "careerId should be stored");
    }

    // ========== Test 3: saved detail has expected fields ==========

    @Test
    void persistDetailTrueBuildsExpectedSnapshot() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, true, fakeStorage);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-snapshot", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeStorage.saveCalled, "save should be called");
        V24DetailedMatchData detail = fakeStorage.savedDetail;
        assertNotNull(detail);
        assertEquals("match-snapshot", detail.matchId());
        assertTrue(detail.homeGoals() >= 0);
        assertTrue(detail.awayGoals() >= 0);
        assertTrue(detail.homePossession() >= 0 && detail.homePossession() <= 100);
        assertTrue(detail.awayPossession() >= 0 && detail.awayPossession() <= 100);
    }

    // ========== Test 4: save failure does not fail round ==========

    @Test
    void saveFailureDoesNotFailRound() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        ThrowingStoragePort throwingStorage = new ThrowingStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, true, throwingStorage);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-fail-save", HOME1, AWAY1, 1)
        ));

        // Should NOT throw
        assertDoesNotThrow(() -> simulator.simulateLeagueRound(career, 1));
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Fixture should still have result after save failure");
    }

    // ========== Test 5: V24 context build failure does not save detail ==========

    @Test
    void contextBuildFailureDoesNotSaveDetail() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, true, fakeStorage);

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
        // No players, no starting XI — will cause context build failure
        career.setTeamManager(tm);
        career.setPlayerManager(pm);

        career.setTournamentState(makeTournamentState(
                makeFixture("match-fallback", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeStorage.saveCalled, "save should NOT be called when context build fails");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Fixture should still have result after fallback");
    }

    // ========== Test 6: persistDetail true without V24 flag does not save ==========

    @Test
    void persistDetailTrueWithoutV24FlagDoesNotSaveDetail() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=false, persistDetail=true
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, false, true, fakeStorage);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-v24-off", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeStorage.saveCalled, "save should NOT be called when V24 flag is false");
        assertTrue(fakeSim.simulateQuickCalled, "Default path should be used");
    }

    // ========== Test 7: exposeDetailApi flag does not trigger persistence ==========

    @Test
    void exposeDetailApiFlagDoesNotTriggerPersistence() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=true, persistDetail=false
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, false, fakeStorage);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-api-only", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeStorage.saveCalled, "persistDetail=false should not save even with V24 path");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult());
    }

    // ========== Test 8: aggregate result still written to fixture ==========

    @Test
    void aggregateMatchResultDataStillWrittenToFixture() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, true, fakeStorage);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-agg", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        assertNotNull(fixture.getResult(), "MatchFixture should have result");
        assertTrue(fixture.getResult().homeGoals >= 0);
        assertTrue(fixture.getResult().awayGoals >= 0);
    }

    // ========== Test 9: MatchFixture result data schema still 6 fields ==========

    @Test
    void matchFixtureResultDataSchemaStillSixFields() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, true, fakeStorage);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("match-schema", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        assertNotNull(fixture.getResult());
        assertNotNull(fixture.getResult().getHomeGoals());
        assertNotNull(fixture.getResult().getAwayGoals());
        assertNotNull(fixture.getResult().getHomePossession());
        assertNotNull(fixture.getResult().getAwayPossession());
        assertNotNull(fixture.getResult().getHomeShots());
        assertNotNull(fixture.getResult().getAwayShots());
    }

    // ========== Factory helpers ==========

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

        List<SessionPlayer> homePlayers = new ArrayList<>();
        for (int i = 0; i < homeStarterCount; i++) {
            SessionPlayer p = SessionPlayer.custom("p_" + homeTeamId + "_" + i, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(p.getSessionPlayerId(), homeTeamId);
            homePlayers.add(p);
        }

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

        @Override
        public MatchState simulateReal(MatchState state, int toMinute) {
            return state;
        }

        @Override
        public MatchResult simulateQuick(String homeTeamId, String awayTeamId, int homeOvr, int awayOvr) {
            simulateQuickCalled = true;
            return new MatchResult(1, 1);
        }
    }

    // ========== Fake Storage Port ==========

    private static class FakeStoragePort implements V24DetailedMatchStoragePort {
        boolean saveCalled = false;
        String savedCareerId = null;
        V24DetailedMatchData savedDetail = null;

        @Override
        public void save(String careerId, V24DetailedMatchData detail) {
            this.saveCalled = true;
            this.savedCareerId = careerId;
            this.savedDetail = detail;
        }

        @Override
        public java.util.Optional<V24DetailedMatchData> findByMatchId(String careerId, String matchId) {
            return java.util.Optional.empty();
        }

        @Override
        public void deleteByCareerId(String careerId) {
        }
    }

    // ========== Throwing Storage Port ==========

    private static class ThrowingStoragePort implements V24DetailedMatchStoragePort {
        @Override
        public void save(String careerId, V24DetailedMatchData detail) {
            throw new RuntimeException("Redis connection failed");
        }

        @Override
        public java.util.Optional<V24DetailedMatchData> findByMatchId(String careerId, String matchId) {
            return java.util.Optional.empty();
        }

        @Override
        public void deleteByCareerId(String careerId) {
        }
    }
}
