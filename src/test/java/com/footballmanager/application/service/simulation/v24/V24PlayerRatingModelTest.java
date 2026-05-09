package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D3B: Tests for V24PlayerRatingModel.
 * Validates rating computation, bonuses, penalties, clamping, determinism.
 */
class V24PlayerRatingModelTest {

    private final V24PlayerRatingModel model = new V24PlayerRatingModel();

    // ========== Base Rating ==========

    @Test
    void baseRatingIsSix() {
        var timeline = new V24MatchTimeline();
        assertEquals(6.0, model.computePlayerRating("p1", timeline), 0.001);
    }

    @Test
    void noEventsGivesBaseRating() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(1, V24MatchEventType.SHOT, "team", "other", "other", null, null, 0.10, "shot"));
        assertEquals(6.0, model.computePlayerRating("p1", timeline), 0.001);
    }

    // ========== Goal Bonuses ==========

    @Test
    void goalIncreasesRatingBy08() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.GOAL, "team", "p1", "Player One", null, null, 0.35, "goal"));
        assertEquals(6.8, model.computePlayerRating("p1", timeline), 0.001);
    }

    @Test
    void twoGoalsIncreaseRatingBy16() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.GOAL, "team", "p1", "Player One", null, null, 0.35, "goal"));
        timeline.addEvent(event(45, V24MatchEventType.GOAL, "team", "p1", "Player One", null, null, 0.20, "goal"));
        assertEquals(7.6, model.computePlayerRating("p1", timeline), 0.001);
    }

    // ========== Assist Bonus ==========

    @Test
    void assistIncreasesRatingBy05() {
        var timeline = new V24MatchTimeline();
        // GOAL: p1 is scorer, p2 is assist provider
        timeline.addEvent(event(12, V24MatchEventType.GOAL, "team", "p1", "Scorer", "p2", "AssistProvider", 0.35, "goal"));
        // Rating for assist provider (p2)
        assertEquals(6.5, model.computePlayerRating("p2", timeline), 0.001);
        // Rating for scorer (p1) — goal bonus only, no double-count
        assertEquals(6.8, model.computePlayerRating("p1", timeline), 0.001);
    }

    @Test
    void multipleAssistsStack() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.GOAL, "team", "pX", "X", "p1", "Player One", 0.30, "g1"));
        timeline.addEvent(event(34, V24MatchEventType.GOAL, "team", "pY", "Y", "p1", "Player One", 0.25, "g2"));
        timeline.addEvent(event(78, V24MatchEventType.GOAL, "team", "pZ", "Z", "p1", "Player One", 0.20, "g3"));
        assertEquals(7.5, model.computePlayerRating("p1", timeline), 0.001); // 6.0 + 3*0.5 = 7.5
    }

    // ========== Shot Bonus ==========

    @Test
    void shotAddsSmallBonus() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.SHOT, "team", "p1", "Player One", null, null, 0.10, "shot"));
        assertEquals(6.10, model.computePlayerRating("p1", timeline), 0.001);
    }

    @Test
    void highXgShotAddsExtraBonus() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.SHOT, "team", "p1", "Player One", null, null, 0.40, "shot"));
        // 0.10 shot bonus + 0.05 high-xg bonus = 0.15
        assertEquals(6.15, model.computePlayerRating("p1", timeline), 0.001);
    }

    @Test
    void shotAtExactly030ThresholdGetsBonus() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.SHOT, "team", "p1", "Player One", null, null, 0.30, "shot"));
        assertEquals(6.15, model.computePlayerRating("p1", timeline), 0.001);
    }

    @Test
    void shotBelow030ThresholdOnlyGetsBaseShotBonus() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.SHOT, "team", "p1", "Player One", null, null, 0.29, "shot"));
        assertEquals(6.10, model.computePlayerRating("p1", timeline), 0.001);
    }

    // ========== Key Pass Bonus ==========

    @Test
    void keyPassIncreasesRatingBy03() {
        var timeline = new V24MatchTimeline();
        // SHOT: p1 is shooter, p2 is key-pass provider
        timeline.addEvent(event(12, V24MatchEventType.SHOT, "team", "p1", "Shooter", "p2", "KeyPasser", 0.12, "shot"));
        // Key-pass provider (p2) gets +0.3
        assertEquals(6.30, model.computePlayerRating("p2", timeline), 0.001);
        // Shooter (p1) gets +0.10 shot bonus
        assertEquals(6.10, model.computePlayerRating("p1", timeline), 0.001);
    }

    // ========== Card Penalties ==========

    @Test
    void yellowCardDecreasesBy03Each() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.YELLOW_CARD, "team", "p1", "Player One", null, null, 0, "foul"));
        assertEquals(5.7, model.computePlayerRating("p1", timeline), 0.001);
    }

    @Test
    void twoYellowCardsDecreaseBy06() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.YELLOW_CARD, "team", "p1", "Player One", null, null, 0, "yc1"));
        timeline.addEvent(event(67, V24MatchEventType.YELLOW_CARD, "team", "p1", "Player One", null, null, 0, "yc2"));
        assertEquals(5.4, model.computePlayerRating("p1", timeline), 0.001);
    }

    @Test
    void redCardDecreasesBy15() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(78, V24MatchEventType.RED_CARD, "team", "p1", "Player One", null, null, 0, "red"));
        assertEquals(4.5, model.computePlayerRating("p1", timeline), 0.001);
    }

    // ========== Injury Penalty ==========

    @Test
    void injuryPenaltyApplied() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(55, V24MatchEventType.INJURY, "team", "p1", "Player One", null, null, 0, "injury"));
        assertEquals(5.8, model.computePlayerRating("p1", timeline), 0.001);
    }

    // ========== Foul Penalty ==========

    @Test
    void foulPenaltySmall() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(33, V24MatchEventType.FOUL, "team", "p1", "Player One", null, null, 0, "foul"));
        assertEquals(5.95, model.computePlayerRating("p1", timeline), 0.001);
    }

    // ========== Clamping ==========

    @Test
    void ratingClampedToTen() {
        var timeline = new V24MatchTimeline();
        // 6 goals + 6 assists at maximum: 6.0 + 6*0.8 + 6*0.5 = 6.0 + 4.8 + 3.0 = 13.8
        for (int i = 0; i < 6; i++) {
            int min = 10 + i * 10;
            timeline.addEvent(event(min, V24MatchEventType.GOAL, "team", "p1", "P1", "pA" + i, "PA", 0.3, "g"));
        }
        assertEquals(10.0, model.computePlayerRating("p1", timeline), 0.001);
    }

    @Test
    void ratingClampedToOne() {
        var timeline = new V24MatchTimeline();
        // 4 red cards + 10 fouls: 6.0 - 4*1.5 - 10*0.05 = 6.0 - 6.0 - 0.5 = -0.5
        for (int i = 0; i < 4; i++) {
            timeline.addEvent(event(10 + i * 20, V24MatchEventType.RED_CARD, "team", "p1", "P1", null, null, 0, "r"));
        }
        for (int i = 0; i < 10; i++) {
            timeline.addEvent(event(15 + i * 8, V24MatchEventType.FOUL, "team", "p1", "P1", null, null, 0, "f"));
        }
        assertEquals(1.0, model.computePlayerRating("p1", timeline), 0.001);
    }

    // ========== Determinism ==========

    @Test
    void sameTimelineProducesSameRating() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.GOAL, "team", "p1", "P1", null, null, 0.35, "goal"));
        timeline.addEvent(event(45, V24MatchEventType.YELLOW_CARD, "team", "p1", "P1", null, null, 0, "yc"));

        double r1 = model.computePlayerRating("p1", timeline);
        double r2 = model.computePlayerRating("p1", timeline);
        assertEquals(r1, r2, 0.001);
    }

    // ========== Multi-Player ==========

    @Test
    void computeRatingsReturnsAllRequestedPlayers() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.GOAL, "team", "p1", "P1", null, null, 0.35, "goal"));

        Map<String, Double> ratings = model.computeRatings(List.of("p1", "p2", "p3"), timeline);

        assertEquals(3, ratings.size());
        assertTrue(ratings.containsKey("p1"));
        assertTrue(ratings.containsKey("p2"));
        assertTrue(ratings.containsKey("p3"));
        assertEquals(6.8, ratings.get("p1"), 0.001); // goal scorer
        assertEquals(6.0, ratings.get("p2"), 0.001); // no events
        assertEquals(6.0, ratings.get("p3"), 0.001); // no events
    }

    @Test
    void unrelatedEventsDoNotAffectPlayer() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.GOAL, "team", "other", "Other", null, null, 0.35, "goal"));
        timeline.addEvent(event(33, V24MatchEventType.YELLOW_CARD, "team", "another", "Another", null, null, 0, "yc"));
        timeline.addEvent(event(55, V24MatchEventType.SHOT, "team", "third", "Third", null, null, 0.10, "shot"));
        // p1 has no events
        assertEquals(6.0, model.computePlayerRating("p1", timeline), 0.001);
    }

    // ========== Substitution Bonus ==========

    @Test
    void substitutionIncomingGets05() {
        var timeline = new V24MatchTimeline();
        // Substitution: pOff is playerId (coming off), pOn is relatedPlayerId (coming on)
        timeline.addEvent(event(60, V24MatchEventType.SUBSTITUTION, "team", "pOff", "Off", "pOn", "On", 0, "sub"));
        // Incoming player gets +0.05
        assertEquals(6.05, model.computePlayerRating("pOn", timeline), 0.001);
        // Outgoing player gets nothing (no penalty per spec)
        assertEquals(6.0, model.computePlayerRating("pOff", timeline), 0.001);
    }

    // ========== Event type coverage ==========

    @Test
    void chanceCreatedDoesNotAffectRating() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.CHANCE_CREATED, "team", "p1", "P1", null, null, 0, "chance"));
        assertEquals(6.0, model.computePlayerRating("p1", timeline), 0.001);
    }

    @Test
    void saveDoesNotAffectRating() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.SAVE, "team", "p1", "P1", null, null, 0, "save"));
        assertEquals(6.0, model.computePlayerRating("p1", timeline), 0.001);
    }

    @Test
    void missDoesNotAffectRating() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.MISS, "team", "p1", "P1", null, null, 0, "miss"));
        assertEquals(6.0, model.computePlayerRating("p1", timeline), 0.001);
    }

    // ========== Null handling ==========

    @Test
    void rejectsNullPlayerId() {
        var timeline = new V24MatchTimeline();
        assertThrows(NullPointerException.class, () ->
                model.computePlayerRating(null, timeline));
    }

    @Test
    void rejectsNullTimeline() {
        assertThrows(NullPointerException.class, () ->
                model.computePlayerRating("p1", null));
    }

    @Test
    void rejectsNullPlayerIdCollection() {
        var timeline = new V24MatchTimeline();
        assertThrows(NullPointerException.class, () ->
                model.computeRatings(null, timeline));
    }

    @Test
    void rejectsNullTimelineInComputeRatings() {
        assertThrows(NullPointerException.class, () ->
                model.computeRatings(List.of("p1"), null));
    }

    // ========== Edge cases ==========

    @Test
    void multipleEventsOnSameMinuteAllCounted() {
        var timeline = new V24MatchTimeline();
        // Multiple events at same minute for same player
        timeline.addEvent(event(45, V24MatchEventType.SHOT, "team", "p1", "P1", null, null, 0.10, "s1"));
        timeline.addEvent(event(45, V24MatchEventType.FOUL, "team", "p1", "P1", null, null, 0, "f1"));
        // +0.10 shot + (-0.05 foul) = +0.05
        assertEquals(6.05, model.computePlayerRating("p1", timeline), 0.001);
    }

    @Test
    void clampRatingEdgeCases() {
        assertEquals(1.0, model.clampRating(0.5), 0.001);
        assertEquals(10.0, model.clampRating(10.5), 0.001);
        assertEquals(6.5, model.clampRating(6.5), 0.001);
    }

    // Helper to build events without repeating parameter lists
    private V24MatchEvent event(int minute, V24MatchEventType type, String teamId,
                                 String playerId, String playerName,
                                 String relatedPlayerId, String relatedPlayerName,
                                 double xg, String desc) {
        return new V24MatchEvent(minute, type, teamId, playerId, playerName,
                relatedPlayerId, relatedPlayerName, xg, desc);
    }
}