package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D24: Unit tests for {@link TimelineSnapshotBuilder}.
 *
 * <p>Covers the filter (event.minute &lt;= minute), the team attribution
 * (event.teamId equals detail.homeTeamId), and the four counter/aggregator
 * accumulators (homeGoals/awayGoals, homeShots/awayShots, homeXg/awayXg).
 */
class TimelineSnapshotBuilderTest {

    private static final String HOME = "home-team";
    private static final String AWAY = "away-team";

    private static V24MatchEventDto ev(int minute, String type, String teamId, double xg) {
        return new V24MatchEventDto(minute, type, teamId, "p1", "Player", null, null, xg, "", null);
    }

    private static V24DetailedMatchData detail(List<V24MatchEventDto> events) {
        return new V24DetailedMatchData(
                "match-1", "career-1", 1, 5,
                HOME, AWAY,
                "Home FC", "Away FC",
                0, 0, 0.0, 0.0,
                0, 0, 50, 50,
                events, List.of(),
                "snapshot", "V24", 1, Instant.now()
        );
    }

    @Test
    void emptyTimelineAtMinuteZero() {
        V24DetailedMatchData d = detail(List.of());

        V24TimelineSnapshot snap = TimelineSnapshotBuilder.build(d, 0);

        assertEquals(0, snap.minute());
        assertEquals(0, snap.homeGoals());
        assertEquals(0, snap.awayGoals());
        assertEquals(0.0, snap.homeXg(), 0.001);
        assertEquals(0.0, snap.awayXg(), 0.001);
        assertEquals(0, snap.homeShots());
        assertEquals(0, snap.awayShots());
        assertEquals(0, snap.events().size());
    }

    @Test
    void minuteZero_excludesAllEvents() {
        V24DetailedMatchData d = detail(List.of(
                ev(5, "GOAL", HOME, 0.20),
                ev(40, "SHOT", AWAY, 0.10),
                ev(90, "GOAL", HOME, 0.05)
        ));

        V24TimelineSnapshot snap = TimelineSnapshotBuilder.build(d, 0);

        // All events have minute >= 1 (V24MatchEventDto rejects minute=0),
        // so minute=0 must return an empty snapshot.
        assertEquals(0, snap.minute());
        assertEquals(0, snap.events().size());
        assertEquals(0, snap.homeGoals());
        assertEquals(0, snap.awayGoals());
    }

    @Test
    void minuteFortyFive_filtersEventsUpToAndIncluding() {
        V24DetailedMatchData d = detail(List.of(
                ev(5,  "GOAL",           HOME, 0.30),
                ev(15, "SHOT",           AWAY, 0.10),
                ev(30, "SHOT_ON_TARGET", HOME, 0.20),
                ev(45, "GOAL",           AWAY, 0.15),  // boundary
                ev(60, "SHOT",           HOME, 0.08),  // excluded
                ev(90, "GOAL",           AWAY, 0.05)   // excluded
        ));

        V24TimelineSnapshot snap = TimelineSnapshotBuilder.build(d, 45);

        assertEquals(45, snap.minute());
        assertEquals(4, snap.events().size()); // minute 5, 15, 30, 45
        // goals: 1 home (min 5) + 1 away (min 45) = 1-1
        assertEquals(1, snap.homeGoals());
        assertEquals(1, snap.awayGoals());
        // shots: 1 away SHOT (min 15) — SHOT_ON_TARGET is NOT counted as a shot
        assertEquals(0, snap.homeShots());
        assertEquals(1, snap.awayShots());
        // xG: home GOAL 0.30 + home SHOT_ON_TARGET 0.20 = 0.50 ; away SHOT 0.10 + away GOAL 0.15 = 0.25
        assertEquals(0.50, snap.homeXg(), 0.001);
        assertEquals(0.25, snap.awayXg(), 0.001);
    }

    @Test
    void minuteOneHundredThirty_includesAllEvents() {
        V24DetailedMatchData d = detail(List.of(
                ev(5,  "GOAL", HOME, 0.30),
                ev(90, "GOAL", AWAY, 0.20),
                ev(120, "SHOT", HOME, 0.05)
        ));

        V24TimelineSnapshot snap = TimelineSnapshotBuilder.build(d, 130);

        assertEquals(130, snap.minute());
        assertEquals(3, snap.events().size());
        assertEquals(1, snap.homeGoals());
        assertEquals(1, snap.awayGoals());
        assertEquals(1, snap.homeShots());
        assertEquals(0, snap.awayShots());
        // home: 0.30 (GOAL) + 0.05 (SHOT) = 0.35 ; away: 0.20 (GOAL)
        assertEquals(0.35, snap.homeXg(), 0.001);
        assertEquals(0.20, snap.awayXg(), 0.001);
    }

    @Test
    void goalContributesToBothGoalsAndXg() {
        V24DetailedMatchData d = detail(List.of(
                ev(10, "GOAL", HOME, 0.35),
                ev(10, "SHOT_ON_TARGET", HOME, 0.35) // follow-up: also carries same xg
        ));

        V24TimelineSnapshot snap = TimelineSnapshotBuilder.build(d, 90);

        assertEquals(1, snap.homeGoals(), "GOAL should count once");
        assertEquals(0, snap.homeShots(), "GOAL should NOT count as a SHOT");
        // Both events carry xg 0.35 → cumulative xg = 0.70
        assertEquals(0.70, snap.homeXg(), 0.001);
    }

    @Test
    void shotOnTargetDoesNotIncrementShotCount() {
        V24DetailedMatchData d = detail(List.of(
                ev(20, "SHOT", HOME, 0.15),
                ev(20, "SHOT_ON_TARGET", HOME, 0.15)
        ));

        V24TimelineSnapshot snap = TimelineSnapshotBuilder.build(d, 90);

        // Only the SHOT event counts as a shot attempt; SHOT_ON_TARGET is
        // part of the same attempt cluster.
        assertEquals(1, snap.homeShots());
        // xG sums both: 0.15 + 0.15 = 0.30
        assertEquals(0.30, snap.homeXg(), 0.001);
    }

    @Test
    void nonShotEventsDoNotContribute() {
        V24DetailedMatchData d = detail(List.of(
                ev(5, "FOUL", HOME, 0.0),
                ev(15, "YELLOW_CARD", AWAY, 0.0),
                ev(30, "SUBSTITUTION", HOME, 0.0),
                ev(60, "CORNER", AWAY, 0.0)
        ));

        V24TimelineSnapshot snap = TimelineSnapshotBuilder.build(d, 90);

        assertEquals(0, snap.homeGoals());
        assertEquals(0, snap.awayGoals());
        assertEquals(0, snap.homeShots());
        assertEquals(0, snap.awayShots());
        assertEquals(0.0, snap.homeXg(), 0.001);
        assertEquals(0.0, snap.awayXg(), 0.001);
        assertEquals(4, snap.events().size()); // events still flow through, just don't aggregate
    }

    @Test
    void eventsListIsUnmodifiable() {
        V24DetailedMatchData d = detail(List.of(
                ev(10, "GOAL", HOME, 0.20)
        ));

        V24TimelineSnapshot snap = TimelineSnapshotBuilder.build(d, 90);

        assertThrows(UnsupportedOperationException.class,
                () -> snap.events().add(ev(20, "SHOT", AWAY, 0.10)));
    }

    @Test
    void rejectsNullDetail() {
        assertThrows(NullPointerException.class,
                () -> TimelineSnapshotBuilder.build(null, 45));
    }

    @Test
    void rejectsMinuteOutOfRange() {
        V24DetailedMatchData d = detail(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> TimelineSnapshotBuilder.build(d, -1));
        assertThrows(IllegalArgumentException.class,
                () -> TimelineSnapshotBuilder.build(d, 131));
    }

    @Test
    void mixedScenarioRealisticMatch() {
        // Realistic match: 2 home goals, 1 away goal, 8 home shots, 5 away shots
        List<V24MatchEventDto> events = new ArrayList<>();
        events.add(ev(7,  "SHOT",           HOME, 0.12));
        events.add(ev(12, "SHOT_ON_TARGET", HOME, 0.12));
        events.add(ev(12, "GOAL",           HOME, 0.12));
        events.add(ev(22, "SHOT",           AWAY, 0.18));
        events.add(ev(33, "SHOT",           HOME, 0.08));
        events.add(ev(40, "FOUL",           AWAY, 0.0));
        events.add(ev(48, "SHOT",           AWAY, 0.07));
        events.add(ev(55, "SHOT",           HOME, 0.20));
        events.add(ev(60, "SHOT_ON_TARGET", HOME, 0.20));
        events.add(ev(60, "SAVE",           AWAY, 0.0));
        events.add(ev(70, "SHOT",           AWAY, 0.25));
        events.add(ev(75, "SHOT",           HOME, 0.05));
        events.add(ev(82, "SHOT",           HOME, 0.30));
        events.add(ev(82, "SHOT_ON_TARGET", HOME, 0.30));
        events.add(ev(82, "GOAL",           HOME, 0.30));
        events.add(ev(89, "SHOT",           AWAY, 0.10));
        events.add(ev(89, "SHOT_ON_TARGET", AWAY, 0.10));
        events.add(ev(89, "GOAL",           AWAY, 0.10));

        V24DetailedMatchData d = detail(events);

        // Snapshot at minute 50: should include events up to min 50 only
        V24TimelineSnapshot s50 = TimelineSnapshotBuilder.build(d, 50);
        assertEquals(50, s50.minute());
        // Events: 7, 12, 12, 22, 33, 40, 48 (7 events)
        assertEquals(7, s50.events().size());
        // Home: 1 goal (min 12) ; Away: 0 goals
        assertEquals(1, s50.homeGoals());
        assertEquals(0, s50.awayGoals());
        // Home shots at minute 50: min 7, 33 → 2
        // Away shots at minute 50: min 22, 48 → 2
        assertEquals(2, s50.homeShots());
        assertEquals(2, s50.awayShots());
        // Home xG at min 50: SHOT 0.12 (min 7) + SHOT_ON_TARGET 0.12 (min 12) + GOAL 0.12 (min 12) + SHOT 0.08 (min 33) = 0.44
        assertEquals(0.44, s50.homeXg(), 0.001);
        // Away xG at min 50: SHOT 0.18 + SHOT 0.07 = 0.25
        assertEquals(0.25, s50.awayXg(), 0.001);

        // Full snapshot at minute 130: all events
        V24TimelineSnapshot sFull = TimelineSnapshotBuilder.build(d, 130);
        assertEquals(130, sFull.minute());
        assertEquals(18, sFull.events().size());
        // Home: 2 goals (min 12, 82)
        assertEquals(2, sFull.homeGoals());
        // Away: 1 goal (min 89)
        assertEquals(1, sFull.awayGoals());
        // Home shots: min 7, 33, 55, 75, 82 = 5
        assertEquals(5, sFull.homeShots());
        // Away shots: min 22, 48, 70, 89 = 4
        assertEquals(4, sFull.awayShots());
    }
}
