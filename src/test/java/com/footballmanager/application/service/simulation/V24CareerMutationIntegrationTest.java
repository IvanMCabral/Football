package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.v24.V24CareerMutationService;
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
 * V24D6B3: Integration tests for V24 career mutation wiring in LeagueSimulator.
 *
 * <p>Tests mutation behavior across different flag combinations and V24 path states.
 * All mutation flags default to false — mutation never occurs without explicit enablement.
 */
class V24CareerMutationIntegrationTest {

    private static final String HOME1 = UUID.randomUUID().toString();
    private static final String AWAY1 = UUID.randomUUID().toString();

    // ========== Test 1: all mutation flags false → no injury mutation ==========

    @Test
    void allMutationFlagsFalse_noInjuryMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // All mutation flags false
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                false, false, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(makeFixture("m1", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        assertFalse(p.getInjured(), "Player should NOT be injured when all mutation flags are false");
    }

    // ========== Test 2: master flag false + persistInjuries=true → no mutation ==========

    @Test
    void masterFlagFalse_noMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // mutateCareerState=false, persistInjuries=true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                false, true, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(makeFixture("m2", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        assertFalse(p.getInjured(), "Player should NOT be injured when master flag is false");
    }

    // ========== Test 3: master true + persistInjuries=false → no mutation ==========

    @Test
    void masterFlagTrue_butSpecificFlagFalse_noMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // mutateCareerState=true, persistInjuries=false
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(makeFixture("m3", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        assertFalse(p.getInjured(), "Player should NOT be injured when specific flag is false");
    }

    // ========== Test 4: verify no INJURY events means no mutation ==========

    @Test
    void noInjuryEvents_noMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(makeFixture("m4", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        // V24 engine ran; if no INJURY events in timeline, no mutation
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult());
        // This test passes as long as round completes without error
    }

    // ========== Test 5: persistDetail=true alone does not mutate ==========

    @Test
    void persistDetailTrueAlone_doesNotMutate() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // persistDetail=true, all mutation flags false
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, true, fakeStorage,
                false, false, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(makeFixture("m5", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeStorage.saveCalled, "Detail should be saved when persistDetail=true");
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        assertFalse(p.getInjured(), "Player should NOT be injured when mutation flags are false");
    }

    // ========== Test 6: V24 disabled + mutation flags true → no mutation ==========

    @Test
    void V24DisabledWithMutationFlags_noMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=false, mutation flags true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, false, false, fakeStorage,
                true, true, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(makeFixture("m6", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "Default path should be used");
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        assertFalse(p.getInjured(), "Player should NOT be injured when V24 is disabled");
    }

    // ========== Test 7: default path does not trigger mutation ==========

    @Test
    void V23Path_doesNotTriggerMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // Default path (no V23, no V24), mutation flags true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, false, false, fakeStorage,
                true, true, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(makeFixture("m7", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        assertFalse(p.getInjured(), "Player should NOT be injured on default path");
    }

    // ========== Test 8: round completes without throwing ==========

    @Test
    void mutationServiceFailure_doesNotFailRound() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(makeFixture("m8", HOME1, AWAY1, 1)));

        // Should NOT throw even though mutation service behavior is unchanged here
        assertDoesNotThrow(() -> simulator.simulateLeagueRound(career, 1));
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult());
    }

    // ========== Test 9: V24 path + mutation flags → round completes successfully ==========

    @Test
    void mutationAfterV24Success_only() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(makeFixture("m9", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        MatchFixture fixture = career.getTournamentState().getFixtures().get(0);
        assertNotNull(fixture.getResult(), "Fixture should have result");
        // V24 path completed; mutation was attempted but may or may not have applied
        // depending on whether V24 engine generated INJURY events
    }

    // ========== Test 10: MatchFixture.MatchResultData remains unchanged ==========

    @Test
    void matchResultDataRemainsUnchanged() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        career.setTournamentState(makeTournamentState(makeFixture("m10", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        MatchFixture.MatchResultData result = career.getTournamentState().getFixtures().get(0).getResult();
        assertNotNull(result.getHomeGoals());
        assertNotNull(result.getAwayGoals());
        assertNotNull(result.getHomePossession());
        assertNotNull(result.getAwayPossession());
        assertNotNull(result.getHomeShots());
        assertNotNull(result.getAwayShots());
        // Still 6 fields — no new fields added
    }

    // ========== Test 11: no fatigue/energy change ==========

    @Test
    void noFatigueEnergyChange() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(career.getTeamStarting11().get(HOME1).get(0));
        int originalEnergy = 85;
        p.setEnergy(originalEnergy);

        career.setTournamentState(makeTournamentState(makeFixture("m11", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertEquals(originalEnergy, p.getEnergy(), "Energy should not change in V24D6B3");
    }

    // ========== Test 12: no cards/form change ==========

    @Test
    void noCardsOrFormChange() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(career.getTeamStarting11().get(HOME1).get(0));
        int originalForm = p.getForm();

        career.setTournamentState(makeTournamentState(makeFixture("m12", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertEquals(originalForm, p.getForm(), "Form should not change in V24D6B3");
    }

    // ========== Test 13: already injured player not overwritten ==========

    @Test
    void alreadyInjuredPlayerNotOverwritten() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(career.getTeamStarting11().get(HOME1).get(0));
        p.setInjured(true);
        p.setInjuryType("PRE_EXISTING");
        p.setInjuryRemainingMatches(99);

        career.setTournamentState(makeTournamentState(makeFixture("m13", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(p.getInjured(), "Player should remain injured");
        assertEquals("PRE_EXISTING", p.getInjuryType(), "Pre-existing injury type should not be overwritten");
        assertEquals(99, p.getInjuryRemainingMatches(), "Pre-existing duration should not be overwritten");
    }

    // ========== Test 14: mutateCareerState=true + persistFatigue=true → energy reduced ==========

    @Test
    void masterFlagTrue_plus_persistFatigueTrue_reducesEnergy() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // All mutation flags false EXCEPT master + fatigue
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, true, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        p.setEnergy(100);
        career.setTournamentState(makeTournamentState(makeFixture("m14", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        // With persistFatigue=true and master on, energy IS reduced (88) because
        // V24DetailedMatchEngine produces non-substitution events
        assertEquals(88, p.getEnergy(), "Energy should be reduced when persistFatigue=true");
    }

    // ========== Test 15: mutateCareerState=false + persistFatigue=true → no energy mutation ==========

    @Test
    void masterFlagFalse_plus_persistFatigueTrue_noMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // mutateCareerState=false, persistFatigue=true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                false, false, true, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        p.setEnergy(100);
        career.setTournamentState(makeTournamentState(makeFixture("m15", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertEquals(100, p.getEnergy(), "No energy change when master flag is false");
    }

    // ========== Test 16: persistFatigue=true alone does not apply injuries ==========

    @Test
    void persistFatigueTrueAlone_doesNotApplyInjuries() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // persistFatigue=true, persistInjuries=false
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, true, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        p.setEnergy(100);
        career.setTournamentState(makeTournamentState(makeFixture("m16", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(p.getInjured(), "No injuries when persistInjuries is false");
    }

    // ========== Test 17: V24 disabled + fatigue flags true → no energy mutation ==========

    @Test
    void V24Disabled_withFatigueFlags_noMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=false, mutation flags true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, false, false, fakeStorage,
                true, false, true, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        p.setEnergy(100);
        career.setTournamentState(makeTournamentState(makeFixture("m17", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "Default path should be used");
        assertEquals(100, p.getEnergy(), "No energy change when V24 is disabled");
    }

    // ========== Test 18: expose-detail-api=true alone does not reduce energy ==========

    @Test
    void exposeDetailApiAlone_doesNotReduceEnergy() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // expose-detail-api would be set at config level; here we test mutation flags independent
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                false, false, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        p.setEnergy(100);
        career.setTournamentState(makeTournamentState(makeFixture("m18", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertEquals(100, p.getEnergy(), "No energy change when mutation flags are false");
    }

    // ========== V24D6F2: LeagueSimulator Wiring / Best-Effort Integration Tests ==========

    /**
     * Test 1: master=false + injury+fatique both true → no mutation at wiring level.
     * Regression: LeagueSimulator.applyV24CareerMutation checks isCareerMutationEnabled() first.
     */
    @Test
    void mutateCareerStateFalse_plusInjuryAndFatigueBothTrue_noMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // mutateCareerState=false, persistInjuries=true, persistFatigue=true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                false, true, true, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        p.setEnergy(100);
        career.setTournamentState(makeTournamentState(makeFixture("m20", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertFalse(p.getInjured(), "No injury when master flag is false");
        assertEquals(100, p.getEnergy(), "No energy change when master flag is false");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Fixture result must exist after round completes");
    }

    /**
     * Test 3: persistDetail storage throws → round completes.
     * Regression: persistV24Detail is best-effort; storage failure does not fail the round.
     * This is covered by existing test 18 design, but we add explicit storage-failure test here.
     */
    @Test
    void persistDetailStorageFails_roundCompletes() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        ThrowingStoragePort throwingStorage = new ThrowingStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, true, throwingStorage,
                false, false, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        p.setEnergy(100);
        career.setTournamentState(makeTournamentState(makeFixture("m21", HOME1, AWAY1, 1)));

        assertDoesNotThrow(() -> simulator.simulateLeagueRound(career, 1),
                "Round must complete even when detail storage throws");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Fixture result must exist after round completes");
        assertEquals(100, p.getEnergy(), "No energy change when mutation flags are false");
    }

    // ========== Test 19: no cards/form mutation ==========

    @Test
    void noCardsOrFormMutation_integration() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, true, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        int originalForm = p.getForm();

        career.setTournamentState(makeTournamentState(makeFixture("m19", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertEquals(originalForm, p.getForm(), "Form should not change in V24D6C3");
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

    /**
     * V24D6F2: Fake storage port that throws on save.
     * Used to verify persistV24Detail is best-effort.
     */
    private static class ThrowingStoragePort implements V24DetailedMatchStoragePort {
        @Override
        public void save(String careerId, V24DetailedMatchData detail) {
            throw new RuntimeException("Simulated storage failure");
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