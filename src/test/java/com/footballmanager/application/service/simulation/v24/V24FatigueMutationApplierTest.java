package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6C1: Unit tests for V24FatigueMutationApplier.
 * Tests fatigue/energy mutation behavior in isolation — no Redis, no Spring, no IO.
 */
class V24FatigueMutationApplierTest {

    private final V24FatigueMutationApplier applier = new V24FatigueMutationApplier();

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

    private V24MatchEvent goalEvent(String playerId, int minute) {
        return new V24MatchEvent(minute, V24MatchEventType.GOAL,
                "team-A", playerId, "Scorer", null, null, 0.35, "Goal");
    }

    private V24MatchEvent shotEvent(String playerId, int minute) {
        return new V24MatchEvent(minute, V24MatchEventType.SHOT,
                "team-A", playerId, "Shooter", null, null, 0.28, "Shot");
    }

    private V24MatchEvent substitutionEvent(String playerId, int minute) {
        return new V24MatchEvent(minute, V24MatchEventType.SUBSTITUTION,
                "team-A", playerId, "Player Out", null, null, 0.0, "Substitution");
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
    void masterFlagFalse_plus_persistFatigueTrue_disablesFatiguePersistence() {
        V24CareerMutationPolicy p = policy(false, false, true, false, false);
        assertFalse(p.isFatiguePersistenceEnabled());
    }

    @Test
    void masterFlagTrue_plus_persistFatigueFalse_disablesFatiguePersistence() {
        V24CareerMutationPolicy p = policy(true, false, false, false, false);
        assertFalse(p.isFatiguePersistenceEnabled());
    }

    @Test
    void masterFlagTrue_plus_persistFatigueTrue_enablesFatiguePersistence() {
        V24CareerMutationPolicy p = policy(true, false, true, false, false);
        assertTrue(p.isFatiguePersistenceEnabled());
    }

    @Test
    void persistInjuriesTrue_alone_doesNotEnableFatigue() {
        V24CareerMutationPolicy p = policy(true, true, false, false, false);
        assertFalse(p.isFatiguePersistenceEnabled());
    }

    @Test
    void persistDetail_doesNotAffectFatiguePolicy() {
        V24CareerMutationPolicy p = policy(true, true, true, false, false);
        assertTrue(p.isFatiguePersistenceEnabled());
    }

    // ========== Applier null-guard tests ==========

    @Test
    void nullCareer_returnsZero() {
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        assertEquals(0, applier.applyFatigue(null, res, pol));
    }

    @Test
    void nullResult_returnsZero() {
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);
        CareerSave career = careerWithPlayers("p1");
        assertEquals(0, applier.applyFatigue(career, null, pol));
    }

    @Test
    void nullPolicy_returnsZero() {
        CareerSave career = careerWithPlayers("p1");
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        assertEquals(0, applier.applyFatigue(career, res, null));
    }

    // ========== Applier flag-disabled tests ==========

    @Test
    void flagsDisabled_returnsZeroAndDoesNotMutateEnergy() {
        CareerSave career = careerWithPlayers("p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(false, false, false, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(0, count);
        assertEquals(100, p.getEnergy());
    }

    // ========== Applier basic behavior tests ==========

    @Test
    void noTimelineEvents_returnsZero() {
        CareerSave career = careerWithPlayers("p1");
        V24DetailedMatchResult res = result();
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(0, count);
    }

    @Test
    void oneEventForMatchingPlayer_reducesEnergy() {
        CareerSave career = careerWithPlayer("p1", "Active Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(1, count);
        assertEquals(88, p.getEnergy());
    }

    @Test
    void energyNeverGoesBelowZero() {
        CareerSave career = careerWithPlayer("p1", "Active Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(5);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(1, count);
        assertEquals(0, p.getEnergy());
    }

    @Test
    void unknownPlayerId_skipsSafelyAndReturnsZero() {
        CareerSave career = careerWithPlayer("p1", "Known Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("unknown-player", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(0, count);
        assertEquals(100, p.getEnergy());
    }

    @Test
    void duplicateEventsForSamePlayer_drainOnce() {
        CareerSave career = careerWithPlayer("p1", "Active Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        V24DetailedMatchResult res = result(
                goalEvent("p1", 30),
                goalEvent("p1", 60),
                shotEvent("p1", 75)
        );
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(1, count);
        assertEquals(88, p.getEnergy());
    }

    @Test
    void multiplePlayersWithEvents_drainEachOnce() {
        CareerSave career = careerWithPlayers("p1", "p2", "p3");
        career.getSessionPlayer("p1").setEnergy(100);
        career.getSessionPlayer("p2").setEnergy(100);
        career.getSessionPlayer("p3").setEnergy(100);
        V24DetailedMatchResult res = result(
                goalEvent("p1", 30),
                goalEvent("p2", 65)
        );
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(2, count);
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
        assertEquals(88, career.getSessionPlayer("p2").getEnergy());
        assertEquals(100, career.getSessionPlayer("p3").getEnergy());
    }

    @Test
    void relatedPlayerId_alsoCountsAsParticipation() {
        CareerSave career = careerWithPlayers("scorer", "assistant");
        career.getSessionPlayer("scorer").setEnergy(100);
        career.getSessionPlayer("assistant").setEnergy(100);
        V24MatchEvent goalWithAssist = new V24MatchEvent(45, V24MatchEventType.GOAL,
                "team-A", "scorer", "Scorer", "assistant", "Assistant",
                0.35, "Goal with assist");
        V24DetailedMatchResult res = result(goalWithAssist);
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(2, count);
        assertEquals(88, career.getSessionPlayer("scorer").getEnergy());
        assertEquals(88, career.getSessionPlayer("assistant").getEnergy());
    }

    @Test
    void nonParticipatingPlayers_areNotMutated() {
        CareerSave career = careerWithPlayers("p1", "p2");
        career.getSessionPlayer("p1").setEnergy(100);
        career.getSessionPlayer("p2").setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(1, count);
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
        assertEquals(100, career.getSessionPlayer("p2").getEnergy());
    }

    @Test
    void doesNotModifyInjuryFields() {
        CareerSave career = careerWithPlayer("p1", "Any Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        p.setInjured(false);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        applier.applyFatigue(career, res, pol);

        assertFalse(p.getInjured());
    }

    @Test
    void doesNotModifyForm() {
        CareerSave career = careerWithPlayer("p1", "Any Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        p.setForm(75);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        applier.applyFatigue(career, res, pol);

        assertEquals(75, p.getForm());
    }

    
    @Test
    void injuredPlayer_isSkipped() {
        CareerSave career = careerWithPlayer("p1", "Injured Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        p.setInjured(true);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(0, count);
        assertEquals(100, p.getEnergy());
    }

    @Test
    void substituteOnlyPlayers_drainLess() {
        CareerSave career = careerWithPlayer("p1", "Sub Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        V24DetailedMatchResult res = result(substitutionEvent("p1", 60));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(1, count);
        assertEquals(94, p.getEnergy());
    }

    @Test
    void fullMatchPlayer_takesFullDrain_notSubDrain() {
        CareerSave career = careerWithPlayer("p1", "Full Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(1, count);
        assertEquals(88, p.getEnergy());
    }

    @Test
    void playerWithBothSubAndGoalEvent_takesFullDrain() {
        CareerSave career = careerWithPlayer("p1", "Mixed Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        V24DetailedMatchResult res = result(
                substitutionEvent("p1", 30),
                goalEvent("p1", 60)
        );
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(1, count);
        assertEquals(88, p.getEnergy());
    }

    @Test
    void deterministicNoRandomBehavior() {
        CareerSave career1 = careerWithPlayer("p1", "Player One", "MID");
        CareerSave career2 = careerWithPlayer("p1", "Player One", "MID");
        career1.getSessionPlayer("p1").setEnergy(100);
        career2.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        applier.applyFatigue(career1, res, pol);
        applier.applyFatigue(career2, res, pol);

        assertEquals(career1.getSessionPlayer("p1").getEnergy(),
                    career2.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void defaultDrainValuesAreUsedWhenNoConstructorArgs() {
        V24FatigueMutationApplier defaultApplier = new V24FatigueMutationApplier();
        CareerSave career = careerWithPlayer("p1", "Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        defaultApplier.applyFatigue(career, res, pol);

        assertEquals(88, p.getEnergy());
    }

    @Test
    void customDrainValuesAreUsed() {
        V24FatigueMutationApplier customApplier = new V24FatigueMutationApplier(5, 2);
        CareerSave career = careerWithPlayer("p1", "Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        customApplier.applyFatigue(career, res, pol);

        assertEquals(95, p.getEnergy());
    }

    @Test
    void nullEnergy_defaultsTo100() {
        CareerSave career = careerWithPlayer("p1", "Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(null);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(1, count);
        assertEquals(88, p.getEnergy());
    }

    // ========== V24D6F3: Edge Case Regression Tests ==========

    @Test
    void allPlayersInjured_noEnergyDrained() {
        CareerSave career = careerWithPlayers("p1", "p2", "p3");
        career.getSessionPlayer("p1").setEnergy(100);
        career.getSessionPlayer("p2").setEnergy(100);
        career.getSessionPlayer("p3").setEnergy(100);
        career.getSessionPlayer("p1").setInjured(true);
        career.getSessionPlayer("p2").setInjured(true);
        career.getSessionPlayer("p3").setInjured(true);

        V24DetailedMatchResult res = result(
                goalEvent("p1", 30),
                goalEvent("p2", 60),
                goalEvent("p3", 75)
        );
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(0, count);
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
        assertEquals(100, career.getSessionPlayer("p2").getEnergy());
        assertEquals(100, career.getSessionPlayer("p3").getEnergy());
    }

    @Test
    void substituteEventWithRelatedPlayerId_takesSubDrain() {
        CareerSave career = careerWithPlayer("p1", "Sub Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        // p1 appears only in SUBSTITUTION (both playerId and relatedPlayerId)
        V24MatchEvent subEvent = new V24MatchEvent(60, V24MatchEventType.SUBSTITUTION,
                "team-A", "p1", "Player Out", "p_sub", "Player In",
                0.0, "Substitution");
        V24DetailedMatchResult res = result(subEvent);
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(1, count);
        assertEquals(94, p.getEnergy());
    }

    @Test
    void timelineWithNullPlayerIdEvents_handledGracefully() {
        CareerSave career = careerWithPlayer("p1", "Player", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setEnergy(100);
        // null playerId event and a valid event
        V24MatchEvent nullEvent = new V24MatchEvent(30, V24MatchEventType.SHOT,
                "team-A", null, null, null, null, 0.2, "Shot");
        V24MatchEvent validEvent = goalEvent("p1", 60);
        V24DetailedMatchResult res = result(nullEvent, validEvent);
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int count = applier.applyFatigue(career, res, pol);

        assertEquals(1, count);
        assertEquals(88, p.getEnergy());
    }
}