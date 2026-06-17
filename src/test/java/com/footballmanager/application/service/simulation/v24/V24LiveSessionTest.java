package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE-MATCH-F1-POC: unit tests for {@link V24LiveSession}.
 *
 * <p>Critical test in this file is
 * {@code recordManualSubstitution_doesNotAlterResult()} — it enforces D1=B
 * (manual substitutions are UI-only and do NOT alter the match result).
 *
 * <p>The match engine pre-simulates the full 90-minute timeline on the first
 * {@code tick()}; subsequent ticks only filter cached events. Injecting a
 * manual substitution via {@code recordManualSubstitution} adds the event to
 * the visible timeline but does NOT recompute homeGoals/awayGoals.
 */
class V24LiveSessionTest {

    private V24LiveSession session;
    private V24MatchContext context;
    private String homeTeamId;
    private String awayTeamId;

    @BeforeEach
    void setUp() {
        homeTeamId = "team-home";
        awayTeamId = "team-away";
        context = buildContext();
        session = new V24LiveSession(context, 42L);
        // Tick once so the engine runs the pre-simulation and sets homeGoals/awayGoals.
        // Without this, currentMinute is still 0 and homeGoals/awayGoals are 0.
        session.tick();
    }

    @Test
    @DisplayName("recordManualSubstitution adds SUBSTITUTION event to accumulatedEvents")
    void recordManualSubstitution_addsEventToAccumulatedEvents() {
        // Snapshot baseline (post-tick #1)
        int baselineSize = session.accumulatedEvents().size();
        int baselineMinute = session.currentMinute();

        // Build a valid SUBSTITUTION event
        V24MatchEvent event = new V24MatchEvent(
            baselineMinute,
            V24MatchEventType.SUBSTITUTION,
            homeTeamId,
            "home-starter-0",
            "Home Starter 0",
            "home-bench-0",
            "Home Bench 0",
            0.0,
            "Substitution: Home Bench 0 on for Home Starter 0"
        );

        session.recordManualSubstitution(event);

        // accumulatedEvents grew by exactly 1
        assertEquals(baselineSize + 1, session.accumulatedEvents().size());
        // The latest event is our manual substitution
        V24MatchEvent last = session.accumulatedEvents().get(
            session.accumulatedEvents().size() - 1);
        assertEquals(V24MatchEventType.SUBSTITUTION, last.type());
        assertEquals("home-starter-0", last.playerId());
        assertEquals("home-bench-0", last.relatedPlayerId());
        assertEquals(baselineMinute, last.minute());
    }

    @Test
    @DisplayName("CRITICAL D1=B: recordManualSubstitution does NOT alter homeGoals/awayGoals")
    void recordManualSubstitution_doesNotAlterResult() {
        // Snapshot baseline (post-tick #1, homeGoals/awayGoals have been computed
        // by the engine's pre-simulation).
        // Tick a few more times so goals may have been scored.
        for (int i = 0; i < 10; i++) {
            session.tick();
        }
        int preHomeGoals = countGoals(session.accumulatedEvents(), homeTeamId);
        int preAwayGoals = countGoals(session.accumulatedEvents(), awayTeamId);
        int preTotalEvents = session.accumulatedEvents().size();
        int currentMinute = session.currentMinute();

        // Inject a manual substitution event.
        V24MatchEvent subEvent = new V24MatchEvent(
            currentMinute,
            V24MatchEventType.SUBSTITUTION,
            homeTeamId,
            "home-starter-0",
            "Home Starter 0",
            "home-bench-0",
            "Home Bench 0",
            0.0,
            "Substitution: Home Bench 0 on for Home Starter 0"
        );
        session.recordManualSubstitution(subEvent);

        // CRITICAL D1=B ASSERTIONS:
        // 1. Goals count from accumulatedEvents is UNCHANGED.
        assertEquals(preHomeGoals, countGoals(session.accumulatedEvents(), homeTeamId),
            "D1=B violated: homeGoals changed after manual substitution");
        assertEquals(preAwayGoals, countGoals(session.accumulatedEvents(), awayTeamId),
            "D1=B violated: awayGoals changed after manual substitution");
        // 2. The total events grew by exactly 1 (the substitution event).
        assertEquals(preTotalEvents + 1, session.accumulatedEvents().size(),
            "Substitution event must be appended exactly once");
        // 3. Next snapshot reflects the same goals (substitution is not a goal).
        V24LiveSnapshot nextSnapshot = session.tick();
        assertEquals(preHomeGoals, nextSnapshot.homeGoals(),
            "D1=B violated: next snapshot.homeGoals changed after manual substitution");
        assertEquals(preAwayGoals, nextSnapshot.awayGoals(),
            "D1=B violated: next snapshot.awayGoals changed after manual substitution");
    }

    @Test
    @DisplayName("recordManualSubstitution throws when event type is not SUBSTITUTION")
    void recordManualSubstitution_rejectsNonSubstitutionEvent() {
        V24MatchEvent goalEvent = new V24MatchEvent(
            1,
            V24MatchEventType.GOAL,
            homeTeamId,
            "home-starter-0",
            "Home Starter 0",
            null,
            null,
            0.5,
            "GOAL!"
        );
        assertThrows(IllegalArgumentException.class,
            () -> session.recordManualSubstitution(goalEvent));
    }

    @Test
    @DisplayName("recordManualSubstitution throws when match is finished")
    void recordManualSubstitution_throwsWhenMatchFinished() {
        // Tick past 90 minutes to mark as finished
        for (int i = 0; i < 95; i++) {
            session.tick();
        }
        assertTrue(session.isFinished());

        V24MatchEvent subEvent = new V24MatchEvent(
            90,
            V24MatchEventType.SUBSTITUTION,
            homeTeamId,
            "home-starter-0",
            "Home Starter 0",
            "home-bench-0",
            "Home Bench 0",
            0.0,
            "Substitution: Home Bench 0 on for Home Starter 0"
        );
        assertThrows(IllegalStateException.class,
            () -> session.recordManualSubstitution(subEvent));
    }

    @Test
    @DisplayName("accumulatedEvents returns an unmodifiable view")
    void accumulatedEvents_isUnmodifiable() {
        V24MatchEvent subEvent = new V24MatchEvent(
            1,
            V24MatchEventType.SUBSTITUTION,
            homeTeamId,
            "x",
            "X",
            "y",
            "Y",
            0.0,
            "x→y"
        );
        session.recordManualSubstitution(subEvent);

        List<V24MatchEvent> view = session.accumulatedEvents();
        assertThrows(UnsupportedOperationException.class,
            () -> view.add(subEvent),
            "accumulatedEvents() should return an unmodifiable view");
    }

    @Test
    @DisplayName("currentMinute and context accessors return the expected values")
    void accessors_returnExpectedValues() {
        // currentMinute reflects the latest tick (started at 0, ticked once -> 1).
        assertEquals(1, session.currentMinute());
        assertNotNull(session.context());
        assertEquals(context, session.context());
        // Accumulated events is non-null
        assertNotNull(session.accumulatedEvents());
        assertFalse(session.accumulatedEvents().isEmpty());
    }

    // ========== Helpers ==========

    private V24MatchContext buildContext() {
        SessionTeam homeTeam = SessionTeam.custom(homeTeamId, "Home FC", "ARG",
            BigDecimal.valueOf(1_000_000L), "4-3-3");
        SessionTeam awayTeam = SessionTeam.custom(awayTeamId, "Away FC", "BRA",
            BigDecimal.valueOf(1_000_000L), "4-4-2");

        List<SessionPlayer> homeStarting = makePlayers(homeTeamId, "starter", 11);
        List<SessionPlayer> homeBench = makePlayers(homeTeamId, "bench", 5);
        List<SessionPlayer> awayStarting = makePlayers(awayTeamId, "starter", 11);
        List<SessionPlayer> awayBench = makePlayers(awayTeamId, "bench", 5);

        return new V24MatchContext(
            "match-1",
            homeTeamId,
            awayTeamId,
            homeTeam,
            awayTeam,
            homeStarting,
            awayStarting,
            homeBench,
            awayBench,
            "4-3-3",
            "4-4-2",
            TeamStyle.BALANCED,
            TeamStyle.BALANCED
        );
    }

    private List<SessionPlayer> makePlayers(String teamId, String suffix, int count) {
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String position = (i == 0) ? "GK"
                : (i <= 4) ? "DEF"
                : (i <= 7) ? "MID"
                : (i <= 9) ? "WINGER" : "ATT";
            String id = teamId + "-" + suffix + "-" + i;
            SessionPlayer sp = SessionPlayer.custom(id, 25, position,
                70, 70, 70, 70, 70, 70, BigDecimal.valueOf(70000L));
            sp.setEnergy(100);
            players.add(sp);
        }
        return players;
    }

    private int countGoals(List<V24MatchEvent> events, String teamId) {
        int count = 0;
        for (V24MatchEvent e : events) {
            if (e.type() == V24MatchEventType.GOAL
                && e.teamId() != null
                && e.teamId().equals(teamId)) {
                count++;
            }
        }
        return count;
    }
}
