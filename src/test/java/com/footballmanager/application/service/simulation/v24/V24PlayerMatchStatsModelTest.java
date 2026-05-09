package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D4A: Tests for V24PlayerMatchStatsModel.
 * Validates stat bundle derivation from timeline and rating integration.
 */
class V24PlayerMatchStatsModelTest {

    private final V24PlayerMatchStatsModel model = new V24PlayerMatchStatsModel();

    // ========== Base / Empty ==========

    @Test
    void handlesEmptyTimeline() {
        var players = makePlayers("p1", "p2");
        var timeline = new V24MatchTimeline();

        var ratings = model.computeRatings(players, timeline);

        assertEquals(2, ratings.size());
        for (V24PlayerMatchRatingDto dto : ratings) {
            assertEquals(6.0, dto.rating(), 0.001); // base rating
            assertEquals(0, dto.goals());
            assertEquals(0, dto.assists());
        }
    }

    @Test
    void includesPlayersWithNoEventsAtBaseRating() {
        var players = makePlayers("p1", "p2", "p3");
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(10, V24MatchEventType.GOAL, "home", "p1", "P1", null, null, 0.3, "goal"));

        var ratings = model.computeRatings(players, timeline);

        assertEquals(3, ratings.size());
        Map<String, V24PlayerMatchRatingDto> map = byPlayerId(ratings);

        assertEquals(6.8, map.get("p1").rating(), 0.001); // goal scorer
        assertEquals(6.0, map.get("p2").rating(), 0.001); // no events
        assertEquals(6.0, map.get("p3").rating(), 0.001); // no events
    }

    // ========== Goal / Assist Counting ==========

    @Test
    void countsGoalsFromGoalEvents() {
        var players = makePlayers("p1", "p2");
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.GOAL, "home", "p1", "P1", null, null, 0.3, "g1"));
        timeline.addEvent(event(45, V24MatchEventType.GOAL, "home", "p1", "P1", null, null, 0.25, "g2"));
        timeline.addEvent(event(67, V24MatchEventType.GOAL, "home", "p2", "P2", null, null, 0.2, "g3"));

        var ratings = model.computeRatings(players, timeline);
        Map<String, V24PlayerMatchRatingDto> map = byPlayerId(ratings);

        assertEquals(2, map.get("p1").goals());
        assertEquals(1, map.get("p2").goals());
    }

    @Test
    void countsAssistsFromGoalRelatedPlayer() {
        var players = makePlayers("p1", "p2");
        var timeline = new V24MatchTimeline();
        // p1 scores, p2 assists
        timeline.addEvent(event(12, V24MatchEventType.GOAL, "home", "p1", "P1", "p2", "P2", 0.3, "g1"));
        // p2 scores unassisted
        timeline.addEvent(event(34, V24MatchEventType.GOAL, "home", "p2", "P2", null, null, 0.2, "g2"));

        var ratings = model.computeRatings(players, timeline);
        Map<String, V24PlayerMatchRatingDto> map = byPlayerId(ratings);

        assertEquals(0, map.get("p1").assists()); // scorer, not assist
        assertEquals(1, map.get("p2").assists()); // assist on p1's goal
        assertEquals(1, map.get("p2").goals());   // own goal
    }

    @Test
    void countsMultipleAssists() {
        var players = makePlayers("pA", "pB", "pC");
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(10, V24MatchEventType.GOAL, "home", "pX", "X", "pA", "A", 0.3, "g1"));
        timeline.addEvent(event(25, V24MatchEventType.GOAL, "home", "pY", "Y", "pA", "A", 0.25, "g2"));
        timeline.addEvent(event(40, V24MatchEventType.GOAL, "home", "pZ", "Z", "pA", "A", 0.2, "g3"));

        var ratings = model.computeRatings(players, timeline);
        Map<String, V24PlayerMatchRatingDto> map = byPlayerId(ratings);

        assertEquals(3, map.get("pA").assists()); // 3 assists
        assertEquals(0, map.get("pA").goals());
    }

    // ========== Shot / Key Pass Counting ==========

    @Test
    void countsShotsFromShotEvents() {
        var players = makePlayers("p1", "p2");
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.SHOT, "home", "p1", "P1", null, null, 0.1, "s1"));
        timeline.addEvent(event(30, V24MatchEventType.SHOT, "home", "p1", "P1", null, null, 0.15, "s2"));
        timeline.addEvent(event(55, V24MatchEventType.SHOT, "home", "p2", "P2", null, null, 0.08, "s3"));

        var ratings = model.computeRatings(players, timeline);
        Map<String, V24PlayerMatchRatingDto> map = byPlayerId(ratings);

        assertEquals(2, map.get("p1").shots());
        assertEquals(1, map.get("p2").shots());
    }

    @Test
    void countsKeyPassesFromShotRelatedPlayer() {
        var players = makePlayers("p1", "p2");
        var timeline = new V24MatchTimeline();
        // p1 shoots, p2 provides key pass
        timeline.addEvent(event(12, V24MatchEventType.SHOT, "home", "p1", "P1", "p2", "P2", 0.12, "shot1"));
        // p2 shoots unassisted
        timeline.addEvent(event(45, V24MatchEventType.SHOT, "home", "p2", "P2", null, null, 0.1, "shot2"));

        var ratings = model.computeRatings(players, timeline);
        Map<String, V24PlayerMatchRatingDto> map = byPlayerId(ratings);

        assertEquals(0, map.get("p1").keyPasses()); // shooter, not key-pass provider
        assertEquals(1, map.get("p2").keyPasses()); // key pass on p1's shot
        assertEquals(1, map.get("p2").shots());     // own shot
    }

    // ========== Cards / Injuries / Fouls ==========

    @Test
    void countsCardsInjuriesAndFouls() {
        var players = makePlayers("p1", "p2");
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(15, V24MatchEventType.YELLOW_CARD, "home", "p1", "P1", null, null, 0, "yc"));
        timeline.addEvent(event(30, V24MatchEventType.YELLOW_CARD, "home", "p1", "P1", null, null, 0, "yc2"));
        timeline.addEvent(event(50, V24MatchEventType.RED_CARD, "home", "p1", "P1", null, null, 0, "rc"));
        timeline.addEvent(event(40, V24MatchEventType.INJURY, "home", "p2", "P2", null, null, 0, "inj"));
        timeline.addEvent(event(20, V24MatchEventType.FOUL, "home", "p2", "P2", null, null, 0, "foul"));

        var ratings = model.computeRatings(players, timeline);
        Map<String, V24PlayerMatchRatingDto> map = byPlayerId(ratings);

        assertEquals(2, map.get("p1").yellowCards());
        assertEquals(1, map.get("p1").redCards());
        assertEquals(1, map.get("p2").injuries());
        assertEquals(1, map.get("p2").fouls());
    }

    // ========== Substitutions ==========

    @Test
    void marksSubstitutedOutAndIn() {
        var players = makePlayers("pOff", "pOn", "pStay");
        var timeline = new V24MatchTimeline();
        // pOff comes off, pOn comes on
        timeline.addEvent(event(60, V24MatchEventType.SUBSTITUTION, "home", "pOff", "Off", "pOn", "On", 0, "sub"));

        var ratings = model.computeRatings(players, timeline);
        Map<String, V24PlayerMatchRatingDto> map = byPlayerId(ratings);

        assertTrue(map.get("pOff").substitutedOut());
        assertFalse(map.get("pOff").substitutedIn());
        assertTrue(map.get("pOn").substitutedIn());
        assertFalse(map.get("pOn").substitutedOut());
        assertFalse(map.get("pStay").substitutedIn());
        assertFalse(map.get("pStay").substitutedOut());
    }

    // ========== Rating Integration ==========

    @Test
    void ratingMatchesV24PlayerRatingModel() {
        var players = makePlayers("p1");
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.GOAL, "home", "p1", "P1", null, null, 0.3, "goal"));
        timeline.addEvent(event(45, V24MatchEventType.YELLOW_CARD, "home", "p1", "P1", null, null, 0, "yc"));

        var ratings = model.computeRatings(players, timeline);

        assertEquals(1, ratings.size());
        // 6.0 + 0.8 (goal) - 0.3 (yellow) = 6.5
        assertEquals(6.5, ratings.get(0).rating(), 0.001);
    }

    @Test
    void unrelatedEventsDoNotAffectPlayer() {
        var players = makePlayers("p1", "p2");
        var timeline = new V24MatchTimeline();
        // only p2 events
        timeline.addEvent(event(12, V24MatchEventType.GOAL, "home", "p2", "P2", null, null, 0.3, "goal"));
        timeline.addEvent(event(33, V24MatchEventType.YELLOW_CARD, "home", "p2", "P2", null, null, 0, "yc"));

        var ratings = model.computeRatings(players, timeline);
        Map<String, V24PlayerMatchRatingDto> map = byPlayerId(ratings);

        assertEquals(6.0, map.get("p1").rating(), 0.001); // no events
        assertEquals(0, map.get("p1").goals());
        assertEquals(6.5, map.get("p2").rating(), 0.001);  // 6.0 + 0.8 - 0.3
        assertEquals(1, map.get("p2").goals());
    }

    // ========== Determinism ==========

    @Test
    void deterministicForSameTimeline() {
        var players = makePlayers("p1", "p2");
        var timeline = new V24MatchTimeline();
        timeline.addEvent(event(12, V24MatchEventType.GOAL, "home", "p1", "P1", "p2", "P2", 0.35, "goal"));

        var r1 = model.computeRatings(players, timeline);
        var r2 = model.computeRatings(players, timeline);

        assertEquals(r1.size(), r2.size());
        for (int i = 0; i < r1.size(); i++) {
            assertEquals(r1.get(i).rating(), r2.get(i).rating(), 0.001);
            assertEquals(r1.get(i).goals(), r2.get(i).goals());
            assertEquals(r1.get(i).assists(), r2.get(i).assists());
        }
    }

    // ========== Null Safety ==========

    @Test
    void rejectsNullPlayersCollection() {
        var timeline = new V24MatchTimeline();
        assertThrows(NullPointerException.class, () ->
                model.computeRatings(null, timeline));
    }

    @Test
    void rejectsNullTimeline() {
        var players = makePlayers("p1");
        assertThrows(NullPointerException.class, () ->
                model.computeRatings(players, null));
    }

    // ========== Helpers ==========

    private List<V24PlayerMatchState> makePlayers(String... ids) {
        List<V24PlayerMatchState> players = new ArrayList<>();
        for (String id : ids) {
            players.add(makePlayer(id, "Team"));
        }
        return players;
    }

    private V24PlayerMatchState makePlayer(String playerId, String teamId) {
        SessionPlayer sp = SessionPlayer.custom(
                playerId, 25, "MID",
                70, 50, 70, 80, 80, 80,
                new java.math.BigDecimal("1000000")
        );
        sp.setSessionPlayerId(playerId);
        return V24PlayerMatchState.fromSessionPlayer(sp, teamId);
    }

    private V24MatchEvent event(int minute, V24MatchEventType type, String teamId,
                                  String playerId, String playerName,
                                  String relatedPlayerId, String relatedPlayerName,
                                  double xg, String desc) {
        return new V24MatchEvent(minute, type, teamId, playerId, playerName,
                relatedPlayerId, relatedPlayerName, xg, desc);
    }

    private Map<String, V24PlayerMatchRatingDto> byPlayerId(List<V24PlayerMatchRatingDto> list) {
        return list.stream().collect(Collectors.toMap(V24PlayerMatchRatingDto::playerId, Function.identity()));
    }
}