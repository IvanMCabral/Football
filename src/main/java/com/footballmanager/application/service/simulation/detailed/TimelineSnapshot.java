package com.footballmanager.application.service.simulation.detailed;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * specific minute. Returned by
 * {@code GET /api/v1/careers/{careerId}/matches/{matchId}/timeline?minute=N}
 * for the test-harness UI timeline scrubber.
 *
 * <p>Aggregations are derived from the timeline events of the stored
 * sub-ms for typical matches (50-200 events).
 *
 * <p>Aggregation rules (see {@link TimelineSnapshotBuilder}):
 * <ul>
 *   <li>{@code homeGoals} / {@code awayGoals} — count of GOAL events per team.</li>
 *   <li>{@code homeShots} / {@code awayShots} — count of SHOT events per team
 *       (GOAL/SHOT_ON_TARGET/MISS/SAVE/BLOCK are NOT counted as separate shots).</li>
 *   <li>{@code homeXg} / {@code awayXg} — sum of xG for SHOT/SHOT_ON_TARGET/GOAL
 *       events per team.</li>
 *   <li>{@code events} — timeline events with {@code event.minute() <= minute}.</li>
 * </ul>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        setterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        creatorVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
public final class TimelineSnapshot {

    private final int minute;
    private final int homeGoals;
    private final int awayGoals;
    private final double homeXg;
    private final double awayXg;
    private final int homeShots;
    private final int awayShots;
    private final List<DetailedMatchEventDto> events;

    @JsonCreator
    public TimelineSnapshot(
            @JsonProperty("minute") int minute,
            @JsonProperty("homeGoals") int homeGoals,
            @JsonProperty("awayGoals") int awayGoals,
            @JsonProperty("homeXg") double homeXg,
            @JsonProperty("awayXg") double awayXg,
            @JsonProperty("homeShots") int homeShots,
            @JsonProperty("awayShots") int awayShots,
            @JsonProperty("events") List<DetailedMatchEventDto> events) {
        if (minute < 0 || minute > 130) {
            throw new IllegalArgumentException("minute must be between 0 and 130, got " + minute);
        }
        if (homeGoals < 0 || awayGoals < 0) {
            throw new IllegalArgumentException("goals must be non-negative");
        }
        if (homeShots < 0 || awayShots < 0) {
            throw new IllegalArgumentException("shots must be non-negative");
        }
        if (!Double.isFinite(homeXg) || homeXg < 0) {
            throw new IllegalArgumentException("homeXg must be >= 0 and finite");
        }
        if (!Double.isFinite(awayXg) || awayXg < 0) {
            throw new IllegalArgumentException("awayXg must be >= 0 and finite");
        }
        this.minute = minute;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.homeXg = homeXg;
        this.awayXg = awayXg;
        this.homeShots = homeShots;
        this.awayShots = awayShots;
        this.events = (events != null)
                ? Collections.unmodifiableList(new ArrayList<>(events))
                : Collections.emptyList();
    }

    @JsonProperty("minute") public int minute() { return minute; }
    @JsonProperty("homeGoals") public int homeGoals() { return homeGoals; }
    @JsonProperty("awayGoals") public int awayGoals() { return awayGoals; }
    @JsonProperty("homeXg") public double homeXg() { return homeXg; }
    @JsonProperty("awayXg") public double awayXg() { return awayXg; }
    @JsonProperty("homeShots") public int homeShots() { return homeShots; }
    @JsonProperty("awayShots") public int awayShots() { return awayShots; }
    @JsonProperty("events") public List<DetailedMatchEventDto> events() { return events; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimelineSnapshot that)) return false;
        return minute == that.minute
                && homeGoals == that.homeGoals
                && awayGoals == that.awayGoals
                && Double.compare(that.homeXg, homeXg) == 0
                && Double.compare(that.awayXg, awayXg) == 0
                && homeShots == that.homeShots
                && awayShots == that.awayShots
                && Objects.equals(events, that.events);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minute, homeGoals, awayGoals, homeXg, awayXg,
                homeShots, awayShots, events);
    }

    @Override
    public String toString() {
        return "TimelineSnapshot{minute=%d, score=%d-%d, xG=%.2f-%.2f, shots=%d-%d, events=%d}"
                .formatted(minute, homeGoals, awayGoals, homeXg, awayXg,
                        homeShots, awayShots, events.size());
    }
}
