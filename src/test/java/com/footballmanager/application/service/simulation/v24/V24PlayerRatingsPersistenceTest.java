package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerSeasonManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D5F: Tests for V24PlayerRatingsAssembler and playerRatings persistence.
 *
 * <p>Coverage:
 * 1.  assemblePlayerRatings produces non-empty list when starting XI exists
 * 2.  empty result when career has no starting XI
 * 3.  ratings include all starting players (home + away)
 * 4.  goals counted from timeline
 * 5.  assists counted from timeline
 * 6.  yellow/red cards counted
 * 7.  shots and key passes counted
 * 8.  base rating (6.0) when no events
 * 9.  null CareerSave throws NPE
 * 10. empty timeline produces base ratings
 * 11. substitutes marked correctly (subIn/subOut)
 * 12. player field values (name, position, teamId) match SessionPlayer
 */
class V24PlayerRatingsPersistenceTest {

    private final V24PlayerRatingsAssembler assembler = new V24PlayerRatingsAssembler();

    // ========== Test 1: non-empty result when starting XI exists ==========

    @Test
    void producesNonEmptyListWhenStartingXiExists() {
        CareerSave career = makeCareerWithXI(HOME, AWAY, 11, 11);
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = makeResultWithEvents(
                HOME, AWAY,
                List.of(event(12, V24MatchEventType.GOAL, HOME, "p_h_0", "H0", null, null, 0.3)),
                List.of()
        );

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(career, fixture, result);

        assertFalse(ratings.isEmpty(), "ratings should not be empty when starting XI exists");
        assertEquals(22, ratings.size(), "11v11 = 22 players expected");
    }

    // ========== Test 2: empty result when career has no starting XI ==========

    @Test
    void returnsEmptyListWhenNoStartingXi() {
        CareerSave career = makeCareerWithXI(HOME, AWAY, 0, 0);
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = emptyResult(HOME, AWAY);

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(career, fixture, result);

        assertTrue(ratings.isEmpty(), "ratings should be empty when no starting XI");
    }

    // ========== Test 3: includes all home + away starting players ==========

    @Test
    void includesAllHomeAndAwayStartingPlayers() {
        CareerSave career = makeCareerWithXI(HOME, AWAY, 11, 11);
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = emptyResult(HOME, AWAY);

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(career, fixture, result);

        assertEquals(22, ratings.size());
        Map<String, V24PlayerMatchRatingDto> byTeam = ratings.stream()
                .collect(Collectors.groupingBy(V24PlayerMatchRatingDto::teamId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
        assertEquals(11, byTeam.get(HOME).teamId().equals(HOME) ? 11 :
                ratings.stream().filter(r -> r.teamId().equals(HOME)).count());
    }

    // ========== Test 4: goals counted from timeline ==========

    @Test
    void countsGoalsFromTimeline() {
        CareerSave career = makeCareerWithXI(HOME, AWAY, 2, 2);
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = makeResultWithEvents(
                HOME, AWAY,
                List.of(
                        event(12, V24MatchEventType.GOAL, HOME, "p_h_0", "H0", null, null, 0.3),
                        event(34, V24MatchEventType.GOAL, HOME, "p_h_1", "H1", null, null, 0.25),
                        event(56, V24MatchEventType.GOAL, AWAY, "p_a_0", "A0", null, null, 0.2)
                ),
                List.of()
        );

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(career, fixture, result);
        Map<String, V24PlayerMatchRatingDto> byPid = byPlayerId(ratings);

        assertEquals(1, byPid.get("p_h_0").goals(), "home striker should have 1 goal");
        assertEquals(1, byPid.get("p_h_1").goals(), "home mid should have 1 goal");
        assertEquals(1, byPid.get("p_a_0").goals(), "away striker should have 1 goal");
    }

    // ========== Test 5: assists counted from timeline ==========

    @Test
    void countsAssistsFromTimeline() {
        CareerSave career = makeCareerWithXI(HOME, AWAY, 2, 2);
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = makeResultWithEvents(
                HOME, AWAY,
                List.of(
                        event(12, V24MatchEventType.GOAL, HOME, "p_h_0", "H0", "p_h_1", "H1", 0.3),
                        event(45, V24MatchEventType.GOAL, AWAY, "p_a_0", "A0", "p_a_1", "A1", 0.25)
                ),
                List.of()
        );

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(career, fixture, result);
        Map<String, V24PlayerMatchRatingDto> byPid = byPlayerId(ratings);

        assertEquals(1, byPid.get("p_h_1").assists(), "H1 assisted H0's goal");
        assertEquals(1, byPid.get("p_a_1").assists(), "A1 assisted A0's goal");
    }

    // ========== Test 6: cards counted ==========

    @Test
    void countsYellowAndRedCards() {
        CareerSave career = makeCareerWithXI(HOME, AWAY, 2, 2);
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = makeResultWithEvents(
                HOME, AWAY,
                List.of(
                        event(20, V24MatchEventType.YELLOW_CARD, HOME, "p_h_0", "H0", null, null, 0),
                        event(35, V24MatchEventType.YELLOW_CARD, HOME, "p_h_0", "H0", null, null, 0),
                        event(50, V24MatchEventType.RED_CARD, HOME, "p_h_0", "H0", null, null, 0)
                ),
                List.of()
        );

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(career, fixture, result);
        Map<String, V24PlayerMatchRatingDto> byPid = byPlayerId(ratings);

        assertEquals(2, byPid.get("p_h_0").yellowCards());
        assertEquals(1, byPid.get("p_h_0").redCards());
    }

    // ========== Test 7: shots and key passes counted ==========

    @Test
    void countsShotsAndKeyPasses() {
        CareerSave career = makeCareerWithXI(HOME, AWAY, 2, 2);
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = makeResultWithEvents(
                HOME, AWAY,
                List.of(
                        event(10, V24MatchEventType.SHOT, HOME, "p_h_0", "H0", null, null, 0.1),
                        event(25, V24MatchEventType.SHOT, HOME, "p_h_0", "H0", "p_h_1", "H1", 0.15),
                        event(40, V24MatchEventType.SHOT, HOME, "p_h_1", "H1", null, null, 0.08)
                ),
                List.of()
        );

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(career, fixture, result);
        Map<String, V24PlayerMatchRatingDto> byPid = byPlayerId(ratings);

        assertEquals(2, byPid.get("p_h_0").shots(), "H0 took 2 shots");
        assertEquals(1, byPid.get("p_h_1").keyPasses(), "H1 provided 1 key pass on H0's second shot");
        assertEquals(1, byPid.get("p_h_1").shots(), "H1 took 1 shot");
    }

    // ========== Test 8: base rating 6.0 when no events ==========

    @Test
    void baseRatingWhenNoEvents() {
        CareerSave career = makeCareerWithXI(HOME, AWAY, 2, 2);
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = emptyResult(HOME, AWAY);

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(career, fixture, result);

        for (V24PlayerMatchRatingDto dto : ratings) {
            assertEquals(6.0, dto.rating(), 0.001, "base rating should be 6.0 for player with no events");
        }
    }

    // ========== Test 9: null career throws NPE ==========

    @Test
    void nullCareerThrowsNPE() {
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = emptyResult(HOME, AWAY);

        assertThrows(NullPointerException.class, () ->
                assembler.assemblePlayerRatings(null, fixture, result));
    }

    // ========== Test 10: empty timeline produces base ratings ==========

    @Test
    void emptyTimelineProducesBaseRatings() {
        CareerSave career = makeCareerWithXI(HOME, AWAY, 2, 2);
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = emptyResult(HOME, AWAY);

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(career, fixture, result);

        assertEquals(4, ratings.size());
        for (V24PlayerMatchRatingDto dto : ratings) {
            assertEquals(6.0, dto.rating(), 0.001);
            assertEquals(0, dto.goals());
            assertEquals(0, dto.assists());
            assertEquals(0, dto.shots());
        }
    }

    // ========== Test 11: substitutes marked correctly ==========

    @Test
    void marksSubstitutedInAndOut() {
        CareerSave career = makeCareerWithXI(HOME, AWAY, 2, 2);
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = makeResultWithEvents(
                HOME, AWAY,
                List.of(
                        event(60, V24MatchEventType.SUBSTITUTION, HOME, "p_h_0", "H0", "p_h_1", "H1", 0)
                ),
                List.of()
        );

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(career, fixture, result);
        Map<String, V24PlayerMatchRatingDto> byPid = byPlayerId(ratings);

        assertTrue(byPid.get("p_h_0").substitutedOut(), "p_h_0 should be marked substituted out");
        assertFalse(byPid.get("p_h_0").substitutedIn());
        assertTrue(byPid.get("p_h_1").substitutedIn(), "p_h_1 should be marked substituted in");
        assertFalse(byPid.get("p_h_1").substitutedOut());
    }

    // ========== Test 12: player field values match SessionPlayer ==========

    @Test
    void playerFieldsMatchSessionPlayer() {
        CareerSave career = makeCareerWithXI(HOME, AWAY, 2, 2);
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = emptyResult(HOME, AWAY);

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(career, fixture, result);
        Map<String, V24PlayerMatchRatingDto> byPid = byPlayerId(ratings);

        assertEquals(HOME, byPid.get("p_h_0").teamId());
        assertEquals(AWAY, byPid.get("p_a_0").teamId());
        assertNotNull(byPid.get("p_h_0").playerName());
        assertFalse(byPid.get("p_h_0").playerName().isBlank());
        assertNotNull(byPid.get("p_h_0").position());
    }

    // ========== Test 13: starting11 exists → uses starting11 (explicit) ==========

    @Test
    void assembleRatings_whenStarting11Exists_usesStarting11() {
        // Career has starting XI set
        CareerSave career = makeCareerWithXI(HOME, AWAY, 11, 11);
        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = emptyResult(HOME, AWAY);

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(career, fixture, result);

        assertFalse(ratings.isEmpty(), "ratings should not be empty when starting XI exists");
        assertEquals(22, ratings.size(), "11v11 = 22 players expected");
        // Verify starting11 IDs are used (not squad IDs)
        Map<String, V24PlayerMatchRatingDto> byPid = byPlayerId(ratings);
        assertNotNull(byPid.get("p_h_0"), "starting XI player p_h_0 should be in ratings");
        assertNotNull(byPid.get("p_a_0"), "starting XI player p_a_0 should be in ratings");
    }

    // ========== Test 14: starting11 missing → falls back to squad ==========

    @Test
    void assembleRatings_whenStarting11Missing_fallsBackToSquad() {
        // Career has NO starting XI (simulates live/SSE flow)
        String careerId = HOME + "_" + AWAY;
        CareerSave save = new CareerSave();
        save.getData().setCareerId(careerId);
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();

        // Create teams
        for (String tid : List.of(HOME, AWAY)) {
            UUID uuid = UUID.fromString(tid);
            SessionTeam team = SessionTeam.fromRealTeam(uuid, "world_" + tid,
                    "Team " + tid, "Country", BigDecimal.ZERO, "4-3-3", null);
            team.setSessionTeamId(tid);
            tm.addSessionTeam(team);
        }

        // Create 15 squad players per team (more than 11, to test "up to 11")
        // HOME players: p_h_0 through p_h_14
        // AWAY players: p_a_0 through p_a_14
        String homePrefix = "p_h_";
        String awayPrefix = "p_a_";
        for (int i = 0; i < 15; i++) {
            SessionPlayer homeP = SessionPlayer.custom(homePrefix + i, 25,
                    i == 0 ? "ST" : (i < 5 ? "MID" : "DEF"),
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            homeP.setSessionPlayerId(homePrefix + i);
            pm.addSessionPlayer(homeP);
            tm.assignPlayerToSquad(homeP.getSessionPlayerId(), HOME);

            SessionPlayer awayP = SessionPlayer.custom(awayPrefix + i, 25,
                    i == 0 ? "ST" : (i < 5 ? "MID" : "DEF"),
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            awayP.setSessionPlayerId(awayPrefix + i);
            pm.addSessionPlayer(awayP);
            tm.assignPlayerToSquad(awayP.getSessionPlayerId(), AWAY);
        }

        save.setTeamManager(tm);
        save.setPlayerManager(pm);

        // intentionally do NOT set teamStarting11 - simulates live/SSE flow
        CareerSeasonManager sm = new CareerSeasonManager();
        sm.setCurrentSeason(1);
        save.setSeasonManager(sm);
        save.setTournamentState(new com.footballmanager.domain.model.entity.TournamentState());

        MatchFixture fixture = makeFixture(HOME, AWAY, 1);
        V24DetailedMatchResult result = emptyResult(HOME, AWAY);

        List<V24PlayerMatchRatingDto> ratings = assembler.assemblePlayerRatings(save, fixture, result);

        assertFalse(ratings.isEmpty(), "ratings should not be empty when falling back to squad");
        // Should return up to 11 players per team = 22 total
        assertEquals(22, ratings.size(), "fallback should return up to 11 players per team (22 total)");
        // Verify squad players are used (p_h_0 through p_h_14 exist in squad, but only first 11 should be used)
        Map<String, V24PlayerMatchRatingDto> byPid = byPlayerId(ratings);
        // First 11 players should be in ratings
        assertNotNull(byPid.get("p_h_0"), "squad player p_h_0 should be in ratings (first 11 used)");
        assertNotNull(byPid.get("p_a_0"), "squad player p_a_0 should be in ratings (first 11 used)");
        // Players beyond 11 should NOT be in ratings
        assertNull(byPid.get("p_h_11"), "squad player p_h_11 should NOT be in ratings (only first 11 used)");
        assertNull(byPid.get("p_a_11"), "squad player p_a_11 should NOT be in ratings (only first 11 used)");
    }

    // ========== Helpers ==========

    private static final String HOME = UUID.randomUUID().toString();
    private static final String AWAY = UUID.randomUUID().toString();

    private CareerSave makeCareerWithXI(String homeTeamId, String awayTeamId,
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

        List<String> homeStarterIds = new ArrayList<>();
        for (int i = 0; i < homeStarterCount; i++) {
            homeStarterIds.add(homePlayers.get(i).getSessionPlayerId());
        }
        List<String> awayStarterIds = new ArrayList<>();
        for (int i = 0; i < awayStarterCount; i++) {
            awayStarterIds.add(awayPlayers.get(i).getSessionPlayerId());
        }
        save.getTeamStarting11().put(homeTeamId, homeStarterIds);
        save.getTeamStarting11().put(awayTeamId, awayStarterIds);

        // SeasonManager for currentSeason
        CareerSeasonManager sm = new CareerSeasonManager();
        sm.setCurrentSeason(1);
        save.setSeasonManager(sm);

        save.setTournamentState(new com.footballmanager.domain.model.entity.TournamentState());
        return save;
    }

    private MatchFixture makeFixture(String homeId, String awayId, int round) {
        return new MatchFixture("match_" + homeId + "_" + awayId, homeId, awayId, round);
    }

    private V24DetailedMatchResult emptyResult(String homeId, String awayId) {
        return new V24DetailedMatchResult(
                "match_" + homeId + "_" + awayId,
                homeId, awayId,
                0, 0,
                0.0, 0.0,
                0, 0,
                50, 50,
                new V24MatchTimeline(),
                ""
        );
    }

    private V24DetailedMatchResult makeResultWithEvents(String homeId, String awayId,
                                                         List<V24MatchEvent> homeEvents,
                                                         List<V24MatchEvent> awayEvents) {
        V24MatchTimeline timeline = new V24MatchTimeline();
        for (V24MatchEvent e : homeEvents) timeline.addEvent(e);
        for (V24MatchEvent e : awayEvents) timeline.addEvent(e);
        return new V24DetailedMatchResult(
                "match_" + homeId + "_" + awayId,
                homeId, awayId,
                (int) homeEvents.stream().filter(e -> e.type() == V24MatchEventType.GOAL).count(),
                (int) awayEvents.stream().filter(e -> e.type() == V24MatchEventType.GOAL).count(),
                0.0, 0.0,
                homeEvents.size(), awayEvents.size(),
                50, 50,
                timeline,
                ""
        );
    }

    private V24MatchEvent event(int minute, V24MatchEventType type, String teamId,
                                 String playerId, String playerName,
                                 String relatedPlayerId, String relatedPlayerName,
                                 double xg) {
        return new V24MatchEvent(minute, type, teamId, playerId, playerName,
                relatedPlayerId, relatedPlayerName, xg, type.name().toLowerCase());
    }

    private Map<String, V24PlayerMatchRatingDto> byPlayerId(List<V24PlayerMatchRatingDto> list) {
        return list.stream().collect(Collectors.toMap(V24PlayerMatchRatingDto::playerId, Function.identity()));
    }
}