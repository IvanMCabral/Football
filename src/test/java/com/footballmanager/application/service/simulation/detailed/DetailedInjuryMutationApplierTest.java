package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests injury mutation behavior in isolation — no Redis, no Spring, no IO.
 */
class InjuryMutationApplierTest {

    private final InjuryMutationApplier applier = new InjuryMutationApplier();

    // ========== Helper builders ==========

    private CareerMutationPolicy policy(boolean mutate, boolean injuries,
            boolean fatigue, boolean discipline, boolean form) {
        return new CareerMutationPolicy(mutate, injuries, fatigue, discipline, form);
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

    private DetailedMatchEvent injuryEvent(String playerId, int minute) {
        return new DetailedMatchEvent(minute, DetailedMatchEventType.INJURY,
                "team-A", playerId, "Injured Player", null, null, 0.0, "Injury");
    }

    private DetailedMatchEvent goalEvent(String playerId, int minute) {
        return new DetailedMatchEvent(minute, DetailedMatchEventType.GOAL,
                "team-A", playerId, "Scorer", null, null, 0.35, "Goal");
    }

    private DetailedMatchResult result(DetailedMatchEvent... events) {
        MatchTimeline timeline = new MatchTimeline();
        for (DetailedMatchEvent e : events) timeline.addEvent(e);
        return DetailedMatchResult.builder()
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
        CareerMutationPolicy p = policy(false, true, false, false, false);
        assertFalse(p.isInjuryPersistenceEnabled());
    }

    @Test
    void masterFlagTrue_plus_persistInjuriesFalse_disablesInjuryPersistence() {
        CareerMutationPolicy p = policy(true, false, false, false, false);
        assertFalse(p.isInjuryPersistenceEnabled());
    }

    @Test
    void masterFlagTrue_plus_persistInjuriesTrue_enablesInjuryPersistence() {
        CareerMutationPolicy p = policy(true, true, false, false, false);
        assertTrue(p.isInjuryPersistenceEnabled());
    }

    @Test
    void fatigueFlagDoesNotEnableInjuryPersistence() {
        CareerMutationPolicy p = policy(true, false, true, false, false);
        assertFalse(p.isInjuryPersistenceEnabled());
    }

    // ========== Applier null-guard tests ==========

    @Test
    void nullCareer_returnsZero() {
        CareerMutationPolicy pol = policy(true, true, false, false, false);
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        assertEquals(0, applier.applyInjuries(null, res, pol));
    }

    @Test
    void nullResult_returnsZero() {
        CareerMutationPolicy pol = policy(true, true, false, false, false);
        CareerSave career = careerWithPlayers("p1");
        assertEquals(0, applier.applyInjuries(career, null, pol));
    }

    @Test
    void nullPolicy_returnsZero() {
        CareerSave career = careerWithPlayers("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        assertEquals(0, applier.applyInjuries(career, res, null));
    }

    // ========== Applier flag-disabled tests ==========

    @Test
    void flagsDisabled_returnsZeroAndDoesNotMutatePlayer() {
        CareerSave career = careerWithPlayers("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(false, false, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(0, count);
        SessionPlayer p = career.getSessionPlayer("p1");
        assertFalse(p.getInjured());
    }

    // ========== Applier basic behavior tests ==========

    @Test
    void noInjuryEvents_returnsZero() {
        CareerSave career = careerWithPlayers("p1");
        DetailedMatchResult res = result(goalEvent("p1", 30), goalEvent("p1", 60));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(0, count);
    }

    @Test
    void oneInjuryEvent_marksMatchingSessionPlayerAsInjured() {
        CareerSave career = careerWithPlayer("p1", "Injured Striker", "ATT");
        DetailedMatchResult res = result(injuryEvent("p1", 67));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(1, count);
        SessionPlayer p = career.getSessionPlayer("p1");
        assertTrue(p.getInjured());
    }

    @Test
    void appliesDefaultInjuryType() {
        CareerSave career = careerWithPlayer("p1", "Injured Mid", "MID");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career, res, pol);

        assertEquals("MATCH_INJURY", career.getSessionPlayer("p1").getInjuryType());
    }

    @Test
    void appliesDefaultInjuryRemainingMatches() {
        CareerSave career = careerWithPlayer("p1", "Injured Def", "DEF");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career, res, pol);

        assertEquals(2, career.getSessionPlayer("p1").getInjuryRemainingMatches());
    }

    @Test
    void unknownPlayerId_skipsSafelyAndReturnsZero() {
        CareerSave career = careerWithPlayer("p1", "Known Player", "MID");
        DetailedMatchResult res = result(injuryEvent("unknown-player", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

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

        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(0, count);
        assertEquals("OLD_INJURY", career.getSessionPlayer("p1").getInjuryType());
        assertEquals(5, career.getSessionPlayer("p1").getInjuryRemainingMatches());
    }

    @Test
    void duplicateInjuryEventsForSamePlayer_applyOnce() {
        CareerSave career = careerWithPlayer("p1", "Twice Injured", "MID");
        DetailedMatchResult res = result(
                injuryEvent("p1", 30),
                injuryEvent("p1", 75)
        );
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(1, count);
        assertTrue(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void multipleInjuryEventsForDifferentPlayers_applyBoth() {
        CareerSave career = careerWithPlayers("p1", "p2", "p3");
        DetailedMatchResult res = result(
                injuryEvent("p1", 30),
                injuryEvent("p2", 65)
        );
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(2, count);
        assertTrue(career.getSessionPlayer("p1").getInjured());
        assertTrue(career.getSessionPlayer("p2").getInjured());
        assertFalse(career.getSessionPlayer("p3").getInjured());
    }

    @Test
    void nonInjuryEvents_doNotMutate() {
        CareerSave career = careerWithPlayer("p1", "Clean Player", "ATT");
        DetailedMatchResult res = result(goalEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career, res, pol);

        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void doesNotChangePlayerEnergy() {
        CareerSave career = careerWithPlayer("p1", "Any Player", "MID");
        career.getSessionPlayer("p1").setEnergy(60);
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career, res, pol);

        assertEquals(60, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void doesNotChangePlayerForm() {
        CareerSave career = careerWithPlayer("p1", "Any Player", "MID");
        career.getSessionPlayer("p1").setForm(75);
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career, res, pol);

        assertEquals(75, career.getSessionPlayer("p1").getForm());
    }

    @Test
    void doesNotChangeNonInjuredPlayer() {
        CareerSave career = careerWithPlayer("p1", "Clean Forward", "ATT");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career, res, pol);

        // Only injured flag, injuryType, injuryRemainingMatches should change
        assertFalse(career.getSessionPlayer("p1").getInjured() == null);
        assertTrue(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void deterministicNoRandomBehavior() {
        CareerSave career1 = careerWithPlayer("p1", "Player One", "MID");
        CareerSave career2 = careerWithPlayer("p1", "Player One", "MID");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        applier.applyInjuries(career1, res, pol);
        applier.applyInjuries(career2, res, pol);

        assertEquals(career1.getSessionPlayer("p1").getInjured(),
                    career2.getSessionPlayer("p1").getInjured());
    }

    @Test
    void emptyTimeline_noMutation() {
        CareerSave career = careerWithPlayers("p1", "p2");
        DetailedMatchResult res = result();
        CareerMutationPolicy pol = policy(true, true, false, false, false);

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

        DetailedMatchResult res = result(
                injuryEvent("p1", 30),
                injuryEvent("p2", 65)
        );
        CareerMutationPolicy pol = policy(true, true, false, false, false);

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
        DetailedMatchEvent nullIdEvent = new DetailedMatchEvent(45, DetailedMatchEventType.INJURY,
                "team-A", null, null, null, null, 0.0, "Injury");
        DetailedMatchResult res = result(nullIdEvent);
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        int count = applier.applyInjuries(career, res, pol);

        assertEquals(0, count);
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }
}