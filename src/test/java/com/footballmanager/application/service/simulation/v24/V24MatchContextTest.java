package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE-MATCH-F2-LIVE F2 (B1): unit tests for {@link V24MatchContext}.
 *
 * <p>Focus: the {@link V24MatchContext#withManualSubstitution} helper
 * introduced in F2. Validates the swap behavior (off → bench, on →
 * starting), the defensive immutability contract (returns a NEW context,
 * original lists are NOT mutated), and the validation rules
 * (teamId must match home/away, off must be in starting, on must be on
 * bench, minute must be in [0, 90], off != on).
 */
class V24MatchContextTest {

    @Test
    @DisplayName("F2: withManualSubstitution swaps off→bench and on→starting and returns NEW context")
    void withManualSubstitution_marksOffAndOn_andReturnsNewContext() {
        // Arrange: 11 starters (home-starter-0..10) + 5 bench (home-bench-0..4).
        V24MatchContext original = buildContext(
            "home", "away",
            /* starter count */ 11,
            /* bench count */ 5,
            /* baseOvr */ 70);

        // Act: sub home-starter-0 (GK) → home-bench-0 (GK) at minute 30.
        V24MatchContext next = original.withManualSubstitution(
            "home", "home-starter-0", "home-bench-0", 30);

        // Assert A: returned context is NOT the same instance (immutability).
        assertNotNull(next);
        assertNotEquals(original, next, "withManualSubstitution must return a new V24MatchContext");

        // Assert B: off player is now in bench (and bench now has the off player).
        List<SessionPlayer> nextHomeBench = next.homeBenchPlayers();
        assertTrue(containsId(nextHomeBench, "home-starter-0"),
            "off player (home-starter-0) must be in the new bench list");
        assertEquals(5, nextHomeBench.size(),
            "bench list size must be preserved (5)");
        assertTrue(containsId(nextHomeBench, "home-bench-0") == false,
            "on player (home-bench-0) must NOT be in the new bench list anymore");

        // Assert C: on player is now in starting (and starting now has the on player).
        List<SessionPlayer> nextHomeStarting = next.homeStartingPlayers();
        assertTrue(containsId(nextHomeStarting, "home-bench-0"),
            "on player (home-bench-0) must be in the new starting list");
        assertEquals(11, nextHomeStarting.size(),
            "starting list size must be preserved (11)");
        assertTrue(containsId(nextHomeStarting, "home-starter-0") == false,
            "off player (home-starter-0) must NOT be in the new starting list anymore");

        // Assert D: away team is untouched (only home was swapped).
        assertEquals(original.awayStartingPlayers(), next.awayStartingPlayers(),
            "away starting list must be untouched");
        assertEquals(original.awayBenchPlayers(), next.awayBenchPlayers(),
            "away bench list must be untouched");
        assertEquals(original.awayTeamId(), next.awayTeamId());
        assertEquals(original.awayFormation(), next.awayFormation());
        assertEquals(original.awayStyle(), next.awayStyle());

        // Assert E: ORIGINAL context is NOT mutated (defensive copy contract).
        assertTrue(containsId(original.homeStartingPlayers(), "home-starter-0"),
            "original home starting list must still contain the off player (immutability)");
        assertTrue(containsId(original.homeBenchPlayers(), "home-bench-0"),
            "original home bench list must still contain the on player (immutability)");
    }

    @Test
    @DisplayName("F2: withManualSubstitution preserves starting order (off removed, on appended at end)")
    void withManualSubstitution_preservesOrder() {
        V24MatchContext original = buildContext("home", "away", 11, 5, 70);

        // Sub home-starter-5 (MID) → home-bench-2 (MID) at minute 45.
        V24MatchContext next = original.withManualSubstitution(
            "home", "home-starter-5", "home-bench-2", 45);

        // Original order: [s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10].
        // After swap: [s0, s1, s2, s3, s4, s6, s7, s8, s9, s10, bench-2].
        List<SessionPlayer> nextHomeStarting = next.homeStartingPlayers();
        assertEquals(11, nextHomeStarting.size());
        // The first 5 starters are unchanged.
        assertEquals("home-starter-0", nextHomeStarting.get(0).getSessionPlayerId());
        assertEquals("home-starter-1", nextHomeStarting.get(1).getSessionPlayerId());
        assertEquals("home-starter-2", nextHomeStarting.get(2).getSessionPlayerId());
        assertEquals("home-starter-3", nextHomeStarting.get(3).getSessionPlayerId());
        assertEquals("home-starter-4", nextHomeStarting.get(4).getSessionPlayerId());
        // starter-5 is removed; starters 6-10 shift to indices 5-9.
        assertEquals("home-starter-6", nextHomeStarting.get(5).getSessionPlayerId());
        assertEquals("home-starter-7", nextHomeStarting.get(6).getSessionPlayerId());
        assertEquals("home-starter-8", nextHomeStarting.get(7).getSessionPlayerId());
        assertEquals("home-starter-9", nextHomeStarting.get(8).getSessionPlayerId());
        assertEquals("home-starter-10", nextHomeStarting.get(9).getSessionPlayerId());
        // on player appended at the end.
        assertEquals("home-bench-2", nextHomeStarting.get(10).getSessionPlayerId());
    }

    @Test
    @DisplayName("F2: withManualSubstitution works for away team (teamId=awayTeamId)")
    void withManualSubstitution_worksForAwayTeam() {
        V24MatchContext original = buildContext("home", "away", 11, 5, 70);
        V24MatchContext next = original.withManualSubstitution(
            "away", "away-starter-3", "away-bench-1", 60);

        // Away starting now has away-bench-1, not away-starter-3.
        assertTrue(containsId(next.awayStartingPlayers(), "away-bench-1"));
        assertTrue(!containsId(next.awayStartingPlayers(), "away-starter-3"));
        // Away bench now has away-starter-3, not away-bench-1.
        assertTrue(containsId(next.awayBenchPlayers(), "away-starter-3"));
        assertTrue(!containsId(next.awayBenchPlayers(), "away-bench-1"));
        // Home is untouched.
        assertEquals(original.homeStartingPlayers(), next.homeStartingPlayers());
        assertEquals(original.homeBenchPlayers(), next.homeBenchPlayers());
    }

    @Test
    @DisplayName("F2: withManualSubstitution throws when teamId does not match home/away")
    void withManualSubstitution_invalidTeamId_throws() {
        V24MatchContext ctx = buildContext("home", "away", 11, 5, 70);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution("unknown-team", "home-starter-0", "home-bench-0", 30));
        assertTrue(ex.getMessage().contains("unknown-team"),
            "Exception should mention the bad teamId: " + ex.getMessage());
    }

    @Test
    @DisplayName("F2: withManualSubstitution throws when playerOffId is not in starting XI")
    void withManualSubstitution_offNotInStarting_throws() {
        V24MatchContext ctx = buildContext("home", "away", 11, 5, 70);

        // "home-bench-0" is on the bench, not in starting.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution("home", "home-bench-0", "home-bench-1", 30));
        assertTrue(ex.getMessage().toLowerCase().contains("starting")
                || ex.getMessage().toLowerCase().contains("home-bench-0"),
            "Exception should mention 'starting' or the bad playerId: " + ex.getMessage());
    }

    @Test
    @DisplayName("F2: withManualSubstitution throws when playerOnId is not on bench")
    void withManualSubstitution_onNotOnBench_throws() {
        V24MatchContext ctx = buildContext("home", "away", 11, 5, 70);

        // "home-starter-0" is in starting, not on bench.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution("home", "home-starter-0", "home-starter-1", 30));
        assertTrue(ex.getMessage().toLowerCase().contains("bench")
                || ex.getMessage().toLowerCase().contains("home-starter-1"),
            "Exception should mention 'bench' or the bad playerId: " + ex.getMessage());
    }

    @Test
    @DisplayName("F2: withManualSubstitution throws when playerOffId == playerOnId")
    void withManualSubstitution_offEqualsOn_throws() {
        V24MatchContext ctx = buildContext("home", "away", 11, 5, 70);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution("home", "home-starter-0", "home-starter-0", 30));
        assertTrue(ex.getMessage().toLowerCase().contains("different")
                || ex.getMessage().toLowerCase().contains("home-starter-0"),
            "Exception should mention the duplicate id: " + ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -10, 91, 100, 200})
    @DisplayName("F2: withManualSubstitution throws when minute is out of [0, 90]")
    void withManualSubstitution_invalidMinute_throws(int badMinute) {
        V24MatchContext ctx = buildContext("home", "away", 11, 5, 70);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution("home", "home-starter-0", "home-bench-0", badMinute));
        assertTrue(ex.getMessage().toLowerCase().contains("minute"),
            "Exception should mention 'minute': " + ex.getMessage());
    }

    @Test
    @DisplayName("F2: withManualSubstitution throws on null or blank teamId / playerOffId / playerOnId")
    void withManualSubstitution_blankArgs_throws() {
        V24MatchContext ctx = buildContext("home", "away", 11, 5, 70);

        assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution(null, "home-starter-0", "home-bench-0", 30));
        assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution("  ", "home-starter-0", "home-bench-0", 30));
        assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution("home", null, "home-bench-0", 30));
        assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution("home", "  ", "home-bench-0", 30));
        assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution("home", "home-starter-0", null, 30));
        assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution("home", "home-starter-0", "  ", 30));
    }

    // ========== Fixture helpers ==========

    private V24MatchContext buildContext(String homeTeamId,
                                         String awayTeamId,
                                         int starterCount,
                                         int benchCount,
                                         int baseOvr) {
        SessionTeam homeTeam = SessionTeam.custom(homeTeamId, "Home FC", "ARG",
            BigDecimal.valueOf(1_000_000L), "4-3-3");
        SessionTeam awayTeam = SessionTeam.custom(awayTeamId, "Away FC", "BRA",
            BigDecimal.valueOf(1_000_000L), "4-4-2");
        return new V24MatchContext(
            "match-f2",
            homeTeamId,
            awayTeamId,
            homeTeam, awayTeam,
            makePlayers(homeTeamId, "starter", starterCount, baseOvr),
            makePlayers(awayTeamId, "starter", starterCount, baseOvr),
            makePlayers(homeTeamId, "bench", benchCount, baseOvr),
            makePlayers(awayTeamId, "bench", benchCount, baseOvr),
            "4-3-3", "4-4-2",
            TeamStyle.BALANCED, TeamStyle.BALANCED
        );
    }

    private List<SessionPlayer> makePlayers(String teamId, String suffix, int count, int ovr) {
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String position = (i == 0) ? "GK"
                : (i <= 4) ? "DEF"
                : (i <= 7) ? "MID"
                : (i <= 9) ? "WINGER" : "ATT";
            String id = teamId + "-" + suffix + "-" + i;
            SessionPlayer sp = SessionPlayer.custom(id, 25, position,
                ovr, ovr, ovr, ovr, ovr, ovr,
                BigDecimal.valueOf(70_000L));
            sp.setSessionPlayerId(id);
            sp.setEnergy(100);
            players.add(sp);
        }
        return players;
    }

    private boolean containsId(List<SessionPlayer> players, String sessionPlayerId) {
        if (players == null) return false;
        for (SessionPlayer p : players) {
            if (p != null && sessionPlayerId.equals(p.getSessionPlayerId())) {
                return true;
            }
        }
        return false;
    }
}
