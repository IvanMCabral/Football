package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6B1: Unit tests for V24InjuryMutationApplier and V24CareerMutationPolicy.
 * Tests injury mutation behavior in isolation — no Redis, no Spring, no IO.
 */
class V24InjuryMutationApplierTest {

    private final V24InjuryMutationApplier applier = new V24InjuryMutationApplier();

    // ========== Helper builders ==========

    private V24CareerMutationPolicy policy(boolean mutate, boolean injuries,
            boolean fatigue, boolean discipline, boolean form) {
        return new V24CareerMutationPolicy(mutate, injuries, fatigue, discipline, form);
    }

    private CareerSave careerWithPlayer(String playerId, String name, String position) {
        CareerSave career = new CareerSave();
        SessionPlayer p = SessionPlayer.fromWorldPlayer(playerId, name, position, 25, 70);
        career.addSessionPlayer(p);
        return career;
    }

    private CareerSave careerWithPlayers(String... ids) {
        CareerSave career = new CareerSave();
        for (int i = 0; i < ids.length; i++) {
            SessionPlayer p = SessionPlayer.fromWorldPlayer(ids[i], "Player " + i, "MID", 25, 70);
            career.addSessionPlayer(p);
        }
        return career;
    }

    private V24MatchEvent injuryEvent(String playerId, int minute) {
        return new V24MatchEvent(minute, V24MatchEventType.INJURY,
                "team-A", playerId, "Injured Player", null, null, 0.0, "Injury");
    }

    private V24MatchEvent goalEvent(String playerId, int minute) {
        return new V24MatchEvent(minute, V24MatchEventType.GOAL,
                "team-A", playerId, "Scorer", null, null, 0.35, "Goal");
    }

    private V24DetailedMatchResult result(V24MatchEvent... events) {
        V24MatchTimeline timeline = new V24MatchTimeline();
        for (V24MatchEvent e : events) timeline.addEvent(e);
        return V24DetailedMatchResult.builder()
                .matchId("match-1")
                .homeTeamId("team-A").awayTeamId("team-B")
                .homeGoals(1).awayGoals(0)
                .homeXg(1.2).awayXg(0.8)
                .homeShots(8).awayShots(5)
                .homePossession(55).awayPossession(45)
                .timeline(timeline)
                .summary("Test match")
                .build();
    }

    // ========== Policy tests ==========

    @Test
    void masterFlagFalse_plus_persistInjuriesTrue_disablesInjuryPersistence() {
        V24CareerMutationPolicy p = policy(false, true, false, false, false);
        assertFalse(p.isInjuryPersistenceEnabled());
    }

    @Test
    void masterFlagTrue_plus_persistInjuriesFalse_disablesInjuryPersistence() {
        V24CareerMutationPolicy p = policy(true, false, false, false, false);
        assertFalse(p.isInjuryPersistenceEnabled());
    }

    @Test
    void masterFlagTrue_plus_persistInjuriesTrue_enablesInjuryPersistence() {
        V24CareerMutationPolicy p = policy(true, true, false, false, false);
        assertTrue(p.isInjuryPersistenceEnabled());
    }

    @Test
    void fatigueFlagDoesNotEnableInjuryPersistence() {
        V24CareerMutationPolicy p = policy(true, false, true, false, false);
        assertFalse(p.isInjuryPersistenceEnabled());
    }

    // ========== Applier null-guard tests ==========

    @Test
    void nullCareer_returnsZero() {
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        assertEquals(0, applier.applyInjuries(null, res, pol));
    }

    @Test
    void nullResult_returnsZero() {
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);
        CareerSave career = careerWithPlayers("p1");
        assertEquals(0, applier.applyInjuries(career, null, pol));
    }

    @Test
    void nullPolicy_returnsZero() {
        CareerSave career = careerWithPlayers("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        assertEquals(0, applier.applyInjuries(career, res, null));
    }

    // ========== Applier flag-disabled tests ==========

    @Test
    void flagsDisabled_returnsZeroAndDoesNotMutatePlayer() {
        CareerSave career = careerWithPlayers("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(false, false, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(0, count);
        SessionPlayer p = career.getSessionPlayer("p1");
        assertFalse(p.getInjured());
    }

    // ========== Applier basic behavior tests ==========

    @Test
    void noInjuryEvents_returnsZero() {
        CareerSave career = careerWithPlayers("p1");
        V24DetailedMatchResult res = result(goalEvent("p1", 30), goalEvent("p1", 60));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(0, count);
    }

    @Test
    void oneInjuryEvent_marksMatchingSessionPlayerAsInjured() {
        CareerSave career = careerWithPlayer("p1", "Injured Striker", "ATT");
        V24DetailedMatchResult res = result(injuryEvent("p1", 67));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(1, count);
        SessionPlayer p = career.getSessionPlayer("p1");
        assertTrue(p.getInjured());
    }

    @Test
    void appliesDefaultInjuryType() {
        CareerSave career = careerWithPlayer("p1", "Injured Mid", "MID");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career, res, pol);

        assertEquals("MATCH_INJURY", career.getSessionPlayer("p1").getInjuryType());
    }

    @Test
    void appliesDefaultInjuryRemainingMatches() {
        CareerSave career = careerWithPlayer("p1", "Injured Def", "DEF");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career, res, pol);

        assertEquals(2, career.getSessionPlayer("p1").getInjuryRemainingMatches());
    }

    @Test
    void unknownPlayerId_skipsSafelyAndReturnsZero() {
        CareerSave career = careerWithPlayer("p1", "Known Player", "MID");
        V24DetailedMatchResult res = result(injuryEvent("unknown-player", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(0, count);
        // p1 must remain uninjured
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void alreadyInjuredPlayer_isNotOverwritten() {
        CareerSave career = careerWithPlayer("p1", "Already Injured", "MID");
        career.getSessionPlayer("p1").setInjured(true);
        career.getSessionPlayer("p1").setInjuryType("OLD_INJURY");
        career.getSessionPlayer("p1").setInjuryRemainingMatches(5);

        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(0, count);
        assertEquals("OLD_INJURY", career.getSessionPlayer("p1").getInjuryType());
        assertEquals(5, career.getSessionPlayer("p1").getInjuryRemainingMatches());
    }

    @Test
    void duplicateInjuryEventsForSamePlayer_applyOnce() {
        CareerSave career = careerWithPlayer("p1", "Twice Injured", "MID");
        V24DetailedMatchResult res = result(
                injuryEvent("p1", 30),
                injuryEvent("p1", 75)
        );
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(1, count);
        assertTrue(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void multipleInjuryEventsForDifferentPlayers_applyBoth() {
        CareerSave career = careerWithPlayers("p1", "p2", "p3");
        V24DetailedMatchResult res = result(
                injuryEvent("p1", 30),
                injuryEvent("p2", 65)
        );
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(2, count);
        assertTrue(career.getSessionPlayer("p1").getInjured());
        assertTrue(career.getSessionPlayer("p2").getInjured());
        assertFalse(career.getSessionPlayer("p3").getInjured());
    }

    @Test
    void nonInjuryEvents_doNotMutate() {
        CareerSave career = careerWithPlayer("p1", "Clean Player", "ATT");
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career, res, pol);

        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void doesNotChangePlayerEnergy() {
        CareerSave career = careerWithPlayer("p1", "Any Player", "MID");
        career.getSessionPlayer("p1").setEnergy(60);
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career, res, pol);

        assertEquals(60, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void doesNotChangePlayerForm() {
        CareerSave career = careerWithPlayer("p1", "Any Player", "MID");
        career.getSessionPlayer("p1").setForm(75);
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career, res, pol);

        assertEquals(75, career.getSessionPlayer("p1").getForm());
    }

    @Test
    void doesNotChangeNonInjuredPlayer() {
        CareerSave career = careerWithPlayer("p1", "Clean Forward", "ATT");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career, res, pol);

        // Only injured flag, injuryType, injuryRemainingMatches should change
        assertFalse(career.getSessionPlayer("p1").getInjured() == null);
        assertTrue(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void deterministicNoRandomBehavior() {
        CareerSave career1 = careerWithPlayer("p1", "Player One", "MID");
        CareerSave career2 = careerWithPlayer("p1", "Player One", "MID");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career1, res, pol);
        applier.applyInjuries(career2, res, pol);

        assertEquals(career1.getSessionPlayer("p1").getInjured(),
                    career2.getSessionPlayer("p1").getInjured());
    }

    // ========== V24D6F3: Edge Case Regression Tests ==========

    @Test
    void emptyTimeline_noMutation() {
        CareerSave career = careerWithPlayers("p1", "p2");
        V24DetailedMatchResult res = result();
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(0, count);
        assertFalse(career.getSessionPlayer("p1").getInjured());
        assertFalse(career.getSessionPlayer("p2").getInjured());
    }

    @Test
    void allPlayersAlreadyInjured_noDoubleApplication() {
        CareerSave career = careerWithPlayers("p1", "p2");
        career.getSessionPlayer("p1").setInjured(true);
        career.getSessionPlayer("p1").setInjuryType("PRE_EXISTING");
        career.getSessionPlayer("p1").setInjuryRemainingMatches(99);
        career.getSessionPlayer("p2").setInjured(true);
        career.getSessionPlayer("p2").setInjuryType("OLD_INJURY");
        career.getSessionPlayer("p2").setInjuryRemainingMatches(5);

        V24DetailedMatchResult res = result(
                injuryEvent("p1", 30),
                injuryEvent("p2", 65)
        );
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(0, count);
        assertEquals("PRE_EXISTING", career.getSessionPlayer("p1").getInjuryType());
        assertEquals(99, career.getSessionPlayer("p1").getInjuryRemainingMatches());
        assertEquals("OLD_INJURY", career.getSessionPlayer("p2").getInjuryType());
        assertEquals(5, career.getSessionPlayer("p2").getInjuryRemainingMatches());
    }

    @Test
    void injuryEventWithNullPlayerId_ignoredSafely() {
        CareerSave career = careerWithPlayer("p1", "Known Player", "MID");
        V24MatchEvent nullIdEvent = new V24MatchEvent(45, V24MatchEventType.INJURY,
                "team-A", null, null, null, null, 0.0, "Injury");
        V24DetailedMatchResult res = result(nullIdEvent);
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(0, count);
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }
}