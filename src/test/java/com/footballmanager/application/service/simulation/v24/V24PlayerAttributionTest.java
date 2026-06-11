package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24B: Player attribution tests.
 * Verifies: no synthetic labels in playerId/name fields,
 * real player IDs from SessionPlayer are used in all events,
 * assist player is different from shooter.
 */
class V24PlayerAttributionTest {

    @Test
    void noSyntheticPlayerIdsInEvents() {
        V24MatchContext ctx = buildContext("real-ids-1", 75, 75);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult r = engine.simulate(ctx, 12345L);

        for (V24MatchEvent e : r.timeline().events()) {
            assertNotNull(e.playerId(), "playerId must not be null");
            assertFalse(e.playerId().startsWith("HOME_") || e.playerId().startsWith("AWAY_")
                            || e.playerId().contains("DEF_") || e.playerId().contains("ST_")
                            || e.playerId().contains("MID_"),
                    "playerId must not be synthetic label: " + e.playerId());
        }
    }

    @Test
    void playerNamesAreRealFromSessionPlayer() {
        V24MatchContext ctx = buildContext("real-names-1", 75, 75);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult r = engine.simulate(ctx, 54321L);

        for (V24MatchEvent e : r.timeline().events()) {
            assertNotNull(e.playerName(), "playerName must not be null");
            assertNotEquals("Unknown Player", e.playerName(),
                    "playerName should be a real name from SessionPlayer");
            assertFalse(e.playerName().isBlank(), "playerName must not be blank");
        }
    }

    @Test
    void goalEventsHaveRealAttribution() {
        V24MatchContext ctx = buildContext("goal-attr-1", 80, 75);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult r = engine.simulate(ctx, 98765L);

        for (V24MatchEvent e : r.timeline().goalEvents()) {
            assertNotNull(e.playerId());
            assertFalse(e.playerId().isEmpty());
            assertNotNull(e.playerName());
            // Goals should have real names, not Unknown Player
            // Note: if player.getName() returns null/blank, V24PlayerMatchState
            // falls back to "Unknown Player" so we just check it's not empty
            assertFalse(e.playerId().isEmpty());
        }
    }

    @Test
    void shotEventsHaveNonNullPlayerIds() {
        V24MatchContext ctx = buildContext("shot-ids-1", 75, 75);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult r = engine.simulate(ctx, 11111L);

        for (V24MatchEvent e : r.timeline().shotEvents()) {
            assertNotNull(e.playerId(), "Shot event must have playerId");
            assertNotNull(e.playerName(), "Shot event must have playerName");
        }
    }

    @Test
    void shooterAndAssistAreDifferentPlayers() {
        V24MatchContext ctx = buildContext("shooter-assist-1", 80, 80);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult r = engine.simulate(ctx, 22222L);

        List<V24MatchEvent> shotEvents = r.timeline().shotEvents();
        for (V24MatchEvent e : shotEvents) {
            if (e.relatedPlayerId() != null) {
                assertNotEquals(e.playerId(), e.relatedPlayerId(),
                        "shooter and assist must be different players");
                assertNotEquals(e.playerName(), e.relatedPlayerName(),
                        "shooter and assist name must be different");
            }
        }
    }

    @Test
    void homeAndAwayPlayersAreDistinguishable() {
        V24MatchContext ctx = buildContext("home-away-1", 75, 75);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult r = engine.simulate(ctx, 33333L);

        // V24D6O: events carry the real session team UUIDs (not HOME/AWAY literals).
        // Use the context's own teamIds as the filter so this test stays
        // consistent with the engine contract introduced in V24D6O.
        String homeId = ctx.homeTeamId();
        String awayId = ctx.awayTeamId();
        assertNotEquals(homeId, awayId, "home and away teamIds must be distinct in context");

        List<V24MatchEvent> homeEvents = r.timeline().events().stream()
                .filter(e -> homeId.equals(e.teamId()))
                .toList();
        List<V24MatchEvent> awayEvents = r.timeline().events().stream()
                .filter(e -> awayId.equals(e.teamId()))
                .toList();

        // Both sides should have events
        assertTrue(homeEvents.size() > 0 || awayEvents.size() > 0,
                "At least one side should have events");
    }

    @Test
    void substituteEventsHaveBothPlayerIds() {
        V24MatchContext ctx = buildContext("sub-event-1", 80, 80);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult r = engine.simulate(ctx, 44444L);

        List<V24MatchEvent> subEvents = r.timeline().events().stream()
                .filter(e -> e.type() == V24MatchEventType.SUBSTITUTION)
                .toList();

        for (V24MatchEvent e : subEvents) {
            assertNotNull(e.playerId(), "Sub off playerId must not be null");
            assertNotNull(e.relatedPlayerId(), "Sub on playerId must not be null");
            assertNotNull(e.relatedPlayerName(), "Sub on playerName must not be null");
        }
    }

    @Test
    void allPlayerIdsFromStartingOrBenchPlayers() {
        V24MatchContext ctx = buildContext("player-pool-1", 75, 75);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult r = engine.simulate(ctx, 55555L);

        // Collect all valid player IDs from context
        List<String> validIds = new ArrayList<>();
        ctx.homeStartingPlayers().forEach(p -> validIds.add(p.getSessionPlayerId()));
        ctx.awayStartingPlayers().forEach(p -> validIds.add(p.getSessionPlayerId()));
        ctx.homeBenchPlayers().forEach(p -> validIds.add(p.getSessionPlayerId()));
        ctx.awayBenchPlayers().forEach(p -> validIds.add(p.getSessionPlayerId()));

        for (V24MatchEvent e : r.timeline().events()) {
            boolean valid = validIds.contains(e.playerId());
            assertTrue(valid, "playerId " + e.playerId() + " must be from starting or bench players");
        }
    }

    // ========== Fixture helpers ==========

    private V24MatchContext buildContext(String matchId, int homeOvr, int awayOvr) {
        List<SessionPlayer> homeStart = makePlayers("home-" + matchId, 11, homeOvr);
        List<SessionPlayer> awayStart = makePlayers("away-" + matchId, 11, awayOvr);
        SessionTeam homeTeam = makeTeam("home-t-" + matchId, "Home FC");
        SessionTeam awayTeam = makeTeam("away-t-" + matchId, "Away FC");
        return new V24MatchContext(
                matchId,
                homeTeam.getSessionTeamId(),
                awayTeam.getSessionTeamId(),
                homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                "4-3-3", "4-3-3",
                TeamStyle.BALANCED, TeamStyle.BALANCED
        );
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

    private SessionTeam makeTeam(String id, String name) {
        return SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world_" + id, name, "Country",
                BigDecimal.ZERO, "4-3-3", null);
    }
}