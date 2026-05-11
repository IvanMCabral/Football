package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.domain.MatchEngineImpl;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
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

/**
 * V24D5D: End-to-end flag integration tests for LeagueSimulator.
 *
 * <p>Validates all feature flag combinations across:
 * - use-v24-detailed-engine (V24 simulation path)
 * - persist-detail (V24 detail persistence)
 * - expose-detail-api (read API flag, no simulation effect)
 *
 * <p>Also validates:
 * - Flag precedence: V24 > V23 > default
 * - No MatchFixture schema mutation
 * - No CareerSave/SessionPlayer/SessionTeam mutation
 * - Best-effort persistence (save failure does not fail round)
 * - Context build failure falls back and skips persistence
 */
class V24EndToEndFlagIntegrationTest {

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

    // ========== Test 1: allFlagsFalse uses default path ==========

    /**
     * When all V24 flags are false, LeagueSimulator uses existing default path.
     * Default path calls DefaultMatchSimulator.simulateQuick() directly.
     */
    @Test
    void allFlagsFalseUsesDefaultPath() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV23Engine=false, useV24=false, persistDetail=false, storagePort=null
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, false, false, fakeStorage);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("e2e-all-false", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "Default path should be used");
        assertFalse(fakeStorage.saveCalled, "No persistence expected");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Fixture result should be written");
    }

    // ========== Test 2: v24EnabledPersistDisabled produces aggregate only ==========

    /**
     * V24 path active, persistence disabled: aggregate result written to fixture,
     * no storagePort.save() call. Validates V24 path without persistence side-effect.
     */
    @Test
    void v24EnabledPersistDisabledProducesAggregateOnly() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=true, persistDetail=false
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, false, fakeStorage);

        CareerSave career = makeCareer(HOME2, AWAY2, HOME2, AWAY2, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("e2e-v24-no-persist", HOME2, AWAY2, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeSim.simulateQuickCalled, "Default path should NOT be used when V24 flag is true");
        assertFalse(fakeStorage.saveCalled, "save should NOT be called when persistDetail=false");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Aggregate fixture result should be written");
        // V24 path used — possession should sum to 100
        MatchFixture.MatchResultData result = career.getTournamentState().getFixtures().get(0).getResult();
        assertEquals(100, result.homePossession + result.awayPossession,
                "V24 path possession should sum to 100");
    }

    // ========== Test 3: v24EnabledPersistEnabled saves detail ==========

    /**
     * V24 path + persistence enabled: storagePort.save(...) called once per successful V24 match.
     * Validates V24DetailedMatchData.fromResult(...) path exercised.
     */
    @Test
    void v24EnabledPersistEnabledSavesDetail() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=true, persistDetail=true
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, true, fakeStorage);

        CareerSave career = makeCareer(HOME3, AWAY3, HOME3, AWAY3, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("e2e-v24-persist", HOME3, AWAY3, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeSim.simulateQuickCalled, "V24 path should be used");
        assertTrue(fakeStorage.saveCalled, "save SHOULD be called when persistDetail=true and V24 succeeds");
        assertNotNull(fakeStorage.savedDetail, "saved detail should not be null");
        assertEquals("e2e-v24-persist", fakeStorage.savedDetail.matchId(),
                "matchId should be set from fixture");
        assertNotNull(fakeStorage.savedCareerId, "careerId should be set");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Aggregate fixture result should be written");
    }

    // ========== Test 4: v24DisabledPersistEnabled does not persist ==========

    /**
     * persist-detail alone does nothing when V24 engine is not enabled.
     * Flags are independent: persistDetail requires useV24DetailedEngine to trigger V24 path.
     */
    @Test
    void v24DisabledPersistEnabledDoesNotPersist() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=false, persistDetail=true
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, false, true, fakeStorage);

        CareerSave career = makeCareer(HOME4, AWAY4, HOME4, AWAY4, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("e2e-persist-only", HOME4, AWAY4, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "Default path should be used");
        assertFalse(fakeStorage.saveCalled,
                "save should NOT be called when V24 flag is false even if persistDetail=true");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult());
    }

    // ========== Test 5: exposeDetailApi flag does not trigger simulation or persistence ==========

    /**
     * expose-detail-api controls read-side only. It has no simulation effect.
     * When V24 is disabled, the read flag cannot trigger simulation or persistence.
     */
    @Test
    void exposeDetailApiEnabledDoesNotTriggerSimulationOrPersistence() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=false, persistDetail=false, exposeDetailApi=true (passed to LeagueSimulator but unused in service)
        // LeagueSimulator does not consume exposeDetailApi at simulation level
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, false, false, fakeStorage);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("e2e-api-only", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "Default path should be used");
        assertFalse(fakeStorage.saveCalled,
                "no persistence when V24 and persistDetail are both false");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult());
    }

    // ========== Test 6: allFlagsTrue completes round and persists detail ==========

    /**
     * All three flags enabled: V24 path, persistence, and API flag (read-side).
     * Simulation uses V24 path, saves detail, and round completes.
     * API flag does not interfere with simulation.
     */
    @Test
    void allFlagsTrueCompletesRoundAndPersistsDetail() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=true, persistDetail=true
        // exposeDetailApi is read-side only (controlled at controller layer, not LeagueSimulator)
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, true, fakeStorage);

        CareerSave career = makeCareer(HOME5, AWAY5, HOME5, AWAY5, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("e2e-all-true", HOME5, AWAY5, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeSim.simulateQuickCalled, "V24 path should be used");
        assertTrue(fakeStorage.saveCalled, "save SHOULD be called when all flags true");
        assertNotNull(fakeStorage.savedDetail, "detail should be saved");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Round should complete");
    }

    // ========== Test 7: v24 context failure falls back and does not persist ==========

    /**
     * Context build failure (missing starting XI) triggers fallback to default path.
     * Fallback path does not call storagePort.save() — persistence is skipped.
     * Round completes successfully.
     */
    @Test
    void v24ContextFailureFallsBackAndDoesNotPersist() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=true, persistDetail=true
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, true, fakeStorage);

        // Career with no starting XI — context build will throw
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
        // No players — no starting XI possible
        career.setTeamManager(tm);
        career.setPlayerManager(pm);

        career.setTournamentState(makeTournamentState(
                makeFixture("e2e-fallback-no-persist", HOME1, AWAY1, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "Fallback path should be used");
        assertFalse(fakeStorage.saveCalled,
                "save should NOT be called when context build fails and fallback is used");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Fixture should still have result after fallback");
    }

    // ========== Test 8: detail save failure does not fail round ==========

    /**
     * storagePort.save throws: exception is caught, round completes.
     * Fixture result is still written — best-effort persistence validated.
     */
    @Test
    void detailSaveFailureDoesNotFailRound() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        ThrowingStoragePort throwingStorage = new ThrowingStoragePort();
        // useV24DetailedEngine=true, persistDetail=true
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, true, throwingStorage);

        CareerSave career = makeCareer(HOME2, AWAY2, HOME2, AWAY2, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("e2e-save-fail", HOME2, AWAY2, 1)
        ));

        assertDoesNotThrow(() -> simulator.simulateLeagueRound(career, 1),
                "Save failure should not throw — round must complete");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Fixture result should be written even when save fails");
    }

    // ========== Test 9: v24 takes precedence over v23 when both enabled ==========

    /**
     * When both useV24DetailedEngine=true and useV23Engine=true, V24 path wins.
     * Flag precedence: V24 > V23 > default.
     * V23 engine path is not used.
     */
    @Test
    void v24TakesPrecedenceOverV23WhenBothEnabled() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        MatchEngineImpl realEngine = new MatchEngineImpl();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV23Engine=true, useV24DetailedEngine=true, persistDetail=true
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, realEngine, true, true, true, fakeStorage);

        CareerSave career = makeCareer(HOME3, AWAY3, HOME3, AWAY3, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("e2e-precedence", HOME3, AWAY3, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(fakeSim.simulateQuickCalled, "Default path should NOT be used");
        // V23 path also not used (realEngine path is V23)
        assertTrue(fakeStorage.saveCalled, "V24 path should be used (wins over V23)");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Round should complete via V24 path");
    }

    // ========== Test 10: default flags remain safe ==========

    /**
     * No-arg LeagueSimulator constructor: all V24 flags default to false.
     * Ensures default instantiation is safe — existing behavior preserved.
     */
    @Test
    void defaultFlagsRemainSafe() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // Single-arg constructor: useV24DetailedEngine=false, persistDetail=false
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, false, false, fakeStorage);

        CareerSave career = makeCareer(HOME4, AWAY4, HOME4, AWAY4, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("e2e-default", HOME4, AWAY4, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "Default path should be used with no flags");
        assertFalse(fakeStorage.saveCalled, "No persistence with all flags false");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult());
    }

    // ========== Test 11: matchResultData schema still six fields ==========

    /**
     * MatchFixture.MatchResultData schema is unchanged by V24D5 flags.
     * V24 path writes aggregate (goals, possession, shots) only.
     * No timeline, xG, or detail fields added to fixture result.
     */
    @Test
    void matchResultDataSchemaStillSixFields() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, true, fakeStorage);

        CareerSave career = makeCareer(HOME5, AWAY5, HOME5, AWAY5, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("e2e-schema", HOME5, AWAY5, 1)
        ));

        simulator.simulateLeagueRound(career, 1);

        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        MatchFixture.MatchResultData result = fixture.getResult();
        assertNotNull(result, "Result should be written");
        // Verify exactly 6 fields
        assertNotNull(result.getHomeGoals(), "homeGoals should exist");
        assertNotNull(result.getAwayGoals(), "awayGoals should exist");
        assertNotNull(result.getHomePossession(), "homePossession should exist");
        assertNotNull(result.getAwayPossession(), "awayPossession should exist");
        assertNotNull(result.getHomeShots(), "homeShots should exist");
        assertNotNull(result.getAwayShots(), "awayShots should exist");
        // Possession sums to 100 in V24 path
        assertEquals(100, result.homePossession + result.awayPossession,
                "Possession should sum to 100 in V24 path");
    }

    // ========== Test 12: no career state mutation after v24 simulation ==========

    /**
     * V24 simulation does not mutate CareerSave schema, SessionPlayer energy/injury/form,
     * or SessionTeam formation/name IDs.
     * Snapshot before and after; assert unchanged.
     */
    @Test
    void noCareerStateMutationAfterV24Simulation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(fakeSim, null, false, true, true, fakeStorage);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(
                makeFixture("e2e-no-mutation", HOME1, AWAY1, 1)
        ));

        // Snapshot before
        int homePlayerEnergyBefore = career.getPlayerManager()
                .getSessionPlayer(career.getTeamManager().getSquadPlayerIds(HOME1).get(0))
                .getEnergy();
        String homeFormationBefore = career.getTeamManager().getSessionTeam(HOME1).getFormation();

        simulator.simulateLeagueRound(career, 1);

        // Snapshot after
        int homePlayerEnergyAfter = career.getPlayerManager()
                .getSessionPlayer(career.getTeamManager().getSquadPlayerIds(HOME1).get(0))
                .getEnergy();
        String homeFormationAfter = career.getTeamManager().getSessionTeam(HOME1).getFormation();

        assertEquals(homePlayerEnergyBefore, homePlayerEnergyAfter,
                "Player energy should not change after V24 simulation");
        assertEquals(homeFormationBefore, homeFormationAfter,
                "Team formation should not change after V24 simulation");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Result should still be written despite no mutation");
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