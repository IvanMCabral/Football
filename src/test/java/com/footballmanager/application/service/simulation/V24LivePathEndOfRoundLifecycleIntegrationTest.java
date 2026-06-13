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
    void participatedInjuredPlayer_doesNotRecover() {
        // V24D6T2: this test now isolates the injury-decrement contract from the
        // suspension-decrement contract (the latter changed — see
        // preExistingSuspendedInParticipatedPlayerIds_decrementFires). A player
        // who is in preRoundInjuredPlayerIds but is also in participatedPlayerIds
        // (i.e. actually played) must NOT have their injury recovery decrement
        // applied, because the recovery represents matches missed.
        String p1 = "p-inj-participating";
        LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(1, 1);
        tracking.preRoundInjuredPlayerIds.add(p1);
        tracking.participatedPlayerIds.add(p1);

        CareerSave career = newCareer();
        addPlayer(career, p1, HOME);
        SessionPlayer p = career.getPlayerManager().getSessionPlayer(p1);
        p.setInjured(true);
        p.setInjuryRemainingMatches(2);
        p.setInjuryType("MATCH_INJURY");

        career.getTournamentState().getFixtures().add(new MatchFixture("m-r1", HOME, AWAY, 1));

        LeagueSimulator simulator = newSimulator(true, true, false, true, false);

        simulator.applyEndOfRoundLiveLifecycle(career, 1,
                career.getTournamentState().getFixtures(), tracking);

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

    // ========== T-V24D6T2 (bug #7): suspended player in participatedPlayerIds still decrements ==========
    // V24D6T2 fix: applyLiveMatchCareerMutations excludes currently-suspended
    // players from participatedPlayerIds accumulation. This test validates the
    // decrement fires for a pre-suspended player even when the participation
    // tracking would otherwise include them.

    @Test
    void preExistingSuspendedInParticipatedPlayerIds_decrementFires() {
        String p1 = "p-susp-in-xi";
        LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(1, 1);
        tracking.preRoundSuspendedPlayerIds.add(p1);
        // Simulate: V24 engine emitted events for p1 (they are in starting XI),
        // so participatedPlayerIds contains them. Pre-fix this would block the decrement.
        tracking.participatedPlayerIds.add(p1);

        CareerSave career = newCareer();
        addPlayer(career, p1, HOME);
        SessionPlayer p = career.getPlayerManager().getSessionPlayer(p1);
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);

        career.getTournamentState().getFixtures().add(new MatchFixture("m-r1-susp-xi", HOME, AWAY, 1));

        LeagueSimulator simulator = newSimulator(true, false, false, true, false);

        simulator.applyEndOfRoundLiveLifecycle(career, 1,
                career.getTournamentState().getFixtures(), tracking);

        // V24D6T2: even though participatedPlayerIds contains p1 (from V24
        // timeline events), the decrement still fires because suspended
        // players are excluded from participatedPlayerIds accumulation.
        assertFalse(p.getSuspended(),
                "V24D6T2: suspended player in participatedPlayerIds must still decrement");
        assertEquals(0, p.getSuspensionRemainingMatches(),
                "V24D6T2: remaining must be 0 even when in participatedPlayerIds");
    }

    // ========== T-V24D6T2 (bug #7): applyLiveMatchCareerMutations excludes suspended from participated ==========
    // V24D6T2: directly exercises applyLiveMatchCareerMutations (now package-private
    // for testability) and asserts a suspended player appearing in the V24 timeline
    // does NOT end up in tracking.participatedPlayerIds. End-to-end decrement
    // behavior is covered by preExistingSuspendedInParticipatedPlayerIds_decrementFires
    // and V24CareerMutationIntegrationTest.preExistingSuspendedPlayerInStartingXI_decrementFires.

    @Test
    void applyLiveMatchCareerMutations_excludesSuspendedFromParticipated() {
        String suspId = "p-suspended-in-xi";
        String healthyId = "p-healthy-in-xi";
        LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(1, 1);

        CareerSave career = newCareer();
        addPlayer(career, suspId, HOME);
        addPlayer(career, healthyId, HOME);
        SessionPlayer sp = career.getPlayerManager().getSessionPlayer(suspId);
        sp.setSuspended(true);
        sp.setSuspensionRemainingMatches(1);

        // Build a V24 timeline where BOTH players appear (e.g. they are in the
        // starting XI). The live mutations method should accumulate both event
        // IDs into participatedPlayerIds — but the suspended one must be excluded.
        V24MatchTimeline timeline = new V24MatchTimeline();
        timeline.addEvent(new V24MatchEvent(
            10, V24MatchEventType.GOAL,
            HOME, suspId, "Suspended Scorer", null, null, 0.0, "Susp goal"));
        timeline.addEvent(new V24MatchEvent(
            20, V24MatchEventType.GOAL,
            HOME, healthyId, "Healthy Scorer", null, null, 0.0, "Healthy goal"));

        V24DetailedMatchResult v24Result = V24DetailedMatchResult.builder()
            .matchId("m-bug7").homeTeamId(HOME).awayTeamId(AWAY)
            .homeGoals(2).awayGoals(0).homeXg(1.0).awayXg(0.0)
            .homeShots(5).awayShots(2).homePossession(60).awayPossession(40)
            .timeline(timeline)
            .summary("V24D6T2: suspended in XI")
            .build();

        LeagueSimulator simulator = newSimulator(true, false, false, true, false);

        // Call the package-private method directly.
        simulator.applyLiveMatchCareerMutations(career, v24Result, tracking);

        // V24D6T2 assertion: suspended player must NOT be in participatedPlayerIds
        // (even though they appear in the V24 timeline as a goal scorer).
        assertFalse(tracking.participatedPlayerIds.contains(suspId),
            "V24D6T2: suspended player must be excluded from participatedPlayerIds "
            + "(they are not actually on the pitch even if they appear in the XI)");
        // Healthy player SHOULD be in participatedPlayerIds.
        assertTrue(tracking.participatedPlayerIds.contains(healthyId),
            "V24D6T2: healthy player who appears in timeline must be in participatedPlayerIds");
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
