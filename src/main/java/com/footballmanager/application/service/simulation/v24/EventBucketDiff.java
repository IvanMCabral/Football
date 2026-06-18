package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE): Per-bucket event-count diff
 * between the baseline and the live match.
 *
 * <p>A "bucket" is a 5-minute window. There are 18 buckets covering
 * minutes [0,5), [5,10), ..., [85,90). The {@code type} field is the
 * V24 event type (GOAL, SHOT, YELLOW_CARD, RED_CARD, SUBSTITUTION).
 *
 * <p>{@code delta = liveCount - baselineCount}. Positive delta means the
 * live match had more of that event in that bucket than the baseline
 * (i.e. the manager's substitutions or tactics contributed additional
 * events). Negative means the live had fewer.
 *
 * <p>This is a "bucket-based" diff — it intentionally does NOT match
 * events by playerId because the engine consumes different draws in
 * each run (even with the same seed + context + subs), so the exact
 * player attribution can shift between baseline and live.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        setterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        creatorVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
public final class EventBucketDiff {

    /** Bucket index: minute / 5, so [0,18). */
    private final int bucket;
    /** V24 event type, e.g. {@code "GOAL"}, {@code "SHOT"}, {@code "SUBSTITUTION"}. */
    private final String type;
    private final int baselineCount;
    private final int liveCount;
    private final int delta;

    @JsonCreator
    public EventBucketDiff(
            @JsonProperty("bucket") int bucket,
            @JsonProperty("type") String type,
            @JsonProperty("baselineCount") int baselineCount,
            @JsonProperty("liveCount") int liveCount,
            @JsonProperty("delta") int delta) {
        if (bucket < 0 || bucket > 17) {
            throw new IllegalArgumentException("bucket must be in [0, 17], got " + bucket);
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (baselineCount < 0) {
            throw new IllegalArgumentException("baselineCount must be >= 0, got " + baselineCount);
        }
        if (liveCount < 0) {
            throw new IllegalArgumentException("liveCount must be >= 0, got " + liveCount);
        }
        this.bucket = bucket;
        this.type = type;
        this.baselineCount = baselineCount;
        this.liveCount = liveCount;
        this.delta = delta;
    }

    /** Convenience factory that computes {@code delta = live - baseline}. */
    public static EventBucketDiff of(int bucket, String type, int baselineCount, int liveCount) {
        return new EventBucketDiff(bucket, type, baselineCount, liveCount, liveCount - baselineCount);
    }

    @JsonProperty("bucket") public int bucket() { return bucket; }
    @JsonProperty("type") public String type() { return type; }
    @JsonProperty("baselineCount") public int baselineCount() { return baselineCount; }
    @JsonProperty("liveCount") public int liveCount() { return liveCount; }
    @JsonProperty("delta") public int delta() { return delta; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventBucketDiff that)) return false;
        return bucket == that.bucket
                && baselineCount == that.baselineCount
                && liveCount == that.liveCount
                && delta == that.delta
                && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucket, type, baselineCount, liveCount, delta);
    }

    @Override
    public String toString() {
        return "EventBucketDiff{bucket=%d, type='%s', base=%d, live=%d, delta=%+d}"
                .formatted(bucket, type, baselineCount, liveCount, delta);
    }
}
