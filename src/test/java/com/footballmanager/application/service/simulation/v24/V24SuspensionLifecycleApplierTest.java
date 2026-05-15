package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6D6A: Unit tests for V24SuspensionLifecycleApplier.
 * Tests suspension lifecycle decrement in isolation — no Spring, no IO.
 */
class V24SuspensionLifecycleApplierTest {

    private final V24SuspensionLifecycleApplier applier = new V24SuspensionLifecycleApplier();

    // ========== Helper builders ==========

    private V24CareerMutationPolicy policy(boolean enabled) {
        // mutateCareerState, persistInjuries, persistFatigue, persistDiscipline, persistForm
        return new V24CareerMutationPolicy(enabled, enabled, enabled, enabled, enabled);
    }

    private V24CareerMutationPolicy policy(boolean mutate, boolean injuries,
            boolean fatigue, boolean discipline, boolean form) {
        return new V24CareerMutationPolicy(mutate, injuries, fatigue, discipline, form);
    }

    private SessionPlayer suspendedPlayer(String playerId, int remaining) {
        SessionPlayer p = SessionPlayer.fromWorldPlayer(playerId, "Suspended Guy", "MID", 25, 70);
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(remaining);
        p.setRedCards(1);
        return p;
    }

    private SessionPlayer normalPlayer(String playerId) {
        return SessionPlayer.fromWorldPlayer(playerId, "Normal Guy", "MID", 25, 70);
    }

    /**
     * Builds a CareerSave with:
     * - One team "team-A" with the given players assigned to squad
     * - Players registered in career
     */
    private CareerSave careerWithTeamAndPlayers(String teamId, List<String> playerIds) {
        CareerSave career = new CareerSave();

        SessionTeam team = new SessionTeam();
        team.setSessionTeamId(teamId);
        team.setName("Team " + teamId);
        team.setWorldTeamId(teamId);
        career.addSessionTeam(team);

        for (String pid : playerIds) {
            SessionPlayer p = career.getSessionPlayer(pid);
            if (p == null) {
                p = SessionPlayer.fromWorldPlayer(pid, "Player " + pid, "MID", 25, 70);
                career.addSessionPlayer(p);
            }
            career.assignPlayerToTeam(pid, teamId);
        }

        return career;
    }

    private CareerSave careerWithTeamAndPlayer(String teamId, String playerId) {
        return careerWithTeamAndPlayers(teamId, List.of(playerId));
    }

    private MatchFixture fixture(String matchId, String homeTeamId, String awayTeamId, int round) {
        return new MatchFixture(matchId, homeTeamId, awayTeamId, round);
    }

    private List<MatchFixture> fixtures(MatchFixture... fixtures) {
        return new ArrayList<>(Arrays.asList(fixtures));
    }

    private Set<String> set(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    // ========== Null-guard / disabled tests ==========

    @Test
    void nullCareer_returnsZero() {
        V24CareerMutationPolicy pol = policy(true);
        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        Set<String> pre = set("p1");

        int result = applier.applyServedSuspensions(null, 1, fx, pre, set(), set("p1"), pol);
        assertEquals(0, result);
    }

    @Test
    void nullPolicy_returnsZero() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));

        int result = applier.applyServedSuspensions(career, 1, fx, set("p1"), set(), set("p1"), null);
        assertEquals(0, result);
    }

    @Test
    void disabledPolicy_noLifecycleChange() {
        // Master false, discipline true
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));

        V24CareerMutationPolicy pol = policy(false, false, false, true, false);
        int result = applier.applyServedSuspensions(career, 1, fx, set("p1"), set(), set(), pol);
        assertEquals(0, result);
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());

        // Master true, discipline false
        pol = policy(true, false, false, false, false);
        result = applier.applyServedSuspensions(career, 1, fx, set("p1"), set(), set(), pol);
        assertEquals(0, result);
    }

    @Test
    void nullPreMatchSuspendedSet_returnsZero() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyServedSuspensions(career, 1, fx, null, set(), set("p1"), pol);
        assertEquals(0, result);
    }

    @Test
    void emptyPreMatchSuspendedSet_returnsZero() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyServedSuspensions(career, 1, fx, Collections.emptySet(), set(), set("p1"), pol);
        assertEquals(0, result);
    }

    @Test
    void nullParticipatedSet_returnsZeroAndDoesNotDecrement() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // null participatedPlayerIds → return 0, no decrement
        int result = applier.applyServedSuspensions(career, 1, fx, set("p1"), set(), null, pol);
        assertEquals(0, result);
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    @Test
    void nullRoundFixtures_returnsZeroAndDoesNotDecrement() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);

        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyServedSuspensions(career, 1, null, set("p1"), set(), set(), pol);
        assertEquals(0, result);
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    // ========== Core lifecycle tests ==========

    @Test
    void suspendedBeforeMatch_remainingOne_clearsAfterServed() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);
        p.setRedCards(1);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // p1 was suspended before round, not participated, team had fixture
        int result = applier.applyServedSuspensions(
                career, 1, fx,
                set("p1"),          // preMatchSuspended
                set(),              // newlySuspended (none)
                set(),              // participated (none)
                pol);

        assertEquals(1, result);
        assertFalse(p.getSuspended());
        assertEquals(0, p.getSuspensionRemainingMatches());
    }

    @Test
    void suspendedBeforeMatch_remainingTwo_decrementsToOneStillSuspended() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(2);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyServedSuspensions(
                career, 1, fx,
                set("p1"),
                set(),
                set(),
                pol);

        assertEquals(1, result);
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    @Test
    void notSuspended_noChange() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        // suspended defaults to false from initDefaults()
        p.setSuspensionRemainingMatches(0);
        p.setYellowCards(2);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyServedSuspensions(
                career, 1, fx,
                set("p1"),  // in preMatchSuspended set but not actually suspended
                set(),
                set(),
                pol);

        assertEquals(0, result);
        assertFalse(p.getSuspended());
        assertEquals(2, p.getYellowCards());
    }

    @Test
    void remainingZero_noChange() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(0);  // already at 0

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyServedSuspensions(
                career, 1, fx,
                set("p1"),
                set(),
                set(),
                pol);

        assertEquals(0, result);
        assertTrue(p.getSuspended());  // stays suspended with 0 remaining
        assertEquals(0, p.getSuspensionRemainingMatches());
    }

    @Test
    void newlyRedCardedThisRound_notDecremented() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);
        p.setRedCards(1);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // p1 is in both preMatchSuspended AND newlySuspended (received RED_CARD this round)
        int result = applier.applyServedSuspensions(
                career, 1, fx,
                set("p1"),  // preMatchSuspended
                set("p1"), // newlySuspended — RED_CARD this round
                set(),
                pol);

        assertEquals(0, result);
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    @Test
    void participatedPlayer_notDecremented() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // p1 participated in the round
        int result = applier.applyServedSuspensions(
                career, 1, fx,
                set("p1"),
                set(),
                set("p1"),  // participated
                pol);

        assertEquals(0, result);
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    @Test
    void unknownPlayer_skipped() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // "unknown-id" not in career
        int result = applier.applyServedSuspensions(
                career, 1, fx,
                set("unknown-id"),
                set(),
                set(),
                pol);

        assertEquals(0, result);
        assertTrue(p.getSuspended());  // p1 unchanged
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    @Test
    void blankOrNullPlayerIds_skipped() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // preMatchSuspended contains blank string — career.getSessionPlayer returns null
        int result = applier.applyServedSuspensions(
                career, 1, fx,
                set("  "),
                set(),
                set(),
                pol);

        assertEquals(0, result);
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    @Test
    void playerTeamNoFixture_noDecrement() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);

        // No fixtures for team-A in round 1
        List<MatchFixture> fx = fixtures(fixture("m1", "team-X", "team-Y", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyServedSuspensions(
                career, 1, fx,
                set("p1"),
                set(),
                set(),
                pol);

        assertEquals(0, result);
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    @Test
    void fixtureDifferentRound_noDecrement() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);

        // team-A has fixture in round 2, not round 1
        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 2));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyServedSuspensions(
                career, 1, fx,  // currentRound = 1
                set("p1"),
                set(),
                set(),
                pol);

        assertEquals(0, result);
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    @Test
    void multiplePlayers_mixedEligibility_countsOnlyChanged() {
        // Setup:
        // team-A: p1, p2, p3 — has fixture in round 1
        // team-C: p4 — has NO fixture in round 1 (only in round 2)
        CareerSave career = new CareerSave();

        SessionTeam teamA = new SessionTeam(); teamA.setSessionTeamId("team-A"); teamA.setName("Team A"); teamA.setWorldTeamId("team-A");
        SessionTeam teamC = new SessionTeam(); teamC.setSessionTeamId("team-C"); teamC.setName("Team C"); teamC.setWorldTeamId("team-C");
        career.addSessionTeam(teamA);
        career.addSessionTeam(teamC);

        for (String pid : List.of("p1", "p2", "p3", "p4")) {
            SessionPlayer p = SessionPlayer.fromWorldPlayer(pid, "Player " + pid, "MID", 25, 70);
            career.addSessionPlayer(p);
        }
        for (String pid : List.of("p1", "p2", "p3")) career.assignPlayerToTeam(pid, "team-A");
        career.assignPlayerToTeam("p4", "team-C");

        // p1: eligible → remaining 2 → after decrement remaining 1
        SessionPlayer sp1 = career.getSessionPlayer("p1");
        sp1.setSuspended(true);
        sp1.setSuspensionRemainingMatches(2);

        // p2: participated → skip
        SessionPlayer sp2 = career.getSessionPlayer("p2");
        sp2.setSuspended(true);
        sp2.setSuspensionRemainingMatches(1);

        // p3: newly suspended this round → skip
        SessionPlayer sp3 = career.getSessionPlayer("p3");
        sp3.setSuspended(true);
        sp3.setSuspensionRemainingMatches(1);
        sp3.setRedCards(1);

        // p4: team-C has fixture in round 2, not round 1 → skip (no fixture in round 1)
        SessionPlayer sp4 = career.getSessionPlayer("p4");
        sp4.setSuspended(true);
        sp4.setSuspensionRemainingMatches(1);

        // team-A has fixture in round 1; team-C has fixture in round 2 only
        List<MatchFixture> fx = new ArrayList<>();
        fx.add(new MatchFixture("m1", "team-A", "team-B", 1));
        fx.add(new MatchFixture("m2", "team-C", "team-D", 2));  // round 2, not round 1

        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyServedSuspensions(
                career, 1, fx,
                set("p1", "p2", "p3", "p4"),
                set("p3"),  // newly suspended this round
                set("p2"),  // participated
                pol);

        // Only p1: team-A had fixture in round 1, p1 didn't participate, wasn't newly suspended
        assertEquals(1, result);
        assertEquals(1, sp1.getSuspensionRemainingMatches());  // 2 → 1
        assertTrue(sp1.getSuspended());

        // p2: skip (participated)
        assertTrue(sp2.getSuspended());
        assertEquals(1, sp2.getSuspensionRemainingMatches());

        // p3: skip (newly suspended)
        assertTrue(sp3.getSuspended());
        assertEquals(1, sp3.getSuspensionRemainingMatches());

        // p4: skip (team-C had no fixture in round 1)
        assertTrue(sp4.getSuspended());
        assertEquals(1, sp4.getSuspensionRemainingMatches());
    }

    @Test
    void nullExistingSuspensionFields_defaultSafely() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        // Force null fields via reflection (simulating old serialized data)
        setSuspensionFields(p, null, null);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // Null suspended/remaining should not cause NPE; getter defaults apply
        int result = applier.applyServedSuspensions(
                career, 1, fx,
                set("p1"),
                set(),
                set(),
                pol);

        // getSuspended() returns false for null → player not considered suspended → 0 applied
        assertEquals(0, result);
    }

    private void setSuspensionFields(SessionPlayer p, Boolean suspended, Integer remaining) {
        try {
            java.lang.reflect.Field suspendedField = SessionPlayer.class.getDeclaredField("suspended");
            suspendedField.setAccessible(true);
            suspendedField.set(p, suspended);

            java.lang.reflect.Field remainingField = SessionPlayer.class.getDeclaredField("suspensionRemainingMatches");
            remainingField.setAccessible(true);
            remainingField.set(p, remaining);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}