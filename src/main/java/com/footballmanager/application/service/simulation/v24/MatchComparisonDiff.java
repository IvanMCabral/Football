package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE): Per-metric diff between the
 * baseline and the live match.
 *
 * <p>Scalar fields are computed as {@code live - baseline}. The
 * {@code timelineDiff} is a list of 90 entries (18 buckets × 5 event
 * types) — see {@link EventBucketDiff}.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        setterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        creatorVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
public final class MatchComparisonDiff {

    private final int scoreDeltaHome;
    private final int scoreDeltaAway;
    private final double xgDeltaHome;
    private final double xgDeltaAway;
    private final int shotsDeltaHome;
    private final int shotsDeltaAway;
    private final int possessionDeltaHome;
    private final List<EventBucketDiff> timelineDiff;

    @JsonCreator
    public MatchComparisonDiff(
            @JsonProperty("scoreDeltaHome") int scoreDeltaHome,
            @JsonProperty("scoreDeltaAway") int scoreDeltaAway,
            @JsonProperty("xgDeltaHome") double xgDeltaHome,
            @JsonProperty("xgDeltaAway") double xgDeltaAway,
            @JsonProperty("shotsDeltaHome") int shotsDeltaHome,
            @JsonProperty("shotsDeltaAway") int shotsDeltaAway,
            @JsonProperty("possessionDeltaHome") int possessionDeltaHome,
            @JsonProperty("timelineDiff") List<EventBucketDiff> timelineDiff) {
        this.scoreDeltaHome = scoreDeltaHome;
        this.scoreDeltaAway = scoreDeltaAway;
        this.xgDeltaHome = xgDeltaHome;
        this.xgDeltaAway = xgDeltaAway;
        this.shotsDeltaHome = shotsDeltaHome;
        this.shotsDeltaAway = shotsDeltaAway;
        this.possessionDeltaHome = possessionDeltaHome;
        this.timelineDiff = (timelineDiff != null)
                ? Collections.unmodifiableList(new ArrayList<>(timelineDiff))
                : Collections.emptyList();
    }

    /**
     * Compute the diff between {@code live} and {@code baseline}. The
     * deltas are signed {@code live - baseline}. The timeline diff uses
     * the 5 event types that are most relevant to the manager: GOAL,
     * SHOT, YELLOW_CARD, RED_CARD, SUBSTITUTION.
     *
     * <p>Both arguments must be non-null. The 5 event types are emitted
     * in stable order so the list is deterministic for snapshot diffing.
     */
    public static MatchComparisonDiff calculate(V24DetailedMatchData baseline,
                                                V24DetailedMatchData live) {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(live, "live must not be null");

        int scoreDeltaHome = live.homeGoals() - baseline.homeGoals();
        int scoreDeltaAway = live.awayGoals() - baseline.awayGoals();
        double xgDeltaHome = live.homeXg() - baseline.homeXg();
        double xgDeltaAway = live.awayXg() - baseline.awayXg();
        int shotsDeltaHome = live.homeShots() - baseline.homeShots();
        int shotsDeltaAway = live.awayShots() - baseline.awayShots();
        int possessionDeltaHome = live.homePossession() - baseline.homePossession();

        // 5 event types × 18 buckets = 90 entries.
        // Use a stable list so the snapshot diff is deterministic.
        List<String> types = List.of("GOAL", "SHOT", "YELLOW_CARD", "RED_CARD", "SUBSTITUTION");
        List<EventBucketDiff> timelineDiff = new ArrayList<>(5 * 18);
        for (int bucket = 0; bucket < 18; bucket++) {
            int minuteStart = bucket * 5;
            int minuteEnd = minuteStart + 5;
            for (String type : types) {
                int baseCount = countEvents(baseline.timeline(), minuteStart, minuteEnd, type);
                int liveCount = countEvents(live.timeline(), minuteStart, minuteEnd, type);
                timelineDiff.add(EventBucketDiff.of(bucket, type, baseCount, liveCount));
            }
        }

        return new MatchComparisonDiff(
                scoreDeltaHome, scoreDeltaAway,
                xgDeltaHome, xgDeltaAway,
                shotsDeltaHome, shotsDeltaAway,
                possessionDeltaHome,
                timelineDiff);
    }

    /**
     * Count events of {@code type} in {@code timeline} that fall in
     * {@code [minuteStart, minuteEnd)}. Used by {@link #calculate}.
     */
    private static int countEvents(List<V24MatchEventDto> timeline,
                                   int minuteStart, int minuteEnd,
                                   String type) {
        int count = 0;
        for (V24MatchEventDto e : timeline) {
            if (e == null) continue;
            if (!type.equals(e.type())) continue;
            // V24DetailedMatchData events use minute in [1, 130] (incl. extra time).
            // We bucket by minute, with extra time falling in bucket 17
            // (minute 85-90) for simplicity — the V24 engine does not
            // emit many extra-time events in practice (matches end at 90).
            int m = e.minute();
            if (m >= minuteStart && m < minuteEnd) {
                count++;
            } else if (m >= 90 && minuteEnd > 90) {
                // Defensive: events past 90 fall in the last bucket.
                count++;
            }
        }
        return count;
    }

    @JsonProperty("scoreDeltaHome") public int scoreDeltaHome() { return scoreDeltaHome; }
    @JsonProperty("scoreDeltaAway") public int scoreDeltaAway() { return scoreDeltaAway; }
    @JsonProperty("xgDeltaHome") public double xgDeltaHome() { return xgDeltaHome; }
    @JsonProperty("xgDeltaAway") public double xgDeltaAway() { return xgDeltaAway; }
    @JsonProperty("shotsDeltaHome") public int shotsDeltaHome() { return shotsDeltaHome; }
    @JsonProperty("shotsDeltaAway") public int shotsDeltaAway() { return shotsDeltaAway; }
    @JsonProperty("possessionDeltaHome") public int possessionDeltaHome() { return possessionDeltaHome; }
    @JsonProperty("timelineDiff") public List<EventBucketDiff> timelineDiff() { return timelineDiff; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MatchComparisonDiff that)) return false;
        return scoreDeltaHome == that.scoreDeltaHome
                && scoreDeltaAway == that.scoreDeltaAway
                && Double.compare(that.xgDeltaHome, xgDeltaHome) == 0
                && Double.compare(that.xgDeltaAway, xgDeltaAway) == 0
                && shotsDeltaHome == that.shotsDeltaHome
                && shotsDeltaAway == that.shotsDeltaAway
                && possessionDeltaHome == that.possessionDeltaHome
                && Objects.equals(timelineDiff, that.timelineDiff);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scoreDeltaHome, scoreDeltaAway,
                xgDeltaHome, xgDeltaAway,
                shotsDeltaHome, shotsDeltaAway,
                possessionDeltaHome, timelineDiff);
    }

    @Override
    public String toString() {
        return "MatchComparisonDiff{score=%+d-%+d, xG=%+.2f-%+.2f, shots=%+d-%+d, possession=%+d, timelineEntries=%d}"
                .formatted(scoreDeltaHome, scoreDeltaAway,
                        xgDeltaHome, xgDeltaAway,
                        shotsDeltaHome, shotsDeltaAway,
                        possessionDeltaHome, timelineDiff.size());
    }
}
