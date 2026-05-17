package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6D3: Unit tests for V24DisciplineMutationApplier.
 * Tests discipline mutation behavior in isolation — no Redis, no Spring, no IO.
 */
class V24DisciplineMutationApplierTest {

    private final V24DisciplineMutationApplier applier = new V24DisciplineMutationApplier();

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

    private V24MatchEvent yellowCardEvent(String playerId, int minute) {
        return new V24MatchEvent(minute, V24MatchEventType.YELLOW_CARD,
                "team-A", playerId, "Card Player", null, null, 0.0, "Foul");
    }

    private V24MatchEvent redCardEvent(String playerId, int minute) {
        return new V24MatchEvent(minute, V24MatchEventType.RED_CARD,
                "team-A", playerId, "Sent Off", null, null, 0.0, "Second yellow");
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

    // ========== Null-guard tests ==========

    @Test
    void nullCareer_returnsZero() {
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);
        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        assertEquals(0, applier.applyDiscipline(null, res, pol));
    }

    @Test
    void nullResult_returnsZero() {
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);
        CareerSave career = careerWithPlayers("p1");
        assertEquals(0, applier.applyDiscipline(career, null, pol));
    }

    @Test
    void nullPolicy_returnsZero() {
        CareerSave career = careerWithPlayers("p1");
        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        assertEquals(0, applier.applyDiscipline(career, res, null));
    }

    // ========== Disabled-policy tests ==========

    @Test
    void disabledPolicy_noMutation() {
        CareerSave career = careerWithPlayers("p1");
        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        // Master false, discipline true
        V24CareerMutationPolicy pol = policy(false, false, false, true, false);
        assertEquals(0, applier.applyDiscipline(career, res, pol));

        // Master true, discipline false
        pol = policy(true, false, false, false, false);
        assertEquals(0, applier.applyDiscipline(career, res, pol));
    }

    @Test
    void emptyTimeline_noMutation() {
        CareerSave career = careerWithPlayers("p1");
        V24DetailedMatchResult res = result(); // no events
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);
        assertEquals(0, applier.applyDiscipline(career, res, pol));
    }

    // ========== YELLOW_CARD tests ==========

    @Test
    void yellowCard_incrementsYellowCards() {
        CareerSave career = careerWithPlayer("player-yc-1", "Yellow Card Guy", "MID");
        V24DetailedMatchResult res = result(yellowCardEvent("player-yc-1", 30));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(1, applied);
        SessionPlayer p = career.getSessionPlayer("player-yc-1");
        assertEquals(1, p.getYellowCards());
        assertEquals(0, p.getRedCards());
        assertFalse(p.getSuspended());
        assertEquals(0, p.getSuspensionRemainingMatches());
    }

    @Test
    void yellowCard_unknownPlayer_skipped() {
        CareerSave career = careerWithPlayers("known-player");
        V24DetailedMatchResult res = result(yellowCardEvent("unknown-player", 30));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(0, applied);
    }

    @Test
    void nullPlayerId_skipped() {
        CareerSave career = careerWithPlayers("p1");
        V24MatchEvent nullIdEvent = new V24MatchEvent(30, V24MatchEventType.YELLOW_CARD,
                "team-A", null, "Null Player", null, null, 0.0, "Foul");
        V24DetailedMatchResult res = result(nullIdEvent);
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(0, applied);
    }

    @Test
    void blankPlayerId_skipped() {
        CareerSave career = careerWithPlayers("p1");
        V24MatchEvent blankIdEvent = new V24MatchEvent(30, V24MatchEventType.YELLOW_CARD,
                "team-A", "   ", "Blank Player", null, null, 0.0, "Foul");
        V24DetailedMatchResult res = result(blankIdEvent);
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(0, applied);
    }

    // ========== RED_CARD tests ==========

    @Test
    void redCard_setsSuspendedAndOneMatchSuspension() {
        CareerSave career = careerWithPlayer("player-rc-1", "Red Card Guy", "DEF");
        V24DetailedMatchResult res = result(redCardEvent("player-rc-1", 70));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(1, applied);
        SessionPlayer p = career.getSessionPlayer("player-rc-1");
        assertEquals(1, p.getRedCards());
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
        assertEquals(0, p.getYellowCards());
    }

    // ========== Yellow + Red combined tests ==========

    @Test
    void yellowAndRed_samePlayer_countsCorrectly() {
        CareerSave career = careerWithPlayer("player-yr-1", "Yellow then Red Guy", "ATT");
        V24MatchEvent yellow = yellowCardEvent("player-yr-1", 25);
        V24MatchEvent red = redCardEvent("player-yr-1", 70);
        V24DetailedMatchResult res = result(yellow, red);
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(2, applied);
        SessionPlayer p = career.getSessionPlayer("player-yr-1");
        assertEquals(1, p.getYellowCards());
        assertEquals(1, p.getRedCards());
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    // ========== Multiple players tests ==========

    @Test
    void multiplePlayers_cardsAppliedIndependently() {
        CareerSave career = careerWithPlayers("player-A", "player-B", "player-C");
        V24MatchEvent ycA = yellowCardEvent("player-A", 20);
        V24MatchEvent rcB = redCardEvent("player-B", 65);
        V24MatchEvent ycC = yellowCardEvent("player-C", 40);
        V24DetailedMatchResult res = result(ycA, rcB, ycC);
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(3, applied);
        assertEquals(1, career.getSessionPlayer("player-A").getYellowCards());
        assertEquals(1, career.getSessionPlayer("player-B").getRedCards());
        assertTrue(career.getSessionPlayer("player-B").getSuspended());
        assertEquals(1, career.getSessionPlayer("player-C").getYellowCards());
    }

    // ========== Duplicate card tests ==========

    @Test
    void duplicateRedCard_sameMatch_countsOnce() {
        CareerSave career = careerWithPlayer("player-dup-rc", "Duplicate Red Guy", "DEF");
        V24MatchEvent rc1 = redCardEvent("player-dup-rc", 70);
        V24MatchEvent rc2 = redCardEvent("player-dup-rc", 72); // second red in same match
        V24DetailedMatchResult res = result(rc1, rc2);
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(1, applied);
        SessionPlayer p = career.getSessionPlayer("player-dup-rc");
        assertEquals(1, p.getRedCards()); // incremented once
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    @Test
    void multipleYellowCards_samePlayer_allCount() {
        CareerSave career = careerWithPlayer("player-dup-yc", "Duplicate Yellow Guy", "MID");
        V24MatchEvent yc1 = yellowCardEvent("player-dup-yc", 30);
        V24MatchEvent yc2 = yellowCardEvent("player-dup-yc", 55); // second yellow in same match
        V24DetailedMatchResult res = result(yc1, yc2);
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(2, applied);
        SessionPlayer p = career.getSessionPlayer("player-dup-yc");
        assertEquals(2, p.getYellowCards());
        assertFalse(p.getSuspended()); // no red card
        assertEquals(0, p.getRedCards());
    }

    // ========== Already-suspended tests ==========

    @Test
    void alreadySuspended_redCard_resetsOneMatchSuspensionAndIncrementsRed() {
        CareerSave career = careerWithPlayer("player-susp", "Suspended Guy", "DEF");
        SessionPlayer p = career.getSessionPlayer("player-susp");
        p.setSuspended(true);
        p.setRedCards(1);
        p.setSuspensionRemainingMatches(3); // was serving a 3-match ban

        V24DetailedMatchResult res = result(redCardEvent("player-susp", 60));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(1, applied);
        assertEquals(2, p.getRedCards()); // incremented
        assertTrue(p.getSuspended()); // still suspended
        assertEquals(1, p.getSuspensionRemainingMatches()); // reset to 1 per MVP rule
    }

    // ========== V24D6H2 Yellow-card threshold tests ==========

    // 1. belowThreshold_noSuspension
    @Test
    void belowThreshold_noSuspension() {
        CareerSave career = careerWithPlayer("p1", "Below Threshold", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(3);

        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(1, applied);
        assertEquals(4, p.getYellowCards());
        assertFalse(p.getSuspended());
        assertEquals(0, p.getSuspensionRemainingMatches());
    }

    // 2. exactlyThreshold_setsSuspendedAndRemainingOne
    @Test
    void exactlyThreshold_setsSuspendedAndRemainingOne() {
        CareerSave career = careerWithPlayer("p1", "At Threshold", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(4);

        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        // applied = 1 yellow event + 1 threshold suspension
        assertEquals(2, applied);
        assertEquals(0, p.getYellowCards());
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    // 3. aboveThreshold_subtractsThresholdOnce
    @Test
    void aboveThreshold_subtractsThresholdOnce() {
        CareerSave career = careerWithPlayer("p1", "Above Threshold", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(5);

        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(2, applied);
        assertEquals(1, p.getYellowCards()); // 6 - 5 = 1
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    // 4. exactlyThresholdAtMatchEnd_subtractsToZero
    @Test
    void exactlyThresholdAtMatchEnd_subtractsToZero() {
        CareerSave career = careerWithPlayer("p1", "Exactly Five", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(4);

        V24DetailedMatchResult res = result(yellowCardEvent("p1", 90));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(2, applied);
        assertEquals(0, p.getYellowCards()); // 5 - 5 = 0
    }

    // 5. redCardSameMatch_doesNotApplyAdditionalYellowThresholdSuspension
    @Test
    void redCardSameMatch_doesNotApplyAdditionalYellowThresholdSuspension() {
        CareerSave career = careerWithPlayer("p1", "Red and Yellow", "DEF");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(4);

        V24MatchEvent yellow = yellowCardEvent("p1", 25);
        V24MatchEvent red = redCardEvent("p1", 70);
        V24DetailedMatchResult res = result(yellow, red);
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        // applied = 1 yellow + 1 red (no extra threshold count since RED takes precedence)
        assertEquals(2, applied);
        assertEquals(5, p.getYellowCards()); // increments to 5, threshold skipped due to RED
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches()); // RED_CARD suspension
        assertEquals(1, p.getRedCards());
    }

    // 6. alreadySuspended_reachesThreshold_doesNotOverrideSuspension
    @Test
    void alreadySuspended_reachesThreshold_doesNotOverrideSuspension() {
        CareerSave career = careerWithPlayer("p1", "Already Suspended", "DEF");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(4);
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(2);

        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(1, applied);
        assertEquals(5, p.getYellowCards()); // incremented but no suspension applied
        assertTrue(p.getSuspended()); // unchanged
        assertEquals(2, p.getSuspensionRemainingMatches()); // unchanged
    }

    // 7. thresholdWithMultipleYellowsInSameMatch_appliesOnce
    @Test
    void thresholdWithMultipleYellowsInSameMatch_appliesOnce() {
        CareerSave career = careerWithPlayer("p1", "Two Yellows", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(4);

        V24MatchEvent yc1 = yellowCardEvent("p1", 30);
        V24MatchEvent yc2 = yellowCardEvent("p1", 55);
        V24DetailedMatchResult res = result(yc1, yc2);
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        // First yellow: yellowCards=5, threshold fires, suspended, yellowCards=0, appliedCount=2
        // Second yellow: yellowCards=1, no threshold, appliedCount=3
        assertEquals(3, applied);
        assertEquals(1, p.getYellowCards()); // 5 - 5 = 0, then +1 = 1
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches()); // applied only once
    }

    // 8. disabledPolicy_noThreshold
    @Test
    void disabledPolicy_noThreshold() {
        CareerSave career = careerWithPlayer("p1", "Policy Off", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(4);

        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(false, false, false, true, false); // master false

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(0, applied);
        assertEquals(4, p.getYellowCards()); // no increment
        assertFalse(p.getSuspended());
    }

    // 9. nullPlayerId_yellowThresholdSkipped
    @Test
    void nullPlayerId_yellowThresholdSkipped() {
        CareerSave career = careerWithPlayers("p1");
        V24MatchEvent nullIdEvent = new V24MatchEvent(30, V24MatchEventType.YELLOW_CARD,
                "team-A", null, "Null Player", null, null, 0.0, "Foul");
        V24DetailedMatchResult res = result(nullIdEvent);
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(0, applied);
    }

    // 10. unknownPlayer_yellowThresholdSkipped
    @Test
    void unknownPlayer_yellowThresholdSkipped() {
        CareerSave career = careerWithPlayers("p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(4);

        V24DetailedMatchResult res = result(yellowCardEvent("unknown-player", 30));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(0, applied);
        assertEquals(4, p.getYellowCards()); // unchanged
    }

    // 11. yellowCardsAccumulatesAcrossMatches
    @Test
    void yellowCardsAccumulatesAcrossMatches() {
        CareerSave career = careerWithPlayer("p1", "Accumulator", "MID");
        SessionPlayer p = career.getSessionPlayer("p1");
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        // First match: 3 yellows + 1 = 4, no suspension
        p.setYellowCards(3);
        V24DetailedMatchResult res1 = result(yellowCardEvent("p1", 30));
        int applied1 = applier.applyDiscipline(career, res1, pol);
        assertEquals(1, applied1);
        assertEquals(4, p.getYellowCards());
        assertFalse(p.getSuspended());

        // Second match: 4 yellows + 1 = threshold reached → suspension
        V24DetailedMatchResult res2 = result(yellowCardEvent("p1", 45));
        int applied2 = applier.applyDiscipline(career, res2, pol);
        assertEquals(2, applied2); // 1 yellow + 1 threshold
        assertEquals(0, p.getYellowCards()); // 5 - 5 = 0
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
    }

    // ========== Null-exising-field tests ==========

    @Test
    void nullExistingDisciplineFields_defaultSafely() throws Exception {
        CareerSave career = new CareerSave();
        SessionPlayer p = new SessionPlayer();
        p.setSessionPlayerId("null-disc-player");
        p.setName("Null Fields Player");
        p.setPosition("MID");
        // Intentionally leave discipline fields as null (uninitialized)
        career.addSessionPlayer(p);

        // Use reflection to ensure fields are null before mutation
        java.lang.reflect.Field ycField = SessionPlayer.class.getDeclaredField("yellowCards");
        ycField.setAccessible(true);
        ycField.set(p, null);
        java.lang.reflect.Field rcField = SessionPlayer.class.getDeclaredField("redCards");
        rcField.setAccessible(true);
        rcField.set(p, null);
        java.lang.reflect.Field suspField = SessionPlayer.class.getDeclaredField("suspended");
        suspField.setAccessible(true);
        suspField.set(p, null);
        java.lang.reflect.Field srmField = SessionPlayer.class.getDeclaredField("suspensionRemainingMatches");
        srmField.setAccessible(true);
        srmField.set(p, null);

        V24MatchEvent yc = yellowCardEvent("null-disc-player", 30);
        V24MatchEvent rc = redCardEvent("null-disc-player", 75);
        V24DetailedMatchResult res = result(yc, rc);
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        int applied = applier.applyDiscipline(career, res, pol);

        assertEquals(2, applied);
        assertEquals(1, p.getYellowCards()); // incremented from null → 1
        assertEquals(1, p.getRedCards()); // incremented from null → 1
        assertTrue(p.getSuspended()); // set to true
        assertEquals(1, p.getSuspensionRemainingMatches()); // set to 1
    }
}