package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.v24.LiveRoundMutationTracking;
import com.footballmanager.application.service.simulation.v24.V24CareerMutationPolicy;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngineProvider;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.application.service.simulation.v24.V24MatchTimeline;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6R2 — Live-path end-of-round lifecycle decrement integration tests.
 *
 * <p>Exercises {@code LeagueSimulator.applyEndOfRoundLiveLifecycle} directly with
 * an in-memory {@link CareerSave} and a pre-populated {@link LiveRoundMutationTracking}.
 * These tests do not mock the {@code RoundController} (would be brittle) — they
 * cover the new helper and the live tracking contract.
 *
 * <p>Located in package {@code com.footballmanager.application.service.simulation}
 * to access the package-private 12-arg {@link LeagueSimulator} constructor.
 */
class V24LivePathEndOfRoundLifecycleIntegrationTest {

    private static final String HOME = "11111111-1111-1111-1111-111111111111";
    private static final String AWAY = "22222222-2222-2222-2222-222222222222";

    // ========== T1: pre-existing injured decrements once at live round end ==========

    @Test
    void preExistingInjured_decrementsOnceAtLiveRoundEnd() {
        String p1 = "p-inj-pre";
        LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(1, 1);
        tracking.preRoundInjuredPlayerIds.add(p1);

        CareerSave career = newCareer();
        addPlayer(career, p1, HOME);
        // Pre-existing injured state
        SessionPlayer p = career.getPlayerManager().getSessionPlayer(p1);
        p.setInjured(true);
        p.setInjuryRemainingMatches(2);
        p.setInjuryType("MATCH_INJURY");

        // Fixture for round 1
        career.getTournamentState().getFixtures().add(new MatchFixture("m-r1", HOME, AWAY, 1));
        career.getTournamentState().setCurrentRound(1);

        LeagueSimulator simulator = newSimulator(true, true, false, false, false);

        simulator.applyEndOfRoundLiveLifecycle(career, 1,
                career.getTournamentState().getFixtures(), tracking);

        assertTrue(p.getInjured(), "After 1 decrement from remaining=2: injured remains true");
        assertEquals(1, p.getInjuryRemainingMatches(),
                "After 1 decrement: injuryRemainingMatches=1");
    }

    // ========== T2: newly injured does NOT decrement same round ==========

    @Test
    void newlyInjured_doesNotDecrementSameRound() {
        String p1 = "p-inj-pre";   // pre-injured, remaining=2
        String p2 = "p-inj-new";   // newly injured in this round, remaining=2
        LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(1, 1);
        tracking.preRoundInjuredPlayerIds.add(p1);
        tracking.newlyInjuredPlayerIds.add(p2);

        CareerSave career = newCareer();
        addPlayer(career, p1, HOME);
        addPlayer(career, p2, HOME);
        career.getPlayerManager().getSessionPlayer(p1).setInjured(true);
        career.getPlayerManager().getSessionPlayer(p1).setInjuryRemainingMatches(2);
        career.getPlayerManager().getSessionPlayer(p2).setInjured(true);
        career.getPlayerManager().getSessionPlayer(p2).setInjuryRemainingMatches(2);

        career.getTournamentState().getFixtures().add(new MatchFixture("m-r1", HOME, AWAY, 1));

        LeagueSimulator simulator = newSimulator(true, true, false, false, false);

        simulator.applyEndOfRoundLiveLifecycle(career, 1,
                career.getTournamentState().getFixtures(), tracking);

        SessionPlayer pre = career.getPlayerManager().getSessionPlayer(p1);
        SessionPlayer newly = career.getPlayerManager().getSessionPlayer(p2);

        assertEquals(1, pre.getInjuryRemainingMatches(),
                "pre-injured p1 must decrement (2 → 1)");
        assertEquals(2, newly.getInjuryRemainingMatches(),
                "newly injured p2 must NOT decrement same-round");
    }

    // ========== T3: pre-existing suspended decrements once ==========

    @Test
    void preExistingSuspended_decrementsOnceAtLiveRoundEnd() {
        String p1 = "p-susp-pre";
        LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(1, 1);
        tracking.preRoundSuspendedPlayerIds.add(p1);

        CareerSave career = newCareer();
        addPlayer(career, p1, HOME);
        SessionPlayer p = career.getPlayerManager().getSessionPlayer(p1);
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);

        career.getTournamentState().getFixtures().add(new MatchFixture("m-r1", HOME, AWAY, 1));

        LeagueSimulator simulator = newSimulator(true, false, false, true, false);

        simulator.applyEndOfRoundLiveLifecycle(career, 1,
                career.getTournamentState().getFixtures(), tracking);

        assertFalse(p.getSuspended(),
                "Pre-suspended with remaining=1 and no participation: suspended must clear");
        assertEquals(0, p.getSuspensionRemainingMatches(),
                "Pre-suspended with remaining=1: remaining=0 after decrement");
    }

    // ========== T4: newly suspended does NOT decrement same round ==========

    @Test
    void newlySuspended_doesNotDecrementSameRound() {
        String p1 = "p-susp-pre";   // pre-suspended, remaining=1
        String p2 = "p-susp-new";   // newly suspended this round, remaining=1
        LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(1, 1);
        tracking.preRoundSuspendedPlayerIds.add(p1);
        tracking.newlySuspendedPlayerIds.add(p2);

        CareerSave career = newCareer();
        addPlayer(career, p1, HOME);
        addPlayer(career, p2, HOME);
        career.getPlayerManager().getSessionPlayer(p1).setSuspended(true);
        career.getPlayerManager().getSessionPlayer(p1).setSuspensionRemainingMatches(1);
        career.getPlayerManager().getSessionPlayer(p2).setSuspended(true);
        career.getPlayerManager().getSessionPlayer(p2).setSuspensionRemainingMatches(1);

        career.getTournamentState().getFixtures().add(new MatchFixture("m-r1", HOME, AWAY, 1));

        LeagueSimulator simulator = newSimulator(true, false, false, true, false);

        simulator.applyEndOfRoundLiveLifecycle(career, 1,
                career.getTournamentState().getFixtures(), tracking);

        SessionPlayer pre = career.getPlayerManager().getSessionPlayer(p1);
        SessionPlayer newly = career.getPlayerManager().getSessionPlayer(p2);

        assertFalse(pre.getSuspended(), "pre-suspended p1 must clear (remaining 1 → 0)");
        assertEquals(0, pre.getSuspensionRemainingMatches());
        assertTrue(newly.getSuspended(), "newly suspended p2 must NOT decrement same-round");
        assertEquals(1, newly.getSuspensionRemainingMatches(),
                "newly suspended p2 stays at remaining=1");
    }

    // ========== T5: participated player does not recover or serve ==========

    @Test
    void participatedPlayerDoesNotRecoverOrServe() {
        String p1 = "p-susp-inj-participating";
        LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(1, 1);
        tracking.preRoundSuspendedPlayerIds.add(p1);
        tracking.preRoundInjuredPlayerIds.add(p1);
        tracking.participatedPlayerIds.add(p1);

        CareerSave career = newCareer();
        addPlayer(career, p1, HOME);
        SessionPlayer p = career.getPlayerManager().getSessionPlayer(p1);
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);
        p.setInjured(true);
        p.setInjuryRemainingMatches(2);
        p.setInjuryType("MATCH_INJURY");

        career.getTournamentState().getFixtures().add(new MatchFixture("m-r1", HOME, AWAY, 1));

        LeagueSimulator simulator = newSimulator(true, true, false, true, false);

        simulator.applyEndOfRoundLiveLifecycle(career, 1,
                career.getTournamentState().getFixtures(), tracking);

        assertTrue(p.getSuspended(), "Participated: suspended stays true");
        assertEquals(1, p.getSuspensionRemainingMatches(), "Participated: no suspension decrement");
        assertTrue(p.getInjured(), "Participated: injured stays true");
        assertEquals(2, p.getInjuryRemainingMatches(), "Participated: no injury recovery");
    }

    // ========== T6: no double decrement when multiple matches finish ==========

    @Test
    void noDoubleDecrementWhenMultipleMatchesFinish() {
        // Player is suspended remaining=2; participates in none of 6 matches;
        // applyEndOfRoundLiveLifecycle is called once (not 6 times).
        String p1 = "p-no-double";
        LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(1, 1);
        tracking.preRoundSuspendedPlayerIds.add(p1);

        CareerSave career = newCareer();
        addPlayer(career, p1, HOME);
        SessionPlayer p = career.getPlayerManager().getSessionPlayer(p1);
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(2);

        // 6 fixtures (live round)
        for (int i = 0; i < 6; i++) {
            String home = i % 2 == 0 ? HOME : AWAY;
            String away = i % 2 == 0 ? AWAY : HOME;
            career.getTournamentState().getFixtures()
                    .add(new MatchFixture("m-" + i, home, away, 1));
        }

        LeagueSimulator simulator = newSimulator(true, false, false, true, false);

        // Called ONCE per round
        simulator.applyEndOfRoundLiveLifecycle(career, 1,
                career.getTournamentState().getFixtures(), tracking);

        assertEquals(1, p.getSuspensionRemainingMatches(),
                "Single decrement (2 → 1), not 6 decrements (2 → 0)");
    }

    // ========== T7: flags false → no lifecycle mutation ==========

    @Test
    void flagsFalse_noLifecycleMutation() {
        String p1 = "p-flags-off";
        LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(1, 1);
        tracking.preRoundSuspendedPlayerIds.add(p1);
        tracking.preRoundInjuredPlayerIds.add(p1);

        CareerSave career = newCareer();
        addPlayer(career, p1, HOME);
        SessionPlayer p = career.getPlayerManager().getSessionPlayer(p1);
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);
        p.setInjured(true);
        p.setInjuryRemainingMatches(2);
        p.setInjuryType("MATCH_INJURY");

        career.getTournamentState().getFixtures().add(new MatchFixture("m-r1", HOME, AWAY, 1));

        // mutateCareerState=false
        LeagueSimulator simulator = newSimulator(false, false, false, false, false);

        simulator.applyEndOfRoundLiveLifecycle(career, 1,
                career.getTournamentState().getFixtures(), tracking);

        // No state change
        assertTrue(p.getSuspended(), "flags=false: suspended unchanged");
        assertEquals(1, p.getSuspensionRemainingMatches());
        assertTrue(p.getInjured(), "flags=false: injured unchanged");
        assertEquals(2, p.getInjuryRemainingMatches());
    }

    // ========== T8: in-place mutation persists via existing career save flow ==========

    @Test
    void inPlaceMutationPersistsViaExistingCareerSaveFlow() {
        String p1 = "p-in-place";
        LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(1, 1);
        tracking.preRoundSuspendedPlayerIds.add(p1);

        CareerSave career = newCareer();
        addPlayer(career, p1, HOME);
        SessionPlayer p = career.getPlayerManager().getSessionPlayer(p1);
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);

        career.getTournamentState().getFixtures().add(new MatchFixture("m-r1", HOME, AWAY, 1));

        LeagueSimulator simulator = newSimulator(true, false, false, true, false);

        // Same CareerSave instance is mutated
        CareerSave sameRef = career;
        simulator.applyEndOfRoundLiveLifecycle(career, 1,
                career.getTournamentState().getFixtures(), tracking);

        assertSame(sameRef, career, "CareerSave reference must be unchanged");
        assertFalse(career.getPlayerManager().getSessionPlayer(p1).getSuspended(),
                "In-place mutation must be visible via the same CareerSave reference");
    }

    // ========== Helpers ==========

    private static CareerSave newCareer() {
        CareerSave save = new CareerSave();
        save.getData().setCareerId("test_v24d6r2_" + UUID.randomUUID());
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();
        for (String tid : List.of(HOME, AWAY)) {
            UUID uuid = UUID.fromString(tid);
            SessionTeam team = SessionTeam.fromRealTeam(
                    uuid, "world_" + tid, "Team " + tid,
                    "Country", BigDecimal.ZERO, "4-3-3", null);
            team.setSessionTeamId(tid);
            tm.addSessionTeam(team);
        }
        save.setTeamManager(tm);
        save.setPlayerManager(pm);
        save.setTournamentState(new TournamentState());
        return save;
    }

    private static void addPlayer(CareerSave career, String playerId, String teamId) {
        SessionPlayer p = SessionPlayer.custom(
                playerId, 25, "MID", 70, 70, 70, 70, 70, 70,
                BigDecimal.valueOf(1000));
        p.setSessionPlayerId(playerId);
        career.getPlayerManager().addSessionPlayer(p);
        career.getTeamManager().assignPlayerToSquad(playerId, teamId);
    }

    private static LeagueSimulator newSimulator(boolean mutateCareerState,
                                                 boolean persistInjuries,
                                                 boolean persistFatigue,
                                                 boolean persistDiscipline,
                                                 boolean persistForm) {
        // V24 path engine and storage are required by persistV24DetailForLiveMatch,
        // but applyEndOfRoundLiveLifecycle does not call into them. Using fakes.
        return new LeagueSimulator(
                new FakeMatchSimulator(),
                null,
                false,                 // useV23
                true,                  // useV24
                false,                 // persistDetail
                new FakeStoragePort(),
                mutateCareerState,
                persistInjuries,
                persistFatigue,
                persistDiscipline,
                persistForm
        );
    }

    // ========== Fakes ==========

    private static class FakeStoragePort implements V24DetailedMatchStoragePort {
        @Override
        public void save(String careerId, com.footballmanager.application.service.simulation.v24.V24DetailedMatchData detail) { /* no-op */ }
        @Override
        public java.util.Optional<com.footballmanager.application.service.simulation.v24.V24DetailedMatchData> findByMatchId(String careerId, String matchId) {
            return java.util.Optional.empty();
        }
        @Override
        public java.util.List<com.footballmanager.application.service.simulation.v24.V24DetailedMatchData> findByCareerId(String careerId) {
            return java.util.List.of();
        }
        @Override
        public void deleteByCareerId(String careerId) { /* no-op */ }
    }

    private static class FakeMatchSimulator implements MatchSimulator {
        @Override
        public MatchState simulateReal(MatchState state, int toMinute) { return state; }
        @Override
        public MatchResult simulateQuick(String homeTeamId, String awayTeamId, int homeOvr, int awayOvr) {
            throw new UnsupportedOperationException();
        }
    }
}
