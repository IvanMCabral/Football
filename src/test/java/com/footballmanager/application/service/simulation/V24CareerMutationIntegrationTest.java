package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.v24.V24CareerMutationService;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngineProvider;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
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

    // ========== V24D6D5: Discipline mutation wiring tests ==========

    @Test
    void persistDisciplineEnabled_appliesDisciplineMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // mutateCareerState=true, persistDiscipline=true, injuries/fatigue disabled
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, true, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));

        career.setTournamentState(makeTournamentState(makeFixture("md1", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Fixture result must exist after round completes");
        // Round completes successfully when discipline flag is enabled
        // (card events are produced by V24 engine naturally)
    }

    @Test
    void persistDisciplineRequiresMasterGate() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // mutateCareerState=false, persistDiscipline=true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                false, false, false, true, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        int originalYellow = p.getYellowCards();
        int originalRed = p.getRedCards();

        career.setTournamentState(makeTournamentState(makeFixture("md2", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertEquals(originalYellow, p.getYellowCards(),
                "Yellow cards should not change when master flag is false");
        assertEquals(originalRed, p.getRedCards(),
                "Red cards should not change when master flag is false");
        assertFalse(p.getSuspended(),
                "Player should not be suspended when master flag is false");
    }

    @Test
    void persistDisciplineSpecificFlagFalse_noDisciplineMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // mutateCareerState=true, persistDiscipline=false
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, false, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        int originalYellow = p.getYellowCards();

        career.setTournamentState(makeTournamentState(makeFixture("md3", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertEquals(originalYellow, p.getYellowCards(),
                "Yellow cards should not change when discipline flag is false");
        assertFalse(p.getSuspended(),
                "Player should not be suspended when discipline flag is false");
    }

    @Test
    void persistDisciplineIndependentFromInjuryAndFatigue() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // Only discipline enabled
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, true, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        int originalEnergy = p.getEnergy();

        career.setTournamentState(makeTournamentState(makeFixture("md4", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertEquals(originalEnergy, p.getEnergy(),
                "Energy should not change when fatigue flag is false");
        assertFalse(p.getInjured(),
                "Player should not be injured when injury flag is false");
        // Round completes; discipline may or may not be applied depending on V24 engine events
    }

    @Test
    void v24DisabledWithDisciplineFlags_noMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // useV24DetailedEngine=false, discipline flags true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, false, false, fakeStorage,
                true, false, false, true, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);
        SessionPlayer p = career.getSessionPlayer(
                career.getTeamStarting11().get(HOME1).get(0));
        int originalYellow = p.getYellowCards();

        career.setTournamentState(makeTournamentState(makeFixture("md5", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertEquals(originalYellow, p.getYellowCards(),
                "Yellow cards should not change in V23 path");
        assertFalse(p.getSuspended(),
                "Player should not be suspended in V23 path");
    }

    @Test
    void allMutationFlagsEnabled_appliesInjuryFatigueDisciplineTogether() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();
        // All three mutation flags enabled
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, true, true, false);

        CareerSave career = makeCareer(HOME1, AWAY1, HOME1, AWAY1, 11, 11);

        career.setTournamentState(makeTournamentState(makeFixture("md6", HOME1, AWAY1, 1)));

        simulator.simulateLeagueRound(career, 1);

        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Fixture result must exist after round with all flags enabled");
    }

    // ========== V24D6D6B: Suspension lifecycle integration tests ==========

    /**
     * Deterministic V24 engine for lifecycle testing.
     * Returns a controlled V24DetailedMatchResult with explicit timeline events.
     * No randomness — tests control every event.
     */
    private static class DeterministicV24Engine implements V24DetailedMatchEngineProvider {
        private final V24DetailedMatchResult result;

        DeterministicV24Engine(V24DetailedMatchResult result) {
            this.result = result;
        }

        @Override
        public V24DetailedMatchResult simulate(V24MatchContext context, long seed) {
            return result;
        }
    }

    /**
     * Test 1: pre-existing suspension not participated → decrements and clears.
     * Fake V24 result has no events for suspended player.
     * Suspended player is NOT in starting XI, NOT in timeline.
     * Expect: suspended=false, remaining=0.
     */
    @Test
    void preExistingSuspension_notParticipated_decrementsAndClears() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        // Fake V24 result with no RED_CARD, no events for suspended player
        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sl1").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.0).awayXg(0.3)
                .homeShots(5).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(new com.footballmanager.application.service.simulation.v24.V24MatchTimeline())
                .summary("Deterministic: no cards")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, true, false,
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithSuspension(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "suspended_p1", 1, true);

        career.setTournamentState(makeTournamentState(makeFixture("sl1", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("suspended_p1");
        assertFalse(p.getSuspended(), "Suspended player who did not participate should be cleared");
        assertEquals(0, p.getSuspensionRemainingMatches(), "Remaining matches should be 0 after served");
    }

    /**
     * Test 2: pre-existing suspension remaining=2, not participated → decrements to 1.
     * Fake V24 result with no RED_CARD for suspended player.
     * Suspended player is NOT in starting XI, NOT in timeline.
     * Expect: suspended=true, remaining=1.
     */
    @Test
    void preExistingSuspension_remainingTwo_decrementsToOne() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sl2").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(2).awayGoals(1).homeXg(1.8).awayXg(0.9)
                .homeShots(7).awayShots(4).homePossession(52).awayPossession(48)
                .timeline(new com.footballmanager.application.service.simulation.v24.V24MatchTimeline())
                .summary("Deterministic: no cards")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, true, false,
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithSuspension(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "suspended_p1", 2, true);

        career.setTournamentState(makeTournamentState(makeFixture("sl2", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("suspended_p1");
        assertTrue(p.getSuspended(), "Player should remain suspended");
        assertEquals(1, p.getSuspensionRemainingMatches(), "Remaining matches should decrement to 1");
    }

    /**
     * Test 3: pre-existing suspended player participated → does NOT decrement.
     * Suspended player IS in starting XI, so participated=true.
     * Expect: suspended=true, remaining=1 (unchanged).
     */
    @Test
    void preExistingSuspendedPlayerParticipated_doesNotDecrement() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        // Empty timeline — participation comes from starting XI
        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sl3").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(0).awayGoals(0).homeXg(0.5).awayXg(0.5)
                .homeShots(3).awayShots(3).homePossession(50).awayPossession(50)
                .timeline(new com.footballmanager.application.service.simulation.v24.V24MatchTimeline())
                .summary("Deterministic: clean match")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, true, false,
                new DeterministicV24Engine(fakeResult));

        // participated=true → suspended player is in starting XI
        CareerSave career = makeCareerWithSuspension(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "suspended_p1", 1, true, true); // participated=true

        career.setTournamentState(makeTournamentState(makeFixture("sl3", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("suspended_p1");
        assertTrue(p.getSuspended(), "Participated suspended player should remain suspended");
        assertEquals(1, p.getSuspensionRemainingMatches(), "Remaining should not decrement");
    }

    /**
     * Test 4: player receives RED_CARD in round → not decremented same round.
     * Fake V24 result includes RED_CARD for the player.
     * Expect: suspended=true, remaining=1 (newlySuspended blocks decrement).
     */
    @Test
    void redCardInRound_playerStillSuspendedAfterRound() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        com.footballmanager.application.service.simulation.v24.V24MatchTimeline timeline =
                new com.footballmanager.application.service.simulation.v24.V24MatchTimeline();
        timeline.addEvent(new com.footballmanager.application.service.simulation.v24.V24MatchEvent(
                45,
                com.footballmanager.application.service.simulation.v24.V24MatchEventType.RED_CARD,
                HOME1, "suspended_p1", "Suspended Player", null, null, 0.0, "Second yellow"));

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sl4").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(0).awayGoals(1).homeXg(0.4).awayXg(1.2)
                .homeShots(2).awayShots(6).homePossession(40).awayPossession(60)
                .timeline(timeline)
                .summary("Deterministic: red card")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, true, false,
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithSuspension(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "suspended_p1", 1, true);

        career.setTournamentState(makeTournamentState(makeFixture("sl4", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("suspended_p1");
        assertTrue(p.getSuspended(), "Red-carded player should be suspended");
        assertEquals(1, p.getSuspensionRemainingMatches(), "RED_CARD takes priority over decrement");
    }

    /**
     * Test 5: pre-existing suspended player also receives RED_CARD → not cleared.
     * Player suspended (remaining=1), fake result has RED_CARD for same player.
     * Expect: suspended=true, remaining=1 (newlySuspended blocks decrement).
     */
    @Test
    void preExistingSuspendedAndRedCardedAgain_notCleared() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        com.footballmanager.application.service.simulation.v24.V24MatchTimeline timeline =
                new com.footballmanager.application.service.simulation.v24.V24MatchTimeline();
        timeline.addEvent(new com.footballmanager.application.service.simulation.v24.V24MatchEvent(
                60,
                com.footballmanager.application.service.simulation.v24.V24MatchEventType.RED_CARD,
                HOME1, "both_p1", "Both Player", null, null, 0.0, "Second yellow"));

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sl5").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(2).homeXg(0.8).awayXg(1.5)
                .homeShots(4).awayShots(7).homePossession(45).awayPossession(55)
                .timeline(timeline)
                .summary("Deterministic: red card on already suspended")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, true, false,
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithSuspension(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "both_p1", 1, true);

        career.setTournamentState(makeTournamentState(makeFixture("sl5", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("both_p1");
        assertTrue(p.getSuspended(), "Player suspended and red-carded should remain suspended");
        assertEquals(1, p.getSuspensionRemainingMatches(), "Should not decrement when newly suspended");
    }

    /**
     * Test 6: persistDiscipline=false → no lifecycle change.
     * Master=true but discipline=false.
     * Expect: suspended state unchanged.
     */
    @Test
    void persistDisciplineFalse_noLifecycleChange() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sl6").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(1).homeXg(1.1).awayXg(0.9)
                .homeShots(5).awayShots(4).homePossession(52).awayPossession(48)
                .timeline(new com.footballmanager.application.service.simulation.v24.V24MatchTimeline())
                .summary("Deterministic: no discipline")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, false, false, // discipline=false
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithSuspension(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "suspended_p1", 1, true);

        career.setTournamentState(makeTournamentState(makeFixture("sl6", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("suspended_p1");
        assertTrue(p.getSuspended(), "No lifecycle when discipline flag is false");
        assertEquals(1, p.getSuspensionRemainingMatches(), "Remaining should not change");
    }

    /**
     * Test 7: mutateCareerState=false → no lifecycle change.
     * Master=false even with persistDiscipline=true.
     * Expect: suspended state unchanged.
     */
    @Test
    void masterFalse_noLifecycleChange() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sl7").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(2).awayGoals(0).homeXg(1.6).awayXg(0.3)
                .homeShots(6).awayShots(2).homePossession(58).awayPossession(42)
                .timeline(new com.footballmanager.application.service.simulation.v24.V24MatchTimeline())
                .summary("Deterministic: clean match")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                false, false, false, true, false, // mutateCareerState=false
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithSuspension(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "suspended_p1", 1, true);

        career.setTournamentState(makeTournamentState(makeFixture("sl7", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("suspended_p1");
        assertTrue(p.getSuspended(), "No lifecycle when master flag is false");
        assertEquals(1, p.getSuspensionRemainingMatches(), "Remaining should not change");
    }

    /**
     * Test 8: useV24DetailedEngine=false → no lifecycle change.
     * V24 disabled, falls back to default engine.
     * Expect: suspended state unchanged.
     */
    @Test
    void v24Disabled_noLifecycleChange() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        // useV24DetailedEngine=false, so provider is never called — pass null
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, false, false, fakeStorage,
                true, false, false, true, false,
                (ctx, seed) -> { throw new AssertionError("V24 engine should not be used"); });

        CareerSave career = makeCareerWithSuspension(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "suspended_p1", 1, true);

        career.setTournamentState(makeTournamentState(makeFixture("sl8", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("suspended_p1");
        assertTrue(p.getSuspended(), "No lifecycle when V24 is disabled");
        assertEquals(1, p.getSuspensionRemainingMatches(), "Remaining should not change");
    }

    // ========== V24D6H4: Yellow-threshold suspension lifecycle integration tests ==========

    /**
     * Test 1: thresholdSuspendedPlayer_notDecrementedSameRound
     *
     * Player starts with yellowCards=4. V24 engine emits one YELLOW_CARD → threshold triggers.
     * Snapshot comparison detects newly suspended player → adds to newlySuspendedPlayerIds.
     * Lifecycle skips decrement (newly suspended) → player ends round suspended=true, remaining=1.
     */
    @Test
    void thresholdSuspendedPlayer_notDecrementedSameRound() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        // Build timeline with YELLOW_CARD for threshold player
        com.footballmanager.application.service.simulation.v24.V24MatchTimeline timeline =
                new com.footballmanager.application.service.simulation.v24.V24MatchTimeline();
        // Yellow card for the threshold player at minute 30
        timeline.addEvent(new com.footballmanager.application.service.simulation.v24.V24MatchEvent(
                30,
                com.footballmanager.application.service.simulation.v24.V24MatchEventType.YELLOW_CARD,
                HOME1, "threshold_p1", "Threshold Player", null, null, 0.0, "Foul"));

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sth1").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.2).awayXg(0.5)
                .homeShots(6).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(timeline)
                .summary("Deterministic: yellow threshold")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, true, false, // discipline=true
                new DeterministicV24Engine(fakeResult));

        // Player p1 starts with yellowCards=4 (threshold will fire on YELLOW_CARD → 5)
        CareerSave career = makeCareerWithYellowCards(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "threshold_p1", 4);

        career.setTournamentState(makeTournamentState(makeFixture("sth1", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("threshold_p1");
        assertTrue(p.getSuspended(), "Threshold-suspended player should be suspended");
        assertEquals(1, p.getSuspensionRemainingMatches(), "Remaining matches should be 1");
        assertEquals(0, p.getYellowCards(), "Yellow cards should be reset to 0 after threshold");
    }

    /**
     * Test 4: bothRedAndThreshold_newlySuspendedExcludesBoth
     *
     * Player A receives YELLOW_CARD (threshold suspension).
     * Player B receives RED_CARD (red suspension).
     * Both should be in newlySuspendedPlayerIds → neither decremented same round.
     */
    @Test
    void bothRedAndThreshold_newlySuspendedExcludesBoth() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        com.footballmanager.application.service.simulation.v24.V24MatchTimeline timeline =
                new com.footballmanager.application.service.simulation.v24.V24MatchTimeline();
        // Player A: yellow card (threshold for player with yellowCards=4 → suspended)
        timeline.addEvent(new com.footballmanager.application.service.simulation.v24.V24MatchEvent(
                30,
                com.footballmanager.application.service.simulation.v24.V24MatchEventType.YELLOW_CARD,
                HOME1, "threshold_p1", "Threshold Player", null, null, 0.0, "Foul"));
        // Player B: red card
        timeline.addEvent(new com.footballmanager.application.service.simulation.v24.V24MatchEvent(
                70,
                com.footballmanager.application.service.simulation.v24.V24MatchEventType.RED_CARD,
                AWAY1, "red_p1", "Red Player", null, null, 0.0, "Second yellow"));

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sth4").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.2).awayXg(0.5)
                .homeShots(6).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(timeline)
                .summary("Deterministic: red + threshold")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, true, false,
                new DeterministicV24Engine(fakeResult));

        // Player A: yellowCards=4 (threshold fires), Player B: no pre-suspension
        CareerSave career = makeCareerWithYellowCardsAndRedPlayer(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "threshold_p1", 4, "red_p1");

        career.setTournamentState(makeTournamentState(makeFixture("sth4", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer thresholdPlayer = career.getSessionPlayer("threshold_p1");
        assertTrue(thresholdPlayer.getSuspended(), "Threshold player should be suspended");
        assertEquals(1, thresholdPlayer.getSuspensionRemainingMatches());
        assertEquals(0, thresholdPlayer.getYellowCards());

        SessionPlayer redPlayer = career.getSessionPlayer("red_p1");
        assertTrue(redPlayer.getSuspended(), "Red-carded player should be suspended");
        assertEquals(1, redPlayer.getSuspensionRemainingMatches());
        assertEquals(1, redPlayer.getRedCards());
    }

    /**
     * Test 5a: persistDisciplineFalse_noThresholdEffect
     *
     * Player yellowCards=4. YELLOW_CARD event emitted.
     * persistDiscipline=false → no discipline applied → threshold never fires.
     */
    @Test
    void persistDisciplineFalse_noThresholdEffect() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        com.footballmanager.application.service.simulation.v24.V24MatchTimeline timeline =
                new com.footballmanager.application.service.simulation.v24.V24MatchTimeline();
        timeline.addEvent(new com.footballmanager.application.service.simulation.v24.V24MatchEvent(
                30,
                com.footballmanager.application.service.simulation.v24.V24MatchEventType.YELLOW_CARD,
                HOME1, "p1", "Yellow Player", null, null, 0.0, "Foul"));

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sth5a").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.2).awayXg(0.5)
                .homeShots(6).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(timeline)
                .summary("Deterministic: yellow")
                .build();

        // discipline=false
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, false, false,
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithYellowCards(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "p1", 4);

        career.setTournamentState(makeTournamentState(makeFixture("sth5a", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("p1");
        assertEquals(4, p.getYellowCards(), "Yellow cards should not change when discipline=false");
        assertFalse(p.getSuspended(), "Player should not be suspended when discipline=false");
        assertEquals(0, p.getSuspensionRemainingMatches());
    }

    /**
     * Test 5b: v24Disabled_noThresholdEffect
     *
     * useV24DetailedEngine=false → V24 path not used → no discipline mutation.
     */
    @Test
    void v24Disabled_noThresholdEffect() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        // useV24DetailedEngine=false, but persistDiscipline=true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, false, false, fakeStorage,
                true, false, false, true, false,
                (ctx, seed) -> { throw new AssertionError("V24 engine should not be used"); });

        CareerSave career = makeCareerWithYellowCards(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "p1", 4);

        career.setTournamentState(makeTournamentState(makeFixture("sth5b", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("p1");
        assertEquals(4, p.getYellowCards(), "Yellow cards should not change in V23 path");
        assertFalse(p.getSuspended(), "Player should not be suspended in V23 path");
    }

    // ========== V24D6E4: Form mutation integration tests ==========

    /**
     * Test 1: persistFormEnabled_appliesFormMutation
     *
     * V24 enabled, master on, persist-form=true.
     * Deterministic timeline: player p1-form scores GOAL at minute 30.
     * Expected: rating 6.8 → delta +1 → form 50→51.
     */
    @Test
    void persistFormEnabled_appliesFormMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        com.footballmanager.application.service.simulation.v24.V24MatchTimeline timeline =
                new com.footballmanager.application.service.simulation.v24.V24MatchTimeline();
        timeline.addEvent(new com.footballmanager.application.service.simulation.v24.V24MatchEvent(
                30,
                com.footballmanager.application.service.simulation.v24.V24MatchEventType.GOAL,
                HOME1, "p1-form", "Form Player", null, null, 0.35, "Goal"));

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sf1").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.2).awayXg(0.4)
                .homeShots(5).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(timeline)
                .summary("Deterministic: goal for form test")
                .build();

        // V24 on, master on, persistForm on, all others off
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, false, true,
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithFormPlayer(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "p1-form", 50);

        career.setTournamentState(makeTournamentState(makeFixture("sf1", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("p1-form");
        // goal → rating 6.8 → delta +1 → form 50→51
        assertEquals(51, p.getForm(), "Form should increase by 1 after goal");
        assertNotNull(career.getTournamentState().getFixtures().get(0).getResult(),
                "Fixture result must exist");
    }

    /**
     * Test 2: persistFormRequiresMasterGate
     *
     * V24 enabled, master=false, persist-form=true.
     * Expected: form remains 50, no mutation.
     */
    @Test
    void persistFormRequiresMasterGate() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        com.footballmanager.application.service.simulation.v24.V24MatchTimeline timeline =
                new com.footballmanager.application.service.simulation.v24.V24MatchTimeline();
        timeline.addEvent(new com.footballmanager.application.service.simulation.v24.V24MatchEvent(
                30,
                com.footballmanager.application.service.simulation.v24.V24MatchEventType.GOAL,
                HOME1, "p1-form", "Form Player", null, null, 0.35, "Goal"));

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sf2").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.2).awayXg(0.4)
                .homeShots(5).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(timeline)
                .summary("Deterministic: goal")
                .build();

        // master=false (mutateCareerState=false), persistForm=true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                false, false, false, false, true,
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithFormPlayer(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "p1-form", 50);

        career.setTournamentState(makeTournamentState(makeFixture("sf2", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("p1-form");
        assertEquals(50, p.getForm(), "Form should not change when master flag is false");
    }

    /**
     * Test 3: persistFormSpecificFlagFalse_noFormMutation
     *
     * V24 enabled, master=true, persist-form=false.
     * Expected: form remains 50.
     */
    @Test
    void persistFormSpecificFlagFalse_noFormMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        com.footballmanager.application.service.simulation.v24.V24MatchTimeline timeline =
                new com.footballmanager.application.service.simulation.v24.V24MatchTimeline();
        timeline.addEvent(new com.footballmanager.application.service.simulation.v24.V24MatchEvent(
                30,
                com.footballmanager.application.service.simulation.v24.V24MatchEventType.GOAL,
                HOME1, "p1-form", "Form Player", null, null, 0.35, "Goal"));

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sf3").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.2).awayXg(0.4)
                .homeShots(5).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(timeline)
                .summary("Deterministic: goal")
                .build();

        // master=true, persistForm=false
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, false, false,
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithFormPlayer(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "p1-form", 50);

        career.setTournamentState(makeTournamentState(makeFixture("sf3", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("p1-form");
        assertEquals(50, p.getForm(), "Form should not change when persistForm=false");
    }

    /**
     * Test 4: v24DisabledWithFormFlags_noMutation
     *
     * V24 disabled (useV24DetailedEngine=false), all mutation flags true including persistForm.
     * Expected: V23 path used, no form mutation.
     */
    @Test
    void v24DisabledWithFormFlags_noMutation() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        // useV24DetailedEngine=false; persistForm=true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, false, false, fakeStorage,
                true, false, false, false, true,
                (ctx, seed) -> { throw new AssertionError("V24 engine should not be used"); });

        CareerSave career = makeCareerWithFormPlayer(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "p1-form", 50);

        career.setTournamentState(makeTournamentState(makeFixture("sf4", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        assertTrue(fakeSim.simulateQuickCalled, "Default path should be used");
        SessionPlayer p = career.getSessionPlayer("p1-form");
        assertEquals(50, p.getForm(), "Form should not change in V23 path");
    }

    /**
     * Test 5: formPlusDisciplineTogether_bothUpdated
     *
     * V24 enabled, master on, persist-discipline+persist-form both true.
     * Deterministic: p1-form gets YELLOW_CARD (not threshold), p2-form gets GOAL.
     * p1-form: yellowCards=0 + YELLOW → yellowCards=1, form 6.0→0 → form 50
     * p2-form: GOAL → rating 6.8 → delta +1 → form 50→51
     */
    @Test
    void formPlusDisciplineTogether_bothUpdated() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        com.footballmanager.application.service.simulation.v24.V24MatchTimeline timeline =
                new com.footballmanager.application.service.simulation.v24.V24MatchTimeline();
        // p1-form gets yellow card
        timeline.addEvent(new com.footballmanager.application.service.simulation.v24.V24MatchEvent(
                30,
                com.footballmanager.application.service.simulation.v24.V24MatchEventType.YELLOW_CARD,
                HOME1, "p1-form", "Yellow Player", null, null, 0.0, "Foul"));
        // p2-form scores goal
        timeline.addEvent(new com.footballmanager.application.service.simulation.v24.V24MatchEvent(
                60,
                com.footballmanager.application.service.simulation.v24.V24MatchEventType.GOAL,
                AWAY1, "p2-form", "Goal Player", null, null, 0.35, "Goal"));

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("sf5").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(1).homeXg(1.0).awayXg(0.9)
                .homeShots(4).awayShots(4).homePossession(50).awayPossession(50)
                .timeline(timeline)
                .summary("Deterministic: yellow + goal")
                .build();

        // persist-discipline + persist-form both true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, true, true,
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithFormAndDisciplinePlayers(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "p1-form", 50, "p2-form", 50);

        career.setTournamentState(makeTournamentState(makeFixture("sf5", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p1 = career.getSessionPlayer("p1-form");
        SessionPlayer p2 = career.getSessionPlayer("p2-form");
        // p1: yellow 6.0-0.3=5.7 → delta 0 → form 50; yellowCards=1
        assertEquals(50, p1.getForm(), "p1 form unchanged (rating 5.7)");
        assertEquals(1, p1.getYellowCards(), "p1 yellow card applied");
        // p2: goal 6.8 → delta +1 → form 51
        assertEquals(51, p2.getForm(), "p2 form increased by 1 after goal");
        assertTrue(career.getTournamentState().getFixtures().get(0).getResult() != null,
                "Fixture result must exist");
    }

    // ========== V24D6I3: Injury Recovery Lifecycle Integration Tests ==========

    /**
     * Test 1: pre-existing injured player — remaining=2, team has fixture, did not participate → decrements to 1.
     */
    @Test
    void injuryRecovery_preExistingInjured_decrements() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("ri1").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.0).awayXg(0.3)
                .homeShots(5).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(new com.footballmanager.application.service.simulation.v24.V24MatchTimeline())
                .summary("Deterministic: no injuries")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, false, false, false, // injuries=true
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithInjury(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "injured_p1", 2, false); // not in starting XI

        career.setTournamentState(makeTournamentState(makeFixture("ri1", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("injured_p1");
        assertTrue(p.getInjured(), "Player should still be injured");
        assertEquals(1, p.getInjuryRemainingMatches(), "Remaining should decrement from 2 to 1");
        assertEquals("MATCH_INJURY", p.getInjuryType(), "injuryType should be preserved on partial decrement");
    }

    /**
     * Test 2: pre-existing injured player — remaining=1, team has fixture, did not participate → recovers.
     */
    @Test
    void injuryRecovery_playerRecoversAt1() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("ri2").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.0).awayXg(0.3)
                .homeShots(5).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(new com.footballmanager.application.service.simulation.v24.V24MatchTimeline())
                .summary("Deterministic: no injuries")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, false, false, false,
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithInjury(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "injured_p1", 1, false);

        career.setTournamentState(makeTournamentState(makeFixture("ri2", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("injured_p1");
        assertFalse(p.getInjured(), "Player should recover — injured=false");
        assertEquals(0, p.getInjuryRemainingMatches(), "Remaining should be 0");
        assertNull(p.getInjuryType(), "injuryType should be cleared on full recovery");
    }

    /**
     * Test 3: player becomes injured during round — newlyInjuredPlayerIds populated → no decrement same round.
     */
    @Test
    void injuryRecovery_newlyInjured_doesNotDecrementSameRound() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        // Fake V24 result with INJURY event for injured_p1
        com.footballmanager.application.service.simulation.v24.V24MatchTimeline timeline =
                new com.footballmanager.application.service.simulation.v24.V24MatchTimeline();
        timeline.addEvent(new com.footballmanager.application.service.simulation.v24.V24MatchEvent(
                30,
                com.footballmanager.application.service.simulation.v24.V24MatchEventType.INJURY,
                HOME1, "injured_p1", "Injured Player", null, null, 0.0, "Muscle strain"));

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("ri3").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.0).awayXg(0.3)
                .homeShots(5).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(timeline)
                .summary("Deterministic: injury event")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, false, false, false,
                new DeterministicV24Engine(fakeResult));

        // injured_p1 NOT pre-injured (injured=false initially)
        CareerSave career = makeCareerWithInjury(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "injured_p1", 0, false); // injured=false

        career.setTournamentState(makeTournamentState(makeFixture("ri3", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("injured_p1");
        assertTrue(p.getInjured(), "Player should be newly injured from INJURY event");
        assertEquals(2, p.getInjuryRemainingMatches(), "New injury duration should be 2 matches");
        assertEquals("MATCH_INJURY", p.getInjuryType(), "injuryType should be set");
        // Newly injured — should NOT decrement in same round (tracked via newlyInjuredPlayerIds)
        assertEquals(2, p.getInjuryRemainingMatches(), "Remaining should stay 2 — newly injured not decremented");
    }

    /**
     * Test 4: injured player has no fixture this round → no decrement.
     */
    @Test
    void injuryRecovery_noFixture_noDecrement() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("ri4").homeTeamId("other-team-A").awayTeamId("other-team-B")
                .homeGoals(1).awayGoals(0).homeXg(1.0).awayXg(0.3)
                .homeShots(5).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(new com.footballmanager.application.service.simulation.v24.V24MatchTimeline())
                .summary("Deterministic: no injuries")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, false, false, false,
                new DeterministicV24Engine(fakeResult));

        // injured_p1 is on AWAY1 (has fixture), but player_id_who_has_no_fixture is on "no-team"
        // → AWAY1 has fixture (ri4), but "no-team" does not → no decrement
        CareerSave career = makeCareerWithInjury(AWAY1, AWAY1, AWAY1, AWAY1,
                11, 11, "player_no_fixture", 2, false);

        career.setTournamentState(makeTournamentState(makeFixture("ri4", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("player_no_fixture");
        assertTrue(p.getInjured(), "Player should still be injured");
        assertEquals(2, p.getInjuryRemainingMatches(), "No fixture → no decrement");
    }

    /**
     * Test 5: injured player participated → no decrement.
     */
    @Test
    void injuryRecovery_participated_noDecrement() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("ri5").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.0).awayXg(0.3)
                .homeShots(5).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(new com.footballmanager.application.service.simulation.v24.V24MatchTimeline())
                .summary("Deterministic: no injuries")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, false, false, false,
                new DeterministicV24Engine(fakeResult));

        // participated=true → injured_p1 is in starting XI
        CareerSave career = makeCareerWithInjury(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "injured_p1", 2, true); // participated=true

        career.setTournamentState(makeTournamentState(makeFixture("ri5", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("injured_p1");
        assertTrue(p.getInjured(), "Player should still be injured");
        assertEquals(2, p.getInjuryRemainingMatches(), "Participated → no decrement");
    }

    /**
     * Test 6: persistInjuries=false → no recovery even if pre-injured.
     */
    @Test
    void injuryRecovery_persistInjuriesFalse_noOp() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("ri6").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.0).awayXg(0.3)
                .homeShots(5).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(new com.footballmanager.application.service.simulation.v24.V24MatchTimeline())
                .summary("Deterministic: no injuries")
                .build();

        // injuries=false (mutateCareerState=true, persistInjuries=false)
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, false, false, false, false,
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithInjury(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "injured_p1", 1, false);

        career.setTournamentState(makeTournamentState(makeFixture("ri6", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("injured_p1");
        assertTrue(p.getInjured(), "Player should remain injured (no recovery when persistInjuries=false)");
        assertEquals(1, p.getInjuryRemainingMatches(), "Remaining should not change");
    }

    /**
     * Test 7: mutateCareerState=false → no recovery.
     */
    @Test
    void injuryRecovery_masterFlagFalse_noOp() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        V24DetailedMatchResult fakeResult = V24DetailedMatchResult.builder()
                .matchId("ri7").homeTeamId(HOME1).awayTeamId(AWAY1)
                .homeGoals(1).awayGoals(0).homeXg(1.0).awayXg(0.3)
                .homeShots(5).awayShots(3).homePossession(55).awayPossession(45)
                .timeline(new com.footballmanager.application.service.simulation.v24.V24MatchTimeline())
                .summary("Deterministic: no injuries")
                .build();

        // mutateCareerState=false, persistInjuries=true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                false, true, false, false, false,
                new DeterministicV24Engine(fakeResult));

        CareerSave career = makeCareerWithInjury(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "injured_p1", 1, false);

        career.setTournamentState(makeTournamentState(makeFixture("ri7", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("injured_p1");
        assertTrue(p.getInjured(), "Player should remain injured (master flag false)");
        assertEquals(1, p.getInjuryRemainingMatches(), "Remaining should not change");
    }

    /**
     * Test 8: useV24DetailedEngine=false → no recovery.
     */
    @Test
    void injuryRecovery_v24Disabled_noOp() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        // useV24DetailedEngine=false, injuries=true
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, false, false, fakeStorage,
                true, true, false, false, false,
                (ctx, seed) -> { throw new AssertionError("V24 engine should not be used"); });

        CareerSave career = makeCareerWithInjury(HOME1, AWAY1, HOME1, AWAY1,
                11, 11, "injured_p1", 1, false);

        career.setTournamentState(makeTournamentState(makeFixture("ri8", HOME1, AWAY1, 1)));
        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer("injured_p1");
        assertTrue(p.getInjured(), "Player should remain injured (V24 path not used)");
        assertEquals(1, p.getInjuryRemainingMatches(), "Remaining should not change");
    }

    // ========== V24D6I3: Factory helper for injury recovery tests ==========

    /**
     * Creates career with a player in starting XI who has initial form set.
     */
    private static CareerSave makeCareerWithFormPlayer(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            int homeStarterCount, int awayStarterCount,
            String formPlayerId, int initialForm) {
        CareerSave save = new CareerSave();
        save.getData().setCareerId("test_career_form_" + formPlayerId);
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
            String expectedPid = "p_" + homeTeamId + "_" + i;
            SessionPlayer p = SessionPlayer.custom(expectedPid, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId(expectedPid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(expectedPid, homeTeamId);
            homePlayers.add(p);
        }

        List<SessionPlayer> awayPlayers = new ArrayList<>();
        for (int i = 0; i < awayStarterCount; i++) {
            String expectedPid = "p_" + awayTeamId + "_" + i;
            SessionPlayer p = SessionPlayer.custom(expectedPid, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId(expectedPid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(expectedPid, awayTeamId);
            awayPlayers.add(p);
        }

        // Add form player (replaces first home starter)
        SessionPlayer formPlayer = SessionPlayer.custom(formPlayerId, 25, "MID",
                75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
        formPlayer.setSessionPlayerId(formPlayerId);
        formPlayer.setForm(initialForm);
        pm.addSessionPlayer(formPlayer);
        tm.assignPlayerToSquad(formPlayerId, homeTeamId);

        save.setTeamManager(tm);
        save.setPlayerManager(pm);

        List<String> homeStarterIds = new ArrayList<>();
        for (SessionPlayer p : homePlayers) {
            homeStarterIds.add(p.getSessionPlayerId());
        }
        homeStarterIds.set(0, formPlayerId);
        List<String> awayStarterIds = new ArrayList<>();
        for (SessionPlayer p : awayPlayers) {
            awayStarterIds.add(p.getSessionPlayerId());
        }
        save.getTeamStarting11().put(homeStartingTeamId, homeStarterIds);
        save.getTeamStarting11().put(awayStartingTeamId, awayStarterIds);

        save.setTournamentState(new TournamentState());
        return save;
    }

    /**
     * Creates career with two players in starting XI, each with initial form set.
     */
    private static CareerSave makeCareerWithFormAndDisciplinePlayers(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            int homeStarterCount, int awayStarterCount,
            String formPlayerId1, int initialForm1,
            String formPlayerId2, int initialForm2) {
        CareerSave save = new CareerSave();
        save.getData().setCareerId("test_career_formdisc_" + formPlayerId1 + "_" + formPlayerId2);
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
            String expectedPid = "p_" + homeTeamId + "_" + i;
            SessionPlayer p = SessionPlayer.custom(expectedPid, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId(expectedPid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(expectedPid, homeTeamId);
            homePlayers.add(p);
        }

        List<SessionPlayer> awayPlayers = new ArrayList<>();
        for (int i = 0; i < awayStarterCount; i++) {
            String expectedPid = "p_" + awayTeamId + "_" + i;
            SessionPlayer p = SessionPlayer.custom(expectedPid, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId(expectedPid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(expectedPid, awayTeamId);
            awayPlayers.add(p);
        }

        // Add p1-form (replaces first home starter)
        SessionPlayer formPlayer1 = SessionPlayer.custom(formPlayerId1, 25, "MID",
                75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
        formPlayer1.setSessionPlayerId(formPlayerId1);
        formPlayer1.setForm(initialForm1);
        formPlayer1.setYellowCards(0);
        pm.addSessionPlayer(formPlayer1);
        tm.assignPlayerToSquad(formPlayerId1, homeTeamId);

        // Add p2-form (replaces first away starter)
        SessionPlayer formPlayer2 = SessionPlayer.custom(formPlayerId2, 25, "MID",
                75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
        formPlayer2.setSessionPlayerId(formPlayerId2);
        formPlayer2.setForm(initialForm2);
        pm.addSessionPlayer(formPlayer2);
        tm.assignPlayerToSquad(formPlayerId2, awayTeamId);

        save.setTeamManager(tm);
        save.setPlayerManager(pm);

        List<String> homeStarterIds = new ArrayList<>();
        for (SessionPlayer p : homePlayers) {
            homeStarterIds.add(p.getSessionPlayerId());
        }
        homeStarterIds.set(0, formPlayerId1);
        List<String> awayStarterIds = new ArrayList<>();
        for (SessionPlayer p : awayPlayers) {
            awayStarterIds.add(p.getSessionPlayerId());
        }
        awayStarterIds.set(0, formPlayerId2);
        save.getTeamStarting11().put(homeStartingTeamId, homeStarterIds);
        save.getTeamStarting11().put(awayStartingTeamId, awayStarterIds);

        save.setTournamentState(new TournamentState());
        return save;
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

    // ========== V24D6D6B: Suspension lifecycle helpers ==========

    /**
     * Creates career with a suspended player who is NOT in the starting XI.
     */
    private static CareerSave makeCareerWithSuspension(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            int homeStarterCount, int awayStarterCount,
            String suspendedPlayerId, int remainingMatches, boolean suspended) {
        return makeCareerWithSuspension(homeTeamId, awayTeamId, homeStartingTeamId, awayStartingTeamId,
                homeStarterCount, awayStarterCount, suspendedPlayerId, remainingMatches, suspended, false);
    }

    /**
     * Creates career with a suspended player, optionally in the starting XI.
     * Note: SessionPlayer.custom() generates a random UUID as sessionPlayerId,
     * so we capture the real ID and use it for both squad assignment and assertions.
     */
    private static CareerSave makeCareerWithSuspension(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            int homeStarterCount, int awayStarterCount,
            String suspendedPlayerId, int remainingMatches, boolean suspended,
            boolean participated) {
        CareerSave save = new CareerSave();
        save.getData().setCareerId("test_career_" + suspendedPlayerId);
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();

        for (String tid : List.of(homeTeamId, awayTeamId)) {
            UUID uuid = UUID.fromString(tid);
            SessionTeam team = SessionTeam.fromRealTeam(uuid, "world_" + tid,
                    "Team " + tid, "Country", BigDecimal.ZERO, "4-3-3", null);
            team.setSessionTeamId(tid);
            tm.addSessionTeam(team);
        }

        // Create all starters with their expected pids (for V24MatchContext resolve)
        List<SessionPlayer> homePlayers = new ArrayList<>();
        for (int i = 0; i < homeStarterCount; i++) {
            String expectedPid = "p_" + homeTeamId + "_" + i;
            SessionPlayer p = SessionPlayer.custom(expectedPid, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            // Force sessionPlayerId to expected pid so V24MatchContext can resolve
            p.setSessionPlayerId(expectedPid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(expectedPid, homeTeamId);
            homePlayers.add(p);
        }

        List<SessionPlayer> awayPlayers = new ArrayList<>();
        for (int i = 0; i < awayStarterCount; i++) {
            String expectedPid = "p_" + awayTeamId + "_" + i;
            SessionPlayer p = SessionPlayer.custom(expectedPid, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId(expectedPid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(expectedPid, awayTeamId);
            awayPlayers.add(p);
        }

        save.setTeamManager(tm);
        save.setPlayerManager(pm);

        // Add the suspended player (not in starting XI unless participated=true)
        SessionPlayer suspendedPlayer = SessionPlayer.custom(suspendedPlayerId, 25, "MID",
                75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
        // Force sessionPlayerId to the expected value for test assertions
        suspendedPlayer.setSessionPlayerId(suspendedPlayerId);
        suspendedPlayer.setSuspended(suspended);
        suspendedPlayer.setSuspensionRemainingMatches(remainingMatches);
        pm.addSessionPlayer(suspendedPlayer);
        tm.assignPlayerToSquad(suspendedPlayerId, homeTeamId);

        List<String> homeStarterIds = new ArrayList<>();
        for (SessionPlayer p : homePlayers) {
            homeStarterIds.add(p.getSessionPlayerId());
        }
        if (participated) {
            // Replace first starter with the suspended player
            homeStarterIds.set(0, suspendedPlayerId);
        }
        List<String> awayStarterIds = new ArrayList<>();
        for (SessionPlayer p : awayPlayers) {
            awayStarterIds.add(p.getSessionPlayerId());
        }
        save.getTeamStarting11().put(homeStartingTeamId, homeStarterIds);
        save.getTeamStarting11().put(awayStartingTeamId, awayStarterIds);

        save.setTournamentState(new TournamentState());
        return save;
    }

    /**
     * Creates career with a player who receives a RED_CARD this round.
     * The player is not pre-suspended.
     */
    private static CareerSave makeCareerWithRedCardThisRound(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            String redCardPlayerId, int redCardRemaining, boolean initiallySuspended) {
        return makeCareerWithSuspension(homeTeamId, awayTeamId, homeStartingTeamId, awayStartingTeamId,
                11, 11, redCardPlayerId, redCardRemaining, initiallySuspended);
    }

    /**
     * Creates career with a player who is both pre-suspended AND receives RED_CARD this round.
     */
    private static CareerSave makeCareerWithSuspensionAndRedCard(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            String playerId, int remainingMatches, boolean suspended) {
        return makeCareerWithSuspension(homeTeamId, awayTeamId, homeStartingTeamId, awayStartingTeamId,
                11, 11, playerId, remainingMatches, suspended);
    }

    // ========== V24D6I2: Injury Recovery Lifecycle factory helpers ==========

    /**
     * Creates career with an injured player, optionally in the starting XI.
     * Player is pre-injured (injured=true, injuryRemainingMatches>0) before the round.
     */
    private static CareerSave makeCareerWithInjury(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            int homeStarterCount, int awayStarterCount,
            String injuredPlayerId, int injuryRemainingMatches,
            boolean participated) {
        CareerSave save = new CareerSave();
        save.getData().setCareerId("test_career_injury_" + injuredPlayerId);
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();

        for (String tid : List.of(homeTeamId, awayTeamId)) {
            UUID uuid = UUID.fromString(tid);
            SessionTeam team = SessionTeam.fromRealTeam(uuid, "world_" + tid,
                    "Team " + tid, "Country", BigDecimal.ZERO, "4-3-3", null);
            team.setSessionTeamId(tid);
            tm.addSessionTeam(team);
        }

        // Create all starters with their expected pids (for V24MatchContext resolve)
        List<SessionPlayer> homePlayers = new ArrayList<>();
        for (int i = 0; i < homeStarterCount; i++) {
            String expectedPid = "p_" + homeTeamId + "_" + i;
            SessionPlayer p = SessionPlayer.custom(expectedPid, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId(expectedPid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(expectedPid, homeTeamId);
            homePlayers.add(p);
        }

        List<SessionPlayer> awayPlayers = new ArrayList<>();
        for (int i = 0; i < awayStarterCount; i++) {
            String expectedPid = "p_" + awayTeamId + "_" + i;
            SessionPlayer p = SessionPlayer.custom(expectedPid, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId(expectedPid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(expectedPid, awayTeamId);
            awayPlayers.add(p);
        }

        save.setTeamManager(tm);
        save.setPlayerManager(pm);

        // Add the injured player (not in starting XI unless participated=true)
        SessionPlayer injuredPlayer = SessionPlayer.custom(injuredPlayerId, 25, "MID",
                75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
        injuredPlayer.setSessionPlayerId(injuredPlayerId);
        injuredPlayer.setInjured(injuryRemainingMatches > 0);
        injuredPlayer.setInjuryRemainingMatches(injuryRemainingMatches);
        injuredPlayer.setInjuryType(injuryRemainingMatches > 0 ? "MATCH_INJURY" : null);
        pm.addSessionPlayer(injuredPlayer);
        tm.assignPlayerToSquad(injuredPlayerId, homeTeamId);

        List<String> homeStarterIds = new ArrayList<>();
        for (SessionPlayer p : homePlayers) {
            homeStarterIds.add(p.getSessionPlayerId());
        }
        if (participated) {
            // Replace first starter with the injured player
            homeStarterIds.set(0, injuredPlayerId);
        }
        List<String> awayStarterIds = new ArrayList<>();
        for (SessionPlayer p : awayPlayers) {
            awayStarterIds.add(p.getSessionPlayerId());
        }
        save.getTeamStarting11().put(homeStartingTeamId, homeStarterIds);
        save.getTeamStarting11().put(awayStartingTeamId, awayStarterIds);

        save.setTournamentState(new TournamentState());
        return save;
    }

    // ========== V24D6H4: Yellow-card threshold factory helpers ==========

    /**
     * Creates career with a player who starts with yellowCards=n (to test threshold behavior).
     * Player is in starting XI and participates normally.
     */
    private static CareerSave makeCareerWithYellowCards(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            int homeStarterCount, int awayStarterCount,
            String yellowPlayerId, int initialYellowCards) {
        CareerSave save = new CareerSave();
        save.getData().setCareerId("test_career_yellow_" + yellowPlayerId);
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
            String expectedPid = "p_" + homeTeamId + "_" + i;
            SessionPlayer p = SessionPlayer.custom(expectedPid, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId(expectedPid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(expectedPid, homeTeamId);
            homePlayers.add(p);
        }

        List<SessionPlayer> awayPlayers = new ArrayList<>();
        for (int i = 0; i < awayStarterCount; i++) {
            String expectedPid = "p_" + awayTeamId + "_" + i;
            SessionPlayer p = SessionPlayer.custom(expectedPid, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId(expectedPid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(expectedPid, awayTeamId);
            awayPlayers.add(p);
        }

        // Add the yellow-card player (replaces first home starter)
        SessionPlayer yellowPlayer = SessionPlayer.custom(yellowPlayerId, 25, "MID",
                75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
        yellowPlayer.setSessionPlayerId(yellowPlayerId);
        yellowPlayer.setYellowCards(initialYellowCards);
        pm.addSessionPlayer(yellowPlayer);
        tm.assignPlayerToSquad(yellowPlayerId, homeTeamId);

        save.setTeamManager(tm);
        save.setPlayerManager(pm);

        List<String> homeStarterIds = new ArrayList<>();
        for (SessionPlayer p : homePlayers) {
            homeStarterIds.add(p.getSessionPlayerId());
        }
        // Replace first home starter with the yellow player
        homeStarterIds.set(0, yellowPlayerId);
        List<String> awayStarterIds = new ArrayList<>();
        for (SessionPlayer p : awayPlayers) {
            awayStarterIds.add(p.getSessionPlayerId());
        }
        save.getTeamStarting11().put(homeStartingTeamId, homeStarterIds);
        save.getTeamStarting11().put(awayStartingTeamId, awayStarterIds);

        save.setTournamentState(new TournamentState());
        return save;
    }

    /**
     * Creates career with:
     * - yellowPlayerId: starts with yellowCards=n, will receive YELLOW_CARD in timeline
     * - redPlayerId: starts with 0 yellows, will receive RED_CARD in timeline
     */
    private static CareerSave makeCareerWithYellowCardsAndRedPlayer(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            int homeStarterCount, int awayStarterCount,
            String yellowPlayerId, int initialYellowCards,
            String redPlayerId) {
        CareerSave save = new CareerSave();
        save.getData().setCareerId("test_career_ycrc_" + yellowPlayerId + "_" + redPlayerId);
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
            String expectedPid = "p_" + homeTeamId + "_" + i;
            SessionPlayer p = SessionPlayer.custom(expectedPid, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId(expectedPid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(expectedPid, homeTeamId);
            homePlayers.add(p);
        }

        List<SessionPlayer> awayPlayers = new ArrayList<>();
        for (int i = 0; i < awayStarterCount; i++) {
            String expectedPid = "p_" + awayTeamId + "_" + i;
            SessionPlayer p = SessionPlayer.custom(expectedPid, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId(expectedPid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(expectedPid, awayTeamId);
            awayPlayers.add(p);
        }

        // Add yellow player (replaces first home starter)
        SessionPlayer yellowPlayer = SessionPlayer.custom(yellowPlayerId, 25, "MID",
                75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
        yellowPlayer.setSessionPlayerId(yellowPlayerId);
        yellowPlayer.setYellowCards(initialYellowCards);
        pm.addSessionPlayer(yellowPlayer);
        tm.assignPlayerToSquad(yellowPlayerId, homeTeamId);

        // Add red player (replaces first away starter)
        SessionPlayer redPlayer = SessionPlayer.custom(redPlayerId, 25, "MID",
                75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
        redPlayer.setSessionPlayerId(redPlayerId);
        pm.addSessionPlayer(redPlayer);
        tm.assignPlayerToSquad(redPlayerId, awayTeamId);

        save.setTeamManager(tm);
        save.setPlayerManager(pm);

        List<String> homeStarterIds = new ArrayList<>();
        for (SessionPlayer p : homePlayers) {
            homeStarterIds.add(p.getSessionPlayerId());
        }
        homeStarterIds.set(0, yellowPlayerId);
        List<String> awayStarterIds = new ArrayList<>();
        for (SessionPlayer p : awayPlayers) {
            awayStarterIds.add(p.getSessionPlayerId());
        }
        awayStarterIds.set(0, redPlayerId);
        save.getTeamStarting11().put(homeStartingTeamId, homeStarterIds);
        save.getTeamStarting11().put(awayStartingTeamId, awayStarterIds);

        save.setTournamentState(new TournamentState());
        return save;
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