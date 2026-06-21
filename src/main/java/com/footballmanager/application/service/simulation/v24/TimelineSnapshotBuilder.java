package com.footballmanager.application.service.simulation.v24;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * V24D24: Builds a {@link V24TimelineSnapshot} from a stored
 * {@link V24DetailedMatchData} filtered up to and including a specific minute.
 *
 * <p>Stateless, side-effect free, sub-millisecond for typical matches
 * (50-200 events). No re-simulation, no cache lookup — pure derivation
 * from the stored timeline.
 *
 * <p>Aggregation rules (per team, determined by {@code event.teamId()}
 * matching {@code detail.homeTeamId()}):
 * <ul>
 *   <li>{@code homeGoals} / {@code awayGoals} — count of {@code GOAL} events.</li>
 *   <li>{@code homeShots} / {@code awayShots} — count of {@code SHOT} events
 *       (only the base SHOT type; GOAL/SHOT_ON_TARGET/MISS/SAVE/BLOCK are
 *       NOT counted as separate shots since they are all outcomes of a single
 *       shot attempt, but xG is summed for the whole attempt cluster).</li>
 *   <li>{@code homeXg} / {@code awayXg} — sum of xG for {@code SHOT},
 *       {@code SHOT_ON_TARGET} and {@code GOAL} events (the shot-attempt
 *       cluster — each event in the cluster carries the same xG, so we sum
 *       it to keep the metric consistent with how {@code V24DetailedMatchData}
 *       computes total xG).</li>
 *   <li>{@code events} — unmodifiable list of events with
 *       {@code event.minute() <= minute}, preserving stored order.</li>
 * </ul>
 */
public final class TimelineSnapshotBuilder {

    private TimelineSnapshotBuilder() {
        // utility class
    }

    /**
     * Build a snapshot for the given detail and minute.
     *
     * @param detail the stored match data (must not be null)
     * @param minute the inclusive upper bound for event filtering (0-130)
     * @return a snapshot with filtered events and accumulated stats
     * @throws NullPointerException     if detail is null
     * @throws IllegalArgumentException if minute is outside [0, 130]
     */
    public static V24TimelineSnapshot build(V24DetailedMatchData detail, int minute) {
        Objects.requireNonNull(detail, "detail must not be null");
        if (minute < 0 || minute > 130) {
            throw new IllegalArgumentException("minute must be between 0 and 130, got " + minute);
        }

        String homeTeamId = detail.homeTeamId();
        List<V24MatchEventDto> events = detail.timeline();
        List<V24MatchEventDto> filtered = new ArrayList<>(events.size());
        int homeGoals = 0;
        int awayGoals = 0;
        int homeShots = 0;
        int awayShots = 0;
        double homeXg = 0.0;
        double awayXg = 0.0;

        for (V24MatchEventDto e : events) {
            if (e.minute() > minute) {
                continue;
            }
            filtered.add(e);

            // teamId may be null for some events (e.g. SUBSTITUTION details);
            // skip teamId-less events for goal/shot/xG aggregation.
            if (e.teamId() == null || homeTeamId == null) {
                continue;
            }
            boolean isHome = e.teamId().equals(homeTeamId);
            String type = e.type();

            if ("GOAL".equals(type)) {
                if (isHome) homeGoals++;
                else awayGoals++;
                // GOAL also contributes to xG (the goal itself carries xG)
                if (isHome) homeXg += e.xg();
                else awayXg += e.xg();
            } else if ("SHOT".equals(type)) {
                if (isHome) homeShots++;
                else awayShots++;
                if (isHome) homeXg += e.xg();
                else awayXg += e.xg();
            } else if ("SHOT_ON_TARGET".equals(type)) {
                // SHOT_ON_TARGET is part of the same shot attempt as SHOT,
                // so it does NOT add to the shot count. But it carries xG
                // that we want to keep in the cumulative xG metric.
                if (isHome) homeXg += e.xg();
                else awayXg += e.xg();
            }
            // Other event types (FOUL, YELLOW_CARD, SUBSTITUTION, etc.) do
            // not contribute to goals, shots, or xG.
        }

        return new V24TimelineSnapshot(
                minute,
                homeGoals,
                awayGoals,
                homeXg,
                awayXg,
                homeShots,
                awayShots,
                filtered);
    }
}
