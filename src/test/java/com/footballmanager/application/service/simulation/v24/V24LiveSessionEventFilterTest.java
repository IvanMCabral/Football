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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * LIVE-MATCH-F5.2 BUG-009 regression test.
 *
 * <p>Without the NOISE_EVENTS filter, the V24 engine produces ~50-80
 * events per 90-minute match (measured in F5.1 with V24D6U4 tuning).
 * The F5.2 spec requires ~30-50 "important" events visible in the live
 * SSE stream — enough to keep the user informed without flooding the UI.
 *
 * <p>This test asserts:
 * <ol>
 *   <li>The visible (SSE) event list has between 25 and 55 events at minute 90.</li>
 *   <li>The visible event list does NOT contain any of the NOISE_EVENTS
 *       (CHANCE_CREATED, OFFSIDE, CORNER, FOUL, MISS, BLOCK).</li>
 *   <li>The visible event list DOES contain the "important" event types
 *       (GOAL, YELLOW_CARD, RED_CARD, SHOT_ON_TARGET, SUBSTITUTION,
 *       TACTICAL_CHANGE, INJURY) — when the engine produces them.</li>
 *   <li>The 3 seeds used produce consistent counts (within the 25-55 range).</li>
 * </ol>
 */
class V24LiveSessionEventFilterTest {

    @Test
    @DisplayName("BUG-009: SSE event count at minute 90 is between 25 and 55 (across 3 seeds)")
    void ssePayload_has25to55Events() {
        long[] seeds = {42L, 123L, 9999L};
        for (long seed : seeds) {
            V24MatchContext ctx = buildContext();
            V24LiveSession session = new V24LiveSession(ctx, seed);
            // Tick to minute 90.
            for (int i = 0; i < 90; i++) {
                session.tick();
            }
            V24LiveSnapshot snap = session.tick();
            int visibleCount = snap.allEvents().size();
            assertTrue(visibleCount >= 25 && visibleCount <= 55,
                "BUG-009 violated for seed=" + seed + ": SSE payload has " + visibleCount
                + " events at minute 90. Expected 25-55. "
                + "Without the noise filter, the engine produces ~50-80 events.");
        }
    }

    @Test
    @DisplayName("BUG-009: visible event list does NOT contain any NOISE_EVENTS")
    void ssePayload_filtersNoiseEvents() {
        V24MatchContext ctx = buildContext();
        V24LiveSession session = new V24LiveSession(ctx, 42L);
        // Tick to minute 90.
        for (int i = 0; i < 90; i++) {
            session.tick();
        }
        V24LiveSnapshot snap = session.tick();
        List<V24MatchEventType> noiseTypes = snap.allEvents().stream()
            .map(V24MatchEvent::type)
            .filter(t -> t == V24MatchEventType.CHANCE_CREATED
                      || t == V24MatchEventType.OFFSIDE
                      || t == V24MatchEventType.CORNER
                      || t == V24MatchEventType.FOUL
                      || t == V24MatchEventType.MISS
                      || t == V24MatchEventType.BLOCK)
            .collect(Collectors.toList());
        assertTrue(noiseTypes.isEmpty(),
            "BUG-009 violated: SSE payload contains NOISE_EVENTS: " + noiseTypes
            + ". These should be filtered out by the buildSnapshot() filter.");
    }

    @Test
    @DisplayName("V24D15-CLEANUP: NOISE_EVENT_THRESHOLD_MIN constant is honoured (Set size >= 6)")
    void noiseEventThresholdMin_constantIsHonoured() throws Exception {
        // Read the constant via reflection so the test breaks if the field is
        // ever renamed. We don't want to couple to the field name in the
        // assertion — the contract is "the Set is at least 6 types big".
        java.lang.reflect.Field thresholdField = V24LiveSession.class
            .getDeclaredField("NOISE_EVENT_THRESHOLD_MIN");
        thresholdField.setAccessible(true);
        int threshold = (int) thresholdField.get(null);
        assertTrue(threshold >= 6,
            "NOISE_EVENT_THRESHOLD_MIN must be >= 6 (F5.2 measured ~30-50 important "
                + "events per match, filtering out ~6 categories). Found: " + threshold);

        // Verify the Set size matches. Indirectly: spawn a V24LiveSession
        // and trigger the static initializer to confirm it doesn't throw.
        // (The static initializer throws if size < threshold; if the class
        //  loaded successfully, the contract holds.)
        V24LiveSession session = new V24LiveSession(buildContext(), 42L);
        assertNotNull(session);
    }

    @Test
    @DisplayName("BUG-009: visible event list KEEPS important event types (GOAL, CARD, etc.)")
    void ssePayload_keepsImportantEvents() {
        V24MatchContext ctx = buildContext();
        V24LiveSession session = new V24LiveSession(ctx, 42L);
        // Tick to minute 90.
        for (int i = 0; i < 90; i++) {
            session.tick();
        }
        V24LiveSnapshot snap = session.tick();
        // Assert that AT LEAST the GOAL events are visible (these are the
        // most important). Other types (SUBSTITUTION, CARD, etc.) depend on
        // the seed; we just need to ensure NO goal is hidden.
        long goalCountInVisible = snap.allEvents().stream()
            .filter(e -> e.type() == V24MatchEventType.GOAL)
            .count();
        long goalCountInFull = session.accumulatedEvents().stream()
            .filter(e -> e.type() == V24MatchEventType.GOAL)
            .count();
        assertEquals(goalCountInFull, goalCountInVisible,
            "BUG-009 violated: SSE payload is missing GOAL events. "
            + "Full timeline has " + goalCountInFull + " GOAL events, "
            + "visible payload has " + goalCountInVisible + ".");
    }

    // ========== Helpers ==========

    private V24MatchContext buildContext() {
        String matchId = "match-bug-009-" + UUID.randomUUID();
        SessionTeam homeTeam = makeTeam("home-" + matchId, "Home FC");
        SessionTeam awayTeam = makeTeam("away-" + matchId, "Away FC");
        return new V24MatchContext(
            matchId,
            homeTeam.getSessionTeamId(),
            awayTeam.getSessionTeamId(),
            homeTeam, awayTeam,
            makePlayers("home", 11, 75),
            makePlayers("away", 11, 75),
            List.of(), List.of(),
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
            SessionPlayer p = SessionPlayer.custom(
                id, 25, "MID",
                ovr, ovr, ovr, ovr, ovr, ovr,
                BigDecimal.valueOf(ovr * 1000));
            list.add(p);
        }
        return list;
    }
}
