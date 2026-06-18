package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE): Unit tests for
 * {@link MatchComparisonDiff} and {@link EventBucketDiff}.
 */
class MatchComparisonDiffTest {

    @Test
    void calculate_emits90EntriesFor5TypesAcross18Buckets() {
        V24DetailedMatchData baseline = sampleData(1, 0, 1.0, 0.5, 8, 5, 50);
        V24DetailedMatchData live = sampleData(2, 1, 1.8, 0.9, 12, 8, 55);

        MatchComparisonDiff diff = MatchComparisonDiff.calculate(baseline, live);

        // 90 entries: 5 types × 18 buckets
        assertEquals(90, diff.timelineDiff().size());
        // Scalar deltas: live - baseline
        assertEquals(1, diff.scoreDeltaHome());   // 2 - 1
        assertEquals(1, diff.scoreDeltaAway());   // 1 - 0
        assertEquals(0.8, diff.xgDeltaHome(), 0.0001);
        assertEquals(0.4, diff.xgDeltaAway(), 0.0001);
        assertEquals(4, diff.shotsDeltaHome());   // 12 - 8
        assertEquals(3, diff.shotsDeltaAway());   // 8 - 5
        assertEquals(5, diff.possessionDeltaHome()); // 55 - 50
    }

    @Test
    void calculate_emptyTimelines_yieldsAllZeros() {
        V24DetailedMatchData baseline = sampleData(0, 0, 0.0, 0.0, 0, 0, 50);
        V24DetailedMatchData live = sampleData(0, 0, 0.0, 0.0, 0, 0, 50);

        MatchComparisonDiff diff = MatchComparisonDiff.calculate(baseline, live);

        assertEquals(0, diff.scoreDeltaHome());
        assertEquals(0, diff.scoreDeltaAway());
        assertEquals(0.0, diff.xgDeltaHome(), 0.0001);
        assertEquals(0.0, diff.xgDeltaAway(), 0.0001);
        assertEquals(0, diff.shotsDeltaHome());
        assertEquals(0, diff.shotsDeltaAway());
        assertEquals(0, diff.possessionDeltaHome());
        diff.timelineDiff().forEach(e -> {
            assertEquals(0, e.baselineCount(), "bucket=" + e.bucket() + " type=" + e.type());
            assertEquals(0, e.liveCount(), "bucket=" + e.bucket() + " type=" + e.type());
            assertEquals(0, e.delta(), "bucket=" + e.bucket() + " type=" + e.type());
        });
    }

    @Test
    void calculate_eventsInCorrectBucket() {
        // Event in minute 22 falls in bucket 4 (minutes 20-25)
        V24DetailedMatchData baseline = sampleDataWithEvents(
                List.of(eventAt(22, "GOAL"))
        );
        V24DetailedMatchData live = sampleDataWithEvents(
                List.of(eventAt(22, "GOAL"), eventAt(23, "GOAL"))
        );

        MatchComparisonDiff diff = MatchComparisonDiff.calculate(baseline, live);
        // Find the bucket-4 GOAL entry
        EventBucketDiff bucket4Goal = diff.timelineDiff().stream()
                .filter(e -> e.bucket() == 4 && "GOAL".equals(e.type()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, bucket4Goal.baselineCount());
        assertEquals(2, bucket4Goal.liveCount());
        assertEquals(1, bucket4Goal.delta());
    }

    @Test
    void calculate_substitutionInCorrectBucket() {
        V24DetailedMatchData baseline = sampleDataWithEvents(
                List.of(eventAt(60, "SUBSTITUTION"))
        );
        V24DetailedMatchData live = sampleDataWithEvents(
                List.of(eventAt(60, "SUBSTITUTION"), eventAt(60, "SUBSTITUTION"))
        );

        MatchComparisonDiff diff = MatchComparisonDiff.calculate(baseline, live);
        // Bucket 12 = minutes 60-65
        EventBucketDiff sub = diff.timelineDiff().stream()
                .filter(e -> e.bucket() == 12 && "SUBSTITUTION".equals(e.type()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, sub.baselineCount());
        assertEquals(2, sub.liveCount());
        assertEquals(1, sub.delta());
    }

    @Test
    void eventBucketDiff_factoryComputesDelta() {
        EventBucketDiff e = EventBucketDiff.of(5, "SHOT", 3, 7);
        assertEquals(5, e.bucket());
        assertEquals("SHOT", e.type());
        assertEquals(3, e.baselineCount());
        assertEquals(7, e.liveCount());
        assertEquals(4, e.delta());
    }

    @Test
    void eventBucketDiff_negativeDelta() {
        EventBucketDiff e = EventBucketDiff.of(5, "GOAL", 2, 0);
        assertEquals(-2, e.delta());
    }

    @Test
    void eventBucketDiff_invalidBucket_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new EventBucketDiff(18, "GOAL", 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new EventBucketDiff(-1, "GOAL", 0, 0, 0));
    }

    @Test
    void eventBucketDiff_blankType_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new EventBucketDiff(0, "", 0, 0, 0));
    }

    // ---- helpers ----

    private V24MatchEventDto eventAt(int minute, String type) {
        return new V24MatchEventDto(
                minute, type, "home-id", "player-1", "Player One",
                null, null, 0.0, "Test event at minute " + minute, null);
    }

    private V24DetailedMatchData sampleData(int homeGoals, int awayGoals,
                                            double homeXg, double awayXg,
                                            int homeShots, int awayShots, int homePossession) {
        return new V24DetailedMatchData(
                "match-1", "career-1", 1, 5,
                "home-id", "away-id",
                "Home", "Away",
                homeGoals, awayGoals, homeXg, awayXg,
                homeShots, awayShots, homePossession, 100 - homePossession,
                List.of(), List.of(),
                "Test", "V24", 1, Instant.now());
    }

    private V24DetailedMatchData sampleDataWithEvents(List<V24MatchEventDto> events) {
        return new V24DetailedMatchData(
                "match-1", "career-1", 1, 5,
                "home-id", "away-id",
                "Home", "Away",
                0, 0, 0.0, 0.0,
                0, 0, 50, 50,
                events, List.of(),
                "Test", "V24", 1, Instant.now());
    }
}
