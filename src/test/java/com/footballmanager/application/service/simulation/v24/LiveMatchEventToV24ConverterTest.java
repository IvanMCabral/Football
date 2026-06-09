package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6M10: Tests for LiveMatchEventToV24Converter.
 *
 * <p>Tests conversion of live SSE entity.MatchEvent to V24MatchEvent
 * with player attribution via squad lookup.
 */
class LiveMatchEventToV24ConverterTest {

    private static final String HOME = UUID.randomUUID().toString();
    private static final String AWAY = UUID.randomUUID().toString();

    // ========== Test 1: GOAL maps correctly ==========

    @Test
    void convertGoal_eventMapsCorrectly() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        // Player "p_h_0" is the first home player
        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.GOAL,
                45,
                "p_h_0",  // playerName
                "Goal by striker"
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        assertNotNull(v24Event, "GOAL event should convert successfully");
        assertEquals(45, v24Event.minute());
        assertEquals(V24MatchEventType.GOAL, v24Event.type());
        assertEquals(HOME, v24Event.teamId());
        assertEquals("p_h_0", v24Event.playerId());
        assertEquals("Goal by striker", v24Event.description());
        assertEquals(0.0, v24Event.xg()); // MVP: no invented xG
    }

    // ========== Test 2: CARD defaults to YELLOW ==========

    @Test
    void convertCard_withoutRedDescriptor_defaultsToYellow() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.CARD,
                30,
                "p_h_0",
                "Foul on player"  // No red/roja keyword
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        assertNotNull(v24Event, "CARD event should convert");
        assertEquals(V24MatchEventType.YELLOW_CARD, v24Event.type());
    }

    // ========== Test 3: CARD with red descriptor maps to RED ==========

    @Test
    void convertCard_withRedDescriptor_mapsToRed() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.CARD,
                70,
                "p_a_0",
                "Red card for violent conduct"
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        assertNotNull(v24Event, "RED CARD event should convert");
        assertEquals(V24MatchEventType.RED_CARD, v24Event.type());
    }

    // ========== Test 4: INJURY maps correctly ==========

    @Test
    void convertInjury_eventMapsCorrectly() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.INJURY,
                55,
                "p_h_1",
                "Muscle strain"
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        assertNotNull(v24Event);
        assertEquals(V24MatchEventType.INJURY, v24Event.type());
        assertEquals("p_h_1", v24Event.playerId());
    }

    // ========== Test 5: SUBSTITUTION maps correctly ==========

    @Test
    void convertSubstitution_eventMapsCorrectly() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.SUBSTITUTION,
                60,
                "p_h_2",
                "Player substituted"
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        assertNotNull(v24Event);
        assertEquals(V24MatchEventType.SUBSTITUTION, v24Event.type());
    }

    // ========== Test 6: PlayerId resolved by exact name match ==========

    @Test
    void resolvePlayerId_byExactPlayerName_returnsCorrectId() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.GOAL,
                20,
                "p_h_5",  // Exact name from squad
                "Goal"
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        assertNotNull(v24Event);
        assertEquals("p_h_5", v24Event.playerId());
    }

    // ========== Test 7: PlayerId resolved by exact match ==========

    @Test
    void resolvePlayerId_byExactName_returnsCorrectId() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        // Player "p_h_3" exists in HOME squad from makeCareer
        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.GOAL,
                25,
                "p_h_3",  // Exact name from squad
                "Goal"
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        assertNotNull(v24Event, "Exact match should resolve");
        assertEquals("p_h_3", v24Event.playerId());
        assertEquals(HOME, v24Event.teamId());
    }

    // ========== Test 8: Unresolved player returns null and skips event ==========

    @Test
    void resolvePlayerId_notFound_returnsNull() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.GOAL,
                30,
                "Unknown Player",  // Not in squad
                "Goal"
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        assertNull(v24Event, "Unknown player should return null (event skipped)");
    }

    // ========== Test 9: Ambiguous player (in both squads) returns null ==========

    @Test
    void resolveTeamId_ambiguousPlayer_returnsNull() {
        // Create career where same playerName exists in both squads
        String careerId = HOME + "_" + AWAY;
        CareerSave save = new CareerSave();
        save.getData().setCareerId(careerId);
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();

        for (String tid : List.of(HOME, AWAY)) {
            UUID uuid = UUID.fromString(tid);
            SessionTeam team = SessionTeam.fromRealTeam(uuid, "world_" + tid,
                    "Team " + tid, "Country", BigDecimal.ZERO, "4-3-3", null);
            team.setSessionTeamId(tid);
            tm.addSessionTeam(team);
        }

        // Add "Same Name" player to both squads
        SessionPlayer homeP = SessionPlayer.custom("home_same", 25, "MID", 75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
        homeP.setSessionPlayerId("home_same");
        homeP.setName("Same Name");
        pm.addSessionPlayer(homeP);
        tm.assignPlayerToSquad("home_same", HOME);

        SessionPlayer awayP = SessionPlayer.custom("away_same", 25, "MID", 75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
        awayP.setSessionPlayerId("away_same");
        awayP.setName("Same Name");
        pm.addSessionPlayer(awayP);
        tm.assignPlayerToSquad("away_same", AWAY);

        save.setTeamManager(tm);
        save.setPlayerManager(pm);

        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(save, HOME, AWAY);

        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.GOAL,
                40,
                "Same Name",  // Found in both squads
                "Goal"
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        assertNull(v24Event, "Ambiguous player should return null");
    }

    // ========== Test 10: buildTimeline with events ==========

    @Test
    void buildTimeline_withMultipleEvents_createsNonEmptyTimeline() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        List<MatchEvent> liveEvents = List.of(
                MatchEvent.of(MatchEvent.EventType.GOAL, 20, "p_h_0", "Goal 1"),
                MatchEvent.of(MatchEvent.EventType.GOAL, 35, "p_a_0", "Goal 2"),
                MatchEvent.of(MatchEvent.EventType.CARD, 10, "p_h_1", "Foul")
        );

        V24MatchTimeline timeline = converter.buildTimeline(liveEvents);

        assertFalse(timeline.events().isEmpty(), "Timeline should not be empty");
        assertTrue(timeline.size() >= 2, "At least 2 events should convert (GOALs for resolvable players)");
    }

    // ========== Test 11: buildTimeline with null events returns empty ==========

    @Test
    void buildTimeline_withNullEvents_returnsEmptyTimeline() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        V24MatchTimeline timeline = converter.buildTimeline(null);

        assertTrue(timeline.events().isEmpty());
    }

    // ========== Test 12: buildTimeline with empty events returns empty ==========

    @Test
    void buildTimeline_withEmptyEvents_returnsEmptyTimeline() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        V24MatchTimeline timeline = converter.buildTimeline(Collections.emptyList());

        assertTrue(timeline.events().isEmpty());
    }

    // ========== Test 13: null live event returns null ==========

    @Test
    void convert_nullLiveEvent_returnsNull() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        V24MatchEvent v24Event = converter.convert(null);

        assertNull(v24Event);
    }

    // ========== Test 14: converter_jugadorLocal_mapsToHomeSquadRealPlayer ==========

    @Test
    void converter_jugadorLocal_mapsToHomeSquadRealPlayer() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        // "Jugador local" with HOME teamId and matchId
        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.GOAL,
                45,
                null,  // no playerId
                "Jugador local",
                HOME,
                "Gol del equipo local",
                HOME // matchId as pseudo-matchId
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        assertNotNull(v24Event, "Jugador local should resolve to a real home player");
        assertEquals(V24MatchEventType.GOAL, v24Event.type());
        assertEquals(HOME, v24Event.teamId());
        assertNotNull(v24Event.playerId());
        assertFalse(v24Event.playerId().isBlank());
        // V24D6M10: playerName should be the REAL squad name, not "Jugador local"
        assertFalse("Jugador local".equals(v24Event.playerName()),
                "playerName should be real, not generic 'Jugador local': " + v24Event.playerName());
        assertEquals(0.0, v24Event.xg()); // MVP: no invented xG
 }

    // ========== Test 15: converter_jugadorVisitante_mapsToAwaySquadRealPlayer ==========

    @Test
    void converter_jugadorVisitante_mapsToAwaySquadRealPlayer() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        // "Jugador visitante" with AWAY teamId
        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.GOAL,
                67,
                null,  // no playerId
                "Jugador visitante",
                AWAY,
                "Gol del equipo visitante",
                HOME // matchId
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        assertNotNull(v24Event, "Jugador visitante should resolve to a real away player");
        assertEquals(V24MatchEventType.GOAL, v24Event.type());
        assertEquals(AWAY, v24Event.teamId());
        assertNotNull(v24Event.playerId());
        assertFalse(v24Event.playerId().isBlank());
        // V24D6M10: playerName should be the REAL squad name, not "Jugador visitante"
        assertFalse("Jugador visitante".equals(v24Event.playerName()),
                "playerName should be real, not generic 'Jugador visitante': " + v24Event.playerName());
    }

    // ========== Test 16: converter_genericJugador_skipsWhenTeamUnknown ==========

    @Test
    void converter_genericJugador_skipsWhenTeamUnknown() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        // "Jugador" with no teamId - cannot determine team, should skip
        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.GOAL,
                30,
                null,
                "Jugador",
                null,  // no teamId
                "Generic goal"
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        // "Jugador" without team context cannot be resolved, so event is skipped
        assertNull(v24Event, "Generic 'Jugador' without teamId should be skipped");
    }

    // ========== Test 17: convertedGoal_hasRealPlayerIdTeamIdName ==========

    @Test
    void convertedGoal_hasRealPlayerIdTeamIdName() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        // GOAL event with playerId and teamId set directly
        MatchEvent liveEvent = MatchEvent.of(
                MatchEvent.EventType.GOAL,
                22,
                "p_h_3",
                "Real Player",
                HOME,
                "Goal by real player",
                HOME
        );

        V24MatchEvent v24Event = converter.convert(liveEvent);

        assertNotNull(v24Event);
        assertEquals(V24MatchEventType.GOAL, v24Event.type());
        assertEquals(HOME, v24Event.teamId());
        assertEquals("p_h_3", v24Event.playerId());
        assertEquals("Real Player", v24Event.playerName());
        assertEquals(0.0, v24Event.xg());
    }

    // ========== Test 18: liveRoundTimeline_hasGoalEventsWithPlayerIds ==========

    @Test
    void liveRoundTimeline_hasGoalEventsWithPlayerIds() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        List<MatchEvent> liveEvents = List.of(
                // Real player event
                MatchEvent.of(MatchEvent.EventType.GOAL, 15, "p_h_2", "Real Home Player", HOME, "Goal", HOME),
                // Generic "Jugador local" resolved via teamId
                MatchEvent.of(MatchEvent.EventType.GOAL, 38, null, "Jugador local", HOME, "Gol local", HOME),
                // Generic "Jugador visitante" resolved via teamId
                MatchEvent.of(MatchEvent.EventType.GOAL, 72, null, "Jugador visitante", AWAY, "Gol visitante", HOME),
                // Card event
                MatchEvent.of(MatchEvent.EventType.CARD, 55, "p_a_4", "Yellow Card", AWAY, "Foul", HOME)
        );

        V24MatchTimeline timeline = converter.buildTimeline(liveEvents);

        // All4 events should resolve (3 with real playerId, 1 generic with teamId)
        assertEquals(4, timeline.size(), "All4 events should convert with player attribution");

        // Check GOAL events have playerIds
        List<V24MatchEvent> goals = timeline.events().stream()
                .filter(e -> e.type() == V24MatchEventType.GOAL)
                .toList();
        assertEquals(3, goals.size());
        for (V24MatchEvent goal : goals) {
            assertNotNull(goal.playerId());
            assertFalse(goal.playerId().isBlank());
            assertNotNull(goal.teamId());
 }
    }

    // ========== Test19: playerStats_afterLiveRound_hasGoalsWhenScoresExist ==========

    @Test
    void playerStats_afterLiveRound_hasGoalsWhenScoresExist() {
        CareerSave career = makeCareer(HOME, AWAY, 11, 11);
        LiveMatchEventToV24Converter converter = new LiveMatchEventToV24Converter(career, HOME, AWAY);

        // Simulate a match with 2 home goals and 1 away goal
        List<MatchEvent> liveEvents = List.of(
                MatchEvent.of(MatchEvent.EventType.GOAL, 12, null, "Jugador local", HOME, "Gol local", HOME),
                MatchEvent.of(MatchEvent.EventType.GOAL, 45, null, "Jugador local", HOME, "Gol local", HOME),
                MatchEvent.of(MatchEvent.EventType.GOAL, 78, null, "Jugador visitante", AWAY, "Gol visitante", HOME)
        );

        V24MatchTimeline timeline = converter.buildTimeline(liveEvents);

        // Verify all 3 goals resolved to real playerIds
        List<V24MatchEvent> goals = timeline.events().stream()
                .filter(e -> e.type() == V24MatchEventType.GOAL)
                .toList();
        assertEquals(3, goals.size());

        // Home goals should have HOME teamId
        long homeGoals = goals.stream().filter(g -> HOME.equals(g.teamId())).count();
        assertEquals(2, homeGoals, "Should have 2 home goals");

        // Away goals should have AWAY teamId
        long awayGoals = goals.stream().filter(g -> AWAY.equals(g.teamId())).count();
        assertEquals(1, awayGoals, "Should have 1 away goal");

        // All goals should have real playerIds
        for (V24MatchEvent goal : goals) {
            assertNotNull(goal.playerId());
            assertFalse(goal.playerId().isBlank());
        }
    }

    // ========== Helpers ==========

    private CareerSave makeCareer(String homeTeamId, String awayTeamId,
                                  int homeStarterCount, int awayStarterCount) {
        String careerId = homeTeamId + "_" + awayTeamId;
        CareerSave save = new CareerSave();
        save.getData().setCareerId(careerId);
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();

        for (String tid : List.of(homeTeamId, awayTeamId)) {
            UUID uuid = UUID.fromString(tid);
            SessionTeam team = SessionTeam.fromRealTeam(uuid, "world_" + tid,
                    "Team " + tid, "Country", BigDecimal.ZERO, "4-3-3", null);
            team.setSessionTeamId(tid);
            tm.addSessionTeam(team);
        }

        List<SessionPlayer> homePlayers = new ArrayList<>();
        for (int i = 0; i < homeStarterCount; i++) {
            SessionPlayer p = SessionPlayer.custom("p_h_" + i, 25,
                    i == 0 ? "ST" : "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId("p_h_" + i);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(p.getSessionPlayerId(), homeTeamId);
            homePlayers.add(p);
        }

        List<SessionPlayer> awayPlayers = new ArrayList<>();
        for (int i = 0; i < awayStarterCount; i++) {
            SessionPlayer p = SessionPlayer.custom("p_a_" + i, 25,
                    i == 0 ? "ST" : "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setSessionPlayerId("p_a_" + i);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(p.getSessionPlayerId(), awayTeamId);
            awayPlayers.add(p);
        }

        save.setTeamManager(tm);
        save.setPlayerManager(pm);

        save.setTournamentState(new com.footballmanager.domain.model.entity.TournamentState());
        return save;
    }
}
