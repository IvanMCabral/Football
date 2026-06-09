package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D4A: Tests for V24DetailedMatchData snapshot/DTO.
 */
class V24DetailedMatchDataTest {

    @Test
    void createsSnapshotWithRequiredFields() {
        var detail = new V24DetailedMatchData(
                "match-123", "career-abc", 1, 5,
                "home-team", "away-team",
                "Home Utd", "Away City",
                2, 1, 1.8, 0.9,
                12, 8,
                55, 45,
                List.of(), List.of(),
                "Home win 2-1",
                "V24", 1, Instant.now()
        );

        assertEquals("match-123", detail.matchId());
        assertEquals("career-abc", detail.careerId());
        assertEquals(1, detail.seasonNumber());
        assertEquals(5, detail.round());
        assertEquals("home-team", detail.homeTeamId());
        assertEquals("away-team", detail.awayTeamId());
        assertEquals("Home Utd", detail.homeTeamName());
        assertEquals("Away City", detail.awayTeamName());
        assertEquals(2, detail.homeGoals());
        assertEquals(1, detail.awayGoals());
        assertEquals(1.8, detail.homeXg(), 0.001);
        assertEquals(0.9, detail.awayXg(), 0.001);
        assertEquals(12, detail.homeShots());
        assertEquals(8, detail.awayShots());
        assertEquals(55, detail.homePossession());
        assertEquals(45, detail.awayPossession());
        assertEquals("Home win 2-1", detail.summary());
        assertEquals("V24", detail.engineVersion());
        assertEquals(1, detail.schemaVersion());
    }

    @Test
    void defaultsEngineVersionAndSchemaVersion() {
        var detail = new V24DetailedMatchData(
                "match-123", "career-abc", null, null,
                "home-team", "away-team",
                "Home", "Away",
                1, 0, 1.0, 0.5,
                10, 5,
                60, 40,
                List.of(), List.of(),
                "summary", null, 0, null
        );

        assertEquals("V24", detail.engineVersion());
        assertEquals(0, detail.schemaVersion());
        assertNotNull(detail.createdAt());
    }

    @Test
    void timelineIsDefensivelyCopied() {
        var original = new java.util.ArrayList<V24MatchEventDto>();
        var dto1 = new V24MatchEventDto(10, "GOAL", "home", "p1", "P1", null, null, 0.3, "", null);
        original.add(dto1);

        var detail = new V24DetailedMatchData(
                "m", "c", null, null, "h", "a", "H", "A",
                0, 0, 0, 0, 0, 0, 0, 0,
                original, List.of(), "", "V24", 1, Instant.now()
        );

        // Original list modifications do not affect stored copy
        original.add(new V24MatchEventDto(20, "SHOT", "home", "p2", "P2", null, null, 0.1, "", null));
        assertEquals(1, detail.timeline().size()); // only dto1, not dto2
        // Stored list is unmodifiable
        assertThrows(UnsupportedOperationException.class, () ->
                detail.timeline().add(new V24MatchEventDto(30, "GOAL", "home", "p3", "P3", null, null, 0.2, "", null)));
    }

    @Test
    void playerRatingsAreDefensivelyCopied() {
        var original = new java.util.ArrayList<V24PlayerMatchRatingDto>();
        var dto1 = new V24PlayerMatchRatingDto("p1", "P1", "t", "MID", 6.5, 0, 0, 0, 0, 0, 0, 0, 0, false, false);
        original.add(dto1);

        var detail = new V24DetailedMatchData(
                "m", "c", null, null, "h", "a", "H", "A",
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), original, "", "V24", 1, Instant.now()
        );

        // Original list modifications do not affect stored copy
        original.add(new V24PlayerMatchRatingDto("p2", "P2", "t", "DEF", 6.0, 0, 0, 0, 0, 0, 0, 0, 0, false, false));
        assertEquals(1, detail.playerRatings().size()); // only dto1, not dto2
        // Stored list is unmodifiable
        assertThrows(UnsupportedOperationException.class, () ->
                detail.playerRatings().add(new V24PlayerMatchRatingDto("p3", "P3", "t", "ATT", 6.2, 0, 0, 0, 0, 0, 0, 0, 0, false, false)));
    }

    @Test
    void fromResultRejectsBlankMatchId() {
        var result = V24DetailedMatchResult.builder()
                .matchId("  ")
                .homeTeamId("h").awayTeamId("a")
                .homeGoals(1).awayGoals(0)
                .homeXg(1.0).awayXg(0.5)
                .homeShots(5).awayShots(3)
                .homePossession(50).awayPossession(50)
                .build();
        assertThrows(IllegalArgumentException.class, () ->
                V24DetailedMatchData.fromResult("career", 1, 1, "H", "A", result, List.of()));
    }

    @Test
    void fromResultRejectsBlankCareerId() {
        var result = V24DetailedMatchResult.builder()
                .matchId("match").homeTeamId("h").awayTeamId("a")
                .homeGoals(1).awayGoals(0)
                .homeXg(1.0).awayXg(0.5)
                .homeShots(5).awayShots(3)
                .homePossession(50).awayPossession(50)
                .build();
        assertThrows(IllegalArgumentException.class, () ->
                V24DetailedMatchData.fromResult("  ", 1, 1, "H", "A", result, List.of()));
    }

    @Test
    void fromResultRejectsNullMatchId() {
        var result = V24DetailedMatchResult.builder()
                .matchId(null)
                .homeTeamId("h").awayTeamId("a")
                .homeGoals(1).awayGoals(0)
                .homeXg(1.0).awayXg(0.5)
                .homeShots(5).awayShots(3)
                .homePossession(50).awayPossession(50)
                .build();
        assertThrows(NullPointerException.class, () ->
                V24DetailedMatchData.fromResult("career", 1, 1, "H", "A", result, List.of()));
    }

    @Test
    void fromResultMapsAggregateFields() {
        var result = V24DetailedMatchResult.builder()
                .matchId("match-X")
                .homeTeamId("home-id")
                .awayTeamId("away-id")
                .homeGoals(3)
                .awayGoals(1)
                .homeXg(2.1)
                .awayXg(0.8)
                .homeShots(15)
                .awayShots(6)
                .homePossession(58)
                .awayPossession(42)
                .summary("Home dominant")
                .build();

        var detail = V24DetailedMatchData.fromResult(
                "career-1", 2, 10, "Real Madrid", "Barcelona",
                result, List.of()
        );

        assertEquals("match-X", detail.matchId());
        assertEquals("career-1", detail.careerId());
        assertEquals(2, detail.seasonNumber());
        assertEquals(10, detail.round());
        assertEquals("Real Madrid", detail.homeTeamName());
        assertEquals("Barcelona", detail.awayTeamName());
        assertEquals(3, detail.homeGoals());
        assertEquals(1, detail.awayGoals());
        assertEquals(2.1, detail.homeXg(), 0.001);
        assertEquals(0.8, detail.awayXg(), 0.001);
        assertEquals(15, detail.homeShots());
        assertEquals(6, detail.awayShots());
        assertEquals(58, detail.homePossession());
        assertEquals(42, detail.awayPossession());
        assertEquals("Home dominant", detail.summary());
        assertEquals("V24", detail.engineVersion());
        assertEquals(1, detail.schemaVersion());
    }

    @Test
    void fromResultMapsTimelineEventsWithNullShotCoordinate() {
        var timeline = new V24MatchTimeline();
        timeline.addEvent(new V24MatchEvent(12, V24MatchEventType.GOAL, "home", "p1", "Player One", "p2", "Assist", 0.35, "goal"));
        timeline.addEvent(new V24MatchEvent(33, V24MatchEventType.YELLOW_CARD, "home", "p3", "Player Three", null, null, 0, "foul"));

        var result = V24DetailedMatchResult.builder()
                .matchId("match-Y")
                .homeTeamId("home")
                .awayTeamId("away")
                .timeline(timeline)
                .build();

        var detail = V24DetailedMatchData.fromResult("career-1", null, null, "TeamA", "TeamB", result, List.of());

        assertEquals(2, detail.timeline().size());

        V24MatchEventDto goal = detail.timeline().get(0);
        assertEquals(12, goal.minute());
        assertEquals("GOAL", goal.type());
        assertEquals("p1", goal.playerId());
        assertEquals("p2", goal.relatedPlayerId());
        assertEquals(0.35, goal.xg(), 0.001);
        assertNull(goal.shotCoordinate()); // V24D3C not implemented

        V24MatchEventDto yc = detail.timeline().get(1);
        assertEquals(33, yc.minute());
        assertEquals("YELLOW_CARD", yc.type());
        assertEquals("p3", yc.playerId());
        assertNull(yc.shotCoordinate());
    }

    @Test
    void fromResultHandlesNullPlayerRatings() {
        var result = V24DetailedMatchResult.builder()
                .matchId("match-Z")
                .homeTeamId("h")
                .awayTeamId("a")
                .build();

        var detail = V24DetailedMatchData.fromResult(
                "career-2", 1, 3, "TeamH", "TeamA", result, null
        );

        assertNotNull(detail.playerRatings());
        assertEquals(0, detail.playerRatings().size());
    }
}