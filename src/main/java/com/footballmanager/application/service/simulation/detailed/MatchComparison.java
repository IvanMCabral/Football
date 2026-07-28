package com.footballmanager.application.service.simulation.detailed;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * {@code GET /api/v1/careers/{careerId}/matches/{matchId}/compare}
 * endpoint.
 *
 * <p>Contains the {@link #baseline} (what would have happened with no
 * manager interventions), the {@link #live} (what actually happened with
 * the manager's subs/formation/style changes), and the {@link #diff}
 * (signed deltas + bucket-based timeline diff).
 *
 * the front can render either view with the same components.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        setterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        creatorVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
public final class MatchComparison {

    private final DetailedMatchData baseline;
    private final DetailedMatchData live;
    private final MatchComparisonDiff diff;

    @JsonCreator
    public MatchComparison(
            @JsonProperty("baseline") DetailedMatchData baseline,
            @JsonProperty("live") DetailedMatchData live,
            @JsonProperty("diff") MatchComparisonDiff diff) {
        this.baseline = Objects.requireNonNull(baseline, "baseline must not be null");
        this.live = Objects.requireNonNull(live, "live must not be null");
        this.diff = Objects.requireNonNull(diff, "diff must not be null");
        if (!baseline.matchId().equals(live.matchId())) {
            throw new IllegalArgumentException(
                    "baseline.matchId '" + baseline.matchId() + "' does not match live.matchId '"
                            + live.matchId() + "'");
        }
    }

    @JsonProperty("baseline") public DetailedMatchData baseline() { return baseline; }
    @JsonProperty("live") public DetailedMatchData live() { return live; }
    @JsonProperty("diff") public MatchComparisonDiff diff() { return diff; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MatchComparison that)) return false;
        return Objects.equals(baseline, that.baseline)
                && Objects.equals(live, that.live)
                && Objects.equals(diff, that.diff);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseline, live, diff);
    }

    @Override
    public String toString() {
        return "MatchComparison{matchId=%s, score(base=%d-%d, live=%d-%d), delta=%+d-%+d}"
                .formatted(live.matchId(),
                        baseline.homeGoals(), baseline.awayGoals(),
                        live.homeGoals(), live.awayGoals(),
                        diff.scoreDeltaHome(), diff.scoreDeltaAway());
    }
}
