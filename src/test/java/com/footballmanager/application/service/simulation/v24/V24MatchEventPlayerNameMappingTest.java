package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE-MATCH-F5.2 BUG-011 regression test.
 *
 * <p>When a manager triggers a substitution via the F2 substitution
 * command, the resulting {@link V24MatchEvent} carries REAL player names
 * ({@code subOff.name()} and {@code subOn.name()}) — NOT generic
 * placeholders like "Player 7 RMA". The previous F5.1 code called
 * {@code liveSession.mutateContext(...)} directly, which DROPPED the
 * event entirely (the engine replay from minute N+1 rebuilt the timeline
 * and the new engine run didn't know about the manual sub).
 *
 * <p>This test asserts that:
 * <ol>
 *   <li>After {@code recordManualSubstitution(event)}, the SSE payload
 *       includes the SUBSTITUTION event with REAL player names.</li>
 *   <li>The event is preserved across replays (the engine's replay
 *       replaces the engine timeline, but manualEvents is preserved).</li>
 *   <li>The event's playerName is NOT "Unknown" or "Player X".</li>
 * </ol>
 */
class V24MatchEventPlayerNameMappingTest {

    @Test
    @DisplayName("BUG-011: recordManualSubstitution appends event with REAL player names")
    void recordManualSubstitution_eventHasRealNames() {
        V24MatchContext ctx = buildContext();
        String[] ids = ctx.homeStartingPlayers().stream()
            .map(SessionPlayer::getSessionPlayerId)
            .toArray(String[]::new);
        String playerOffId = ids[0];
        String playerOnId = ctx.homeBenchPlayers().get(0).getSessionPlayerId();
        V24LiveSession session = new V24LiveSession(ctx, 42L);

        // Tick once to advance to minute 1.
        session.tick();

        // Build a manual sub event with REAL player names.
        V24MatchEvent subEvent = new V24MatchEvent(
            1,                                  // minute
            V24MatchEventType.SUBSTITUTION,
            ctx.homeTeamId(),                   // teamId
            playerOffId,                        // playerOffId (real from session)
            "Vinícius Jr.",                     // playerOffName (REAL name)
            playerOnId,                         // playerOnId (real from session)
            "Endrick",                          // playerOnName (REAL name)
            0.0,
            "Substitution: Endrick on for Vinícius Jr."
        );
        session.recordManualSubstitution(subEvent);

        // Trigger a tick so the snapshot includes the manual event.
        V24LiveSnapshot snap = session.tick();
        // The SUBSTITUTION event is a manual event, NOT a noise event,
        // so it should appear in the visible SSE payload. The engine ALSO
        // emits a SUBSTITUTION event when applying the manual sub (with
        // playerName=null → "Unknown" — see V24DetailedMatchEngine line 238
        // and V24MatchEvent constructor line 54). So we expect to see TWO
        // SUBSTITUTION events in the snapshot: the engine-emitted one
        // (with "Unknown") AND the manualEvents one (with "Vinícius Jr.").
        // The UI should display the manualEvents one preferentially.
        List<V24MatchEvent> subEvents = snap.allEvents().stream()
            .filter(e -> e.type() == V24MatchEventType.SUBSTITUTION)
            .toList();
        V24MatchEvent realSub = subEvents.stream()
            .filter(e -> "Vinícius Jr.".equals(e.playerName()))
            .findFirst()
            .orElse(null);
        assertNotNull(realSub,
            "BUG-011 violated: SUBSTITUTION event with REAL player name "
            + "('Vinícius Jr.') is missing from the SSE payload. "
            + "Found " + subEvents.size() + " SUBSTITUTION events: "
            + subEvents.stream().map(V24MatchEvent::playerName).toList() + ". "
            + "The previous code called mutateContext() directly which dropped the event. "
            + "The fix calls recordManualSubstitution(event) which appends to manualEvents.");
        assertEquals("Endrick", realSub.relatedPlayerName(),
            "BUG-011 violated: SUBSTITUTION event's playerOnName should be 'Endrick', "
            + "got '" + realSub.relatedPlayerName() + "'");
    }

    @Test
    @DisplayName("BUG-011: manual substitution event survives an engine replay")
    void recordManualSubstitution_eventPreservedAcrossReplay() {
        V24MatchContext ctx = buildContext();
        String[] ids = ctx.homeStartingPlayers().stream()
            .map(SessionPlayer::getSessionPlayerId)
            .toArray(String[]::new);
        String playerOffId = ids[0];
        String playerOnId = ctx.homeBenchPlayers().get(0).getSessionPlayerId();
        V24LiveSession session = new V24LiveSession(ctx, 42L);

        // Tick to minute 5.
        for (int i = 0; i < 5; i++) session.tick();

        // Record a manual sub at minute 5.
        V24MatchEvent subEvent = new V24MatchEvent(
            5,
            V24MatchEventType.SUBSTITUTION,
            ctx.homeTeamId(),
            playerOffId,
            "Mbappé",
            playerOnId,
            "Endrick",
            0.0,
            "Sub: Endrick on for Mbappé"
        );
        session.recordManualSubstitution(subEvent);

        // Continue ticking to minute 30. Each tick replays the engine
        // (the engine timeline is replaced) but manualEvents is preserved.
        for (int i = 6; i <= 30; i++) session.tick();
        V24LiveSnapshot snap = session.tick();

        // The manual SUBSTITUTION event at minute 5 should still be visible.
        // The engine ALSO emits a sub event on every replay, but those
        // have playerName="Unknown" (no name in the engine). The
        // manualEvents one with "Mbappé" should be preserved.
        List<V24MatchEvent> subEvents = snap.allEvents().stream()
            .filter(e -> e.type() == V24MatchEventType.SUBSTITUTION)
            .toList();
        V24MatchEvent realSub = subEvents.stream()
            .filter(e -> "Mbappé".equals(e.playerName()))
            .findFirst()
            .orElse(null);
        assertNotNull(realSub,
            "BUG-011 violated: manual SUBSTITUTION event was LOST after engine replay. "
            + "manualEvents must be preserved across replays. Found "
            + subEvents.size() + " sub events: "
            + subEvents.stream().map(V24MatchEvent::playerName).toList());
    }

    @Test
    @DisplayName("BUG-011: recordManualSubstitution does NOT accept null events")
    void recordManualSubstitution_nullEvent_throws() {
        V24MatchContext ctx = buildContext();
        V24LiveSession session = new V24LiveSession(ctx, 42L);
        try {
            session.recordManualSubstitution(null);
            assertFalse(true, "Expected IllegalArgumentException for null event");
        } catch (IllegalArgumentException e) {
            // Expected.
            assertTrue(e.getMessage().contains("null"));
        }
    }

    @Test
    @DisplayName("BUG-011: recordManualSubstitution does NOT accept non-SUBSTITUTION events")
    void recordManualSubstitution_wrongType_throws() {
        V24MatchContext ctx = buildContext();
        V24LiveSession session = new V24LiveSession(ctx, 42L);
        String playerOffId = ctx.homeStartingPlayers().get(0).getSessionPlayerId();
        V24MatchEvent goalEvent = new V24MatchEvent(
            5, V24MatchEventType.GOAL, ctx.homeTeamId(),
            playerOffId, "Mbappé", null, null, 0.5, "GOAL"
        );
        try {
            session.recordManualSubstitution(goalEvent);
            assertFalse(true, "Expected IllegalArgumentException for non-SUBSTITUTION event");
        } catch (IllegalArgumentException e) {
            // Expected.
            assertTrue(e.getMessage().contains("SUBSTITUTION"));
        }
    }

    // ========== Helpers ==========

    private V24MatchContext buildContext() {
        String matchId = "match-bug-011-" + UUID.randomUUID();
        SessionTeam homeTeam = makeTeam("home-" + matchId, "Home FC");
        SessionTeam awayTeam = makeTeam("away-" + matchId, "Away FC");
        return new V24MatchContext(
            matchId,
            homeTeam.getSessionTeamId(),
            awayTeam.getSessionTeamId(),
            homeTeam, awayTeam,
            makePlayers("home", 11, 75),
            makePlayers("away", 11, 75),
            makePlayers("home_bench", 7, 70),  // bench with 7 players
            makePlayers("away_bench", 7, 70),
            "4-3-3", "4-3-3",
            TeamStyle.BALANCED, TeamStyle.BALANCED
        );
    }

    private SessionTeam makeTeam(String id, String name) {
        return SessionTeam.fromRealTeam(
            UUID.nameUUIDFromBytes(id.getBytes()),
            "world_" + id, name, "Country",
            BigDecimal.ZERO, "4-3-3", null);
    }

    private List<SessionPlayer> makePlayers(String prefix, int count, int ovr) {
        List<SessionPlayer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = prefix + "_p" + i;
            // Use fromWorldPlayer (not custom) so we can control the
            // sessionPlayerId — the V24MatchContext.withManualSubstitution
            // validates by sessionPlayerId.
            SessionPlayer p = SessionPlayer.fromWorldPlayer(id, id, "MID", 25, ovr);
            list.add(p);
        }
        return list;
    }
}
