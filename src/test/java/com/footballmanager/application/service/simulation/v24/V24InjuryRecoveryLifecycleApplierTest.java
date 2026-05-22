package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6I2: Unit tests for V24InjuryRecoveryLifecycleApplier.
 * Tests injury recovery lifecycle decrement in isolation — no Spring, no IO.
 */
class V24InjuryRecoveryLifecycleApplierTest {

    private final V24InjuryRecoveryLifecycleApplier applier = new V24InjuryRecoveryLifecycleApplier();

    // ========== Helper builders ==========

    private V24CareerMutationPolicy policy(boolean enabled) {
        return new V24CareerMutationPolicy(enabled, enabled, enabled, enabled, enabled);
    }

    private V24CareerMutationPolicy policy(boolean mutate, boolean injuries,
            boolean fatigue, boolean discipline, boolean form) {
        return new V24CareerMutationPolicy(mutate, injuries, fatigue, discipline, form);
    }

    private SessionPlayer injuredPlayer(String playerId, int remaining) {
        SessionPlayer p = SessionPlayer.fromWorldPlayer(playerId, "Injured Guy", "MID", 25, 70);
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(remaining);
        return p;
    }

    private SessionPlayer normalPlayer(String playerId) {
        return SessionPlayer.fromWorldPlayer(playerId, "Normal Guy", "MID", 25, 70);
    }

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
    void nullCareer_doesNothing() {
        V24CareerMutationPolicy pol = policy(true);
        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));

        int result = applier.applyRecovery(null, 1, fx, set("p1"), set(), set("p1"), pol);
        assertEquals(0, result);
    }

    @Test
    void nullPolicy_doesNothing() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));

        int result = applier.applyRecovery(career, 1, fx, set("p1"), set(), set("p1"), null);
        assertEquals(0, result);
    }

    @Test
    void persistInjuriesFalse_doesNothing() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(2);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(false, false, false, true, false);

        int result = applier.applyRecovery(career, 1, fx, set("p1"), set(), set(), pol);
        assertEquals(0, result);
        assertTrue(p.getInjured());
        assertEquals(2, p.getInjuryRemainingMatches());
        assertEquals("MATCH_INJURY", p.getInjuryType());
    }

    @Test
    void nullPreRoundSet_doesNothing() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(career, 1, fx, null, set(), set("p1"), pol);
        assertEquals(0, result);
    }

    @Test
    void emptyPreRoundSet_doesNothing() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(career, 1, fx, Collections.emptySet(), set(), set("p1"), pol);
        assertEquals(0, result);
    }

    @Test
    void nullParticipatedSet_doesNothing() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(1);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(career, 1, fx, set("p1"), set(), null, pol);
        assertEquals(0, result);
        assertTrue(p.getInjured());
        assertEquals(1, p.getInjuryRemainingMatches());
    }

    @Test
    void nullRoundFixtures_doesNothing() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(1);

        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(career, 1, null, set("p1"), set(), set(), pol);
        assertEquals(0, result);
        assertTrue(p.getInjured());
        assertEquals(1, p.getInjuryRemainingMatches());
    }

    // ========== Core lifecycle tests ==========

    @Test
    void preExistingInjured_decrements() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(2);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // p1 was injured before round, team has fixture, did not participate
        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1"),          // preRoundInjuredPlayerIds
                set(),              // newlyInjuredPlayerIds (none)
                set(),              // participated (none)
                pol);

        assertEquals(1, result);
        assertTrue(p.getInjured());
        assertEquals(1, p.getInjuryRemainingMatches());
        assertEquals("MATCH_INJURY", p.getInjuryType());  // injuryType unchanged on partial decrement
    }

    @Test
    void preExistingInjured_recoversWhen1() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(1);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // p1 was injured before round, team has fixture, did not participate, remaining=1
        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1"),
                set(),
                set(),
                pol);

        assertEquals(1, result);
        assertFalse(p.getInjured());
        assertEquals(0, p.getInjuryRemainingMatches());
        assertNull(p.getInjuryType());
    }

    @Test
    void newlyInjured_doesNotDecrementSameRound() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        // p1 was NOT in preRoundInjuredPlayerIds — p1 becomes injured this round via INJURY event
        // But for this test: p1 IS in preRoundInjuredPlayerIds AND newlyInjuredPlayerIds
        // (simulating player who was injured in round N-1, then got a new injury in round N)
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(2);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // p1 is in both preRoundInjuredPlayerIds AND newlyInjuredPlayerIds
        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1"),          // preRoundInjuredPlayerIds
                set("p1"),           // newlyInjuredPlayerIds — received new injury this round
                set(),              // participated (none)
                pol);

        assertEquals(0, result);
        assertTrue(p.getInjured());
        assertEquals(2, p.getInjuryRemainingMatches());
        assertEquals("MATCH_INJURY", p.getInjuryType());
    }

    @Test
    void participated_doesNotDecrement() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(2);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // p1 participated in the round
        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1"),
                set(),
                set("p1"),  // participated
                pol);

        assertEquals(0, result);
        assertTrue(p.getInjured());
        assertEquals(2, p.getInjuryRemainingMatches());
    }

    @Test
    void noFixture_doesNotDecrement() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(2);

        // No fixtures for team-A in round 1
        List<MatchFixture> fx = fixtures(fixture("m1", "team-X", "team-Y", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1"),
                set(),
                set(),
                pol);

        assertEquals(0, result);
        assertTrue(p.getInjured());
        assertEquals(2, p.getInjuryRemainingMatches());
    }

    @Test
    void nullRemaining_handled() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        // Force null remaining via reflection
        setInjuryRemainingMatches(p, null);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1"),
                set(),
                set(),
                pol);

        assertEquals(0, result);
        assertTrue(p.getInjured());  // stays injured with null remaining
    }

    @Test
    void zeroRemaining_handled() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(0);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1"),
                set(),
                set(),
                pol);

        assertEquals(0, result);
        assertTrue(p.getInjured());
        assertEquals(0, p.getInjuryRemainingMatches());
    }

    @Test
    void injuredFalse_staleRemaining_handled() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        // injured=false but remaining > 0 — stale data
        p.setInjured(false);
        p.setInjuryType(null);
        p.setInjuryRemainingMatches(2);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1"),  // in preRoundInjured set but not actually injured
                set(),
                set(),
                pol);

        assertEquals(0, result);
        assertFalse(p.getInjured());
        assertEquals(2, p.getInjuryRemainingMatches());  // stale data not modified
    }

    @Test
    void playerNotInCareer_handled() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(1);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // "unknown-id" not in career
        int result = applier.applyRecovery(
                career, 1, fx,
                set("unknown-id"),
                set(),
                set(),
                pol);

        assertEquals(0, result);
        assertTrue(p.getInjured());  // p1 unchanged
        assertEquals(1, p.getInjuryRemainingMatches());
    }

    @Test
    void multipleInjuredPlayers_decrementTogether() {
        // team-A has fixture in round 1; team-B has no fixture
        CareerSave career = new CareerSave();

        SessionTeam teamA = new SessionTeam();
        teamA.setSessionTeamId("team-A");
        teamA.setName("Team A");
        teamA.setWorldTeamId("team-A");
        career.addSessionTeam(teamA);

        SessionTeam teamB = new SessionTeam();
        teamB.setSessionTeamId("team-B");
        teamB.setName("Team B");
        teamB.setWorldTeamId("team-B");
        career.addSessionTeam(teamB);

        SessionPlayer p1 = SessionPlayer.fromWorldPlayer("p1", "Player 1", "MID", 25, 70);
        SessionPlayer p2 = SessionPlayer.fromWorldPlayer("p2", "Player 2", "MID", 25, 70);
        career.addSessionPlayer(p1);
        career.addSessionPlayer(p2);
        career.assignPlayerToTeam("p1", "team-A");
        career.assignPlayerToTeam("p2", "team-B");

        p1.setInjured(true);
        p1.setInjuryType("MATCH_INJURY");
        p1.setInjuryRemainingMatches(2);

        p2.setInjured(true);
        p2.setInjuryType("MATCH_INJURY");
        p2.setInjuryRemainingMatches(2);

        // team-A has fixture in round 1; team-B has no fixture
        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-C", 1));

        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1", "p2"),
                set(),
                set(),  // neither participated
                pol);

        // Only p1: team-A had fixture in round 1, p1 didn't participate
        assertEquals(1, result);
        assertTrue(p1.getInjured());
        assertEquals(1, p1.getInjuryRemainingMatches());

        // p2: team-B had no fixture in round 1 → no decrement
        assertTrue(p2.getInjured());
        assertEquals(2, p2.getInjuryRemainingMatches());
    }

    @Test
    void injuryTypeCleared_onFullRecovery() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(1);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1"),
                set(),
                set(),
                pol);

        assertEquals(1, result);
        assertFalse(p.getInjured());
        assertEquals(0, p.getInjuryRemainingMatches());
        assertNull(p.getInjuryType());
    }

    @Test
    void injuryTypeKept_onPartialDecrement() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(2);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1"),
                set(),
                set(),
                pol);

        assertEquals(1, result);
        assertTrue(p.getInjured());
        assertEquals(1, p.getInjuryRemainingMatches());
        assertEquals("MATCH_INJURY", p.getInjuryType());  // injuryType kept on partial decrement
    }

    @Test
    void newlyInjuredSetNull_treatedAsEmpty() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(2);

        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 1));
        V24CareerMutationPolicy pol = policy(true);

        // newlyInjuredPlayerIds = null should be treated as empty set
        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1"),
                null,  // null newlyInjuredPlayerIds
                set(),
                pol);

        assertEquals(1, result);
        assertTrue(p.getInjured());
        assertEquals(1, p.getInjuryRemainingMatches());
    }

    @Test
    void fixtureDifferentRound_noDecrement() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setInjured(true);
        p.setInjuryType("MATCH_INJURY");
        p.setInjuryRemainingMatches(2);

        // team-A has fixture in round 2, not round 1
        List<MatchFixture> fx = fixtures(fixture("m1", "team-A", "team-B", 2));
        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(
                career, 1, fx,  // currentRound = 1
                set("p1"),
                set(),
                set(),
                pol);

        assertEquals(0, result);
        assertTrue(p.getInjured());
        assertEquals(2, p.getInjuryRemainingMatches());
    }

    @Test
    void multiplePlayers_mixedEligibility_countsOnlyChanged() {
        // Setup:
        // team-A: p1, p2, p3 — has fixture in round 1
        // team-C: p4 — has NO fixture in round 1 (only in round 2)
        CareerSave career = new CareerSave();

        SessionTeam teamA = new SessionTeam();
        teamA.setSessionTeamId("team-A");
        teamA.setName("Team A");
        teamA.setWorldTeamId("team-A");
        career.addSessionTeam(teamA);

        SessionTeam teamC = new SessionTeam();
        teamC.setSessionTeamId("team-C");
        teamC.setName("Team C");
        teamC.setWorldTeamId("team-C");
        career.addSessionTeam(teamC);

        for (String pid : List.of("p1", "p2", "p3", "p4")) {
            SessionPlayer p = SessionPlayer.fromWorldPlayer(pid, "Player " + pid, "MID", 25, 70);
            career.addSessionPlayer(p);
        }
        for (String pid : List.of("p1", "p2", "p3")) career.assignPlayerToTeam(pid, "team-A");
        career.assignPlayerToTeam("p4", "team-C");

        SessionPlayer sp1 = career.getSessionPlayer("p1");
        sp1.setInjured(true);
        sp1.setInjuryType("MATCH_INJURY");
        sp1.setInjuryRemainingMatches(2);

        SessionPlayer sp2 = career.getSessionPlayer("p2");
        sp2.setInjured(true);
        sp2.setInjuryType("MATCH_INJURY");
        sp2.setInjuryRemainingMatches(1);

        SessionPlayer sp3 = career.getSessionPlayer("p3");
        sp3.setInjured(true);
        sp3.setInjuryType("MATCH_INJURY");
        sp3.setInjuryRemainingMatches(1);

        SessionPlayer sp4 = career.getSessionPlayer("p4");
        sp4.setInjured(true);
        sp4.setInjuryType("MATCH_INJURY");
        sp4.setInjuryRemainingMatches(1);

        // team-A has fixture in round 1; team-C has fixture in round 2 only
        List<MatchFixture> fx = new ArrayList<>();
        fx.add(new MatchFixture("m1", "team-A", "team-B", 1));
        fx.add(new MatchFixture("m2", "team-C", "team-D", 2));

        V24CareerMutationPolicy pol = policy(true);

        int result = applier.applyRecovery(
                career, 1, fx,
                set("p1", "p2", "p3", "p4"),
                set("p3"),  // newly injured this round
                set("p2"),  // participated
                pol);

        // Only p1: team-A had fixture in round 1, p1 didn't participate, wasn't newly injured
        assertEquals(1, result);
        assertEquals(1, sp1.getInjuryRemainingMatches());  // 2 → 1
        assertTrue(sp1.getInjured());

        // p2: skip (participated)
        assertTrue(sp2.getInjured());
        assertEquals(1, sp2.getInjuryRemainingMatches());

        // p3: skip (newly injured)
        assertTrue(sp3.getInjured());
        assertEquals(1, sp3.getInjuryRemainingMatches());

        // p4: skip (team-C had no fixture in round 1)
        assertTrue(sp4.getInjured());
        assertEquals(1, sp4.getInjuryRemainingMatches());
    }

    // ========== Reflection helpers ==========

    private void setInjuryRemainingMatches(SessionPlayer p, Integer value) {
        try {
            java.lang.reflect.Field field = SessionPlayer.class.getDeclaredField("injuryRemainingMatches");
            field.setAccessible(true);
            field.set(p, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}