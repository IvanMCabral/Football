package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
                "V24", 1, Instant.now(), null, null);

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
                "summary", null, 0, null, null, null);

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
                original, List.of(), "", "V24", 1, Instant.now(), null, null);

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
                List.of(), original, "", "V24", 1, Instant.now(), null, null);

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

    // ============== V24D24-F1.2: formation fields ==============

    @Test
    void fromResultConFormationNull_delegatingOverload_devuelveFormationNull() {
        // V24D24-F1.2: the back-compat overload (no formation params) must
        // still work for the 31 existing call sites, and formation must be null.
        var result = V24DetailedMatchResult.builder()
                .matchId("match-FN")
                .homeTeamId("h").awayTeamId("a")
                .build();

        var detail = V24DetailedMatchData.fromResult(
                "career-FN", 1, 1, "Home", "Away", result, List.of()
        );

        assertNull(detail.homeFormation());
        assertNull(detail.awayFormation());
    }

    @Test
    void fromResultConFormationNew_overload_propagatesFormations() {
        // V24D24-F1.2: the new overload that accepts formation strings must
        // propagate them to the constructed DTO.
        var result = V24DetailedMatchResult.builder()
                .matchId("match-FP")
                .homeTeamId("h").awayTeamId("a")
                .build();

        var detail = V24DetailedMatchData.fromResult(
                "career-FP", 1, 1, "Home", "Away",
                "4-3-3", "4-4-2",
                result, List.of()
        );

        assertEquals("4-3-3", detail.homeFormation());
        assertEquals("4-4-2", detail.awayFormation());
    }

    @Test
    void constructorTreatsBlankFormationAsNull() {
        // V24D24-F1.2: a blank string in the formation field is normalized
        // to null so the UI renders "—" instead of an empty cell.
        var detail = new V24DetailedMatchData(
                "m", "c", 1, 1, "h", "a", "H", "A",
                0, 0, 0.0, 0.0, 0, 0, 50, 50,
                List.of(), List.of(), "", "V24", 1, Instant.now(),
                "  ", null);

        assertNull(detail.homeFormation());
        assertNull(detail.awayFormation());
    }

    @Test
    void equalsAndHashCode_includeFormation() {
        var result = V24DetailedMatchResult.builder()
                .matchId("match-EQ")
                .homeTeamId("h").awayTeamId("a")
                .build();

        var baseWithNull = V24DetailedMatchData.fromResult(
                "career-EQ", 1, 1, "H", "A", null, null, result, List.of());
        var baseWith433 = V24DetailedMatchData.fromResult(
                "career-EQ", 1, 1, "H", "A", "4-3-3", "4-4-2", result, List.of());
        var sameAs433 = V24DetailedMatchData.fromResult(
                "career-EQ", 1, 1, "H", "A", "4-3-3", "4-4-2", result, List.of());
        var differentFormation = V24DetailedMatchData.fromResult(
                "career-EQ", 1, 1, "H", "A", "3-5-2", "4-4-2", result, List.of());

        // Same formation → equal
        assertEquals(baseWith433, sameAs433);
        assertEquals(baseWith433.hashCode(), sameAs433.hashCode());

        // Null vs populated → NOT equal
        assertNotEquals(baseWithNull, baseWith433);

        // Different formation → NOT equal
        assertNotEquals(baseWith433, differentFormation);
    }

    @Test
    void jacksonDeserialization_handlesNullFormation() throws Exception {
        // V24D24-F1.2: JSON from partidos viejos (Redis pre-F1.2) has no
        // homeFormation/awayFormation fields. Jackson must deserialize with
        // null and the UI must show "—".
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String jsonOld = """
                {
                  "matchId": "m-1",
                  "careerId": "c-1",
                  "seasonNumber": 1,
                  "round": 5,
                  "homeTeamId": "h",
                  "awayTeamId": "a",
                  "homeTeamName": "H",
                  "awayTeamName": "A",
                  "homeGoals": 1,
                  "awayGoals": 0,
                  "homeXg": 0.5,
                  "awayXg": 0.3,
                  "homeShots": 5,
                  "awayShots": 3,
                  "homePossession": 55,
                  "awayPossession": 45,
                  "timeline": [],
                  "playerRatings": [],
                  "summary": "old",
                  "engineVersion": "V24",
                  "schemaVersion": 1,
                  "createdAt": "2026-06-20T10:00:00Z"
                }
                """;

        V24DetailedMatchData detail = mapper.readValue(jsonOld, V24DetailedMatchData.class);
        assertNull(detail.homeFormation(), "old JSON without homeFormation must deserialize to null");
        assertNull(detail.awayFormation(), "old JSON without awayFormation must deserialize to null");
    }

    @Test
    void jacksonRoundtrip_preservesFormation() throws Exception {
        // V24D24-F1.2: new detail with formations must roundtrip through Jackson
        // without losing the values.
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var detail = new V24DetailedMatchData(
                "m-rt", "c-rt", 1, 1, "h", "a", "H", "A",
                1, 0, 0.5, 0.3, 5, 3, 55, 45,
                List.of(), List.of(), "rt", "V24", 1, Instant.parse("2026-06-20T10:00:00Z"),
                "4-3-3", "4-4-2"
        );

        String json = mapper.writeValueAsString(detail);
        V24DetailedMatchData deserialized = mapper.readValue(json, V24DetailedMatchData.class);

        assertEquals("4-3-3", deserialized.homeFormation());
        assertEquals("4-4-2", deserialized.awayFormation());
        assertEquals(detail, deserialized);
    }
}