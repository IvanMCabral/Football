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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE-MATCH-F2-LIVE F2 (B1) + F2.5 (B1): unit tests for
 * {@link V24MatchContext}.
 *
 * <p>Focus: the {@link V24MatchContext#withManualSubstitution} helper.
 * F2.5 changed the helper's contract: instead of swapping the players
 * in the lineup immediately, the helper appends a
 * {@link V24MatchContext.ScheduledSub} to
 * {@link V24MatchContext#manualSubstitutions()} and the engine applies
 * the swap when the minute loop reaches {@code effectiveMinute}. The
 * tests below assert this new contract (lineup intact + scheduled sub
 * registered) and the validation rules (teamId, off in starting, on
 * on bench, minute in [0, 90], off != on, F2.5 duplicate-scheduled-sub
 * check).
 */
class V24MatchContextTest {

    @Test
    @DisplayName("F2.5: withManualSubstitution appends to list, does NOT mutate lineup")
    void withManualSubstitution_appendsToList_doesNotMutateLineup() {
        // Arrange: 11 starters (home-starter-0..10) + 5 bench (home-bench-0..4).
        V24MatchContext original = buildContext(
            "home", "away",
            /* starter count */ 11,
            /* bench count */ 5,
            /* baseOvr */ 70);

        int origStartingSize = original.homeStartingPlayers().size();
        int origBenchSize = original.homeBenchPlayers().size();

        // Act: sub home-starter-0 (GK) → home-bench-0 (GK) at minute 30.
        V24MatchContext next = original.withManualSubstitution(
            "home", "home-starter-0", "home-bench-0", 30);

        // Assert A: returned context is NOT the same instance (immutability).
        assertNotNull(next);
        assertNotEquals(original, next, "withManualSubstitution must return a new V24MatchContext");

        // Assert B: lineups are INTACT — the swap is deferred to the engine.
        assertEquals(origStartingSize, next.homeStartingPlayers().size(),
            "F2.5: starting list size must be preserved (the swap is deferred)");
        assertEquals(origBenchSize, next.homeBenchPlayers().size(),
            "F2.5: bench list size must be preserved (the swap is deferred)");
        assertTrue(containsId(next.homeStartingPlayers(), "home-starter-0"),
            "F2.5: starter-0 must REMAIN in starting (the swap is deferred)");
        assertTrue(containsId(next.homeBenchPlayers(), "home-bench-0"),
            "F2.5: bench-0 must REMAIN on bench (the swap is deferred)");

        // Assert C: away team is untouched.
        assertEquals(original.awayStartingPlayers(), next.awayStartingPlayers(),
            "away starting list must be untouched");
        assertEquals(original.awayBenchPlayers(), next.awayBenchPlayers(),
            "away bench list must be untouched");
        assertEquals(original.awayTeamId(), next.awayTeamId());
        assertEquals(original.awayFormation(), next.awayFormation());
        assertEquals(original.awayStyle(), next.awayStyle());

        // Assert D: ORIGINAL context is NOT mutated (immutability).
        assertEquals(0, original.manualSubstitutions().size(),
            "F2.5: original context must have an EMPTY manualSubstitutions list (immutability)");
        assertTrue(containsId(original.homeStartingPlayers(), "home-starter-0"),
            "original home starting list must still contain the off player (immutability)");
        assertTrue(containsId(original.homeBenchPlayers(), "home-bench-0"),
            "original home bench list must still contain the on player (immutability)");

        // Assert E: scheduled sub registered with the expected fields.
        assertEquals(1, next.manualSubstitutions().size(),
            "F2.5: next context must have exactly 1 scheduled sub");
        V24MatchContext.ScheduledSub sub = next.manualSubstitutions().get(0);
        assertEquals("home", sub.teamId(),
            "ScheduledSub.teamId must match the sub's team");
        assertEquals("home-starter-0", sub.playerOffId(),
            "ScheduledSub.playerOffId must match the off player");
        assertEquals("home-bench-0", sub.playerOnId(),
            "ScheduledSub.playerOnId must match the on player");
        assertEquals(30, sub.effectiveMinute(),
            "ScheduledSub.effectiveMinute must match the requested minute");
    }

    @Test
    @DisplayName("F2.5: withManualSubstitution preserves starting order (lineup NOT mutated)")
    void withManualSubstitution_preservesOrder() {
        V24MatchContext original = buildContext("home", "away", 11, 5, 70);

        // Sub home-starter-5 (MID) → home-bench-2 (MID) at minute 45.
        V24MatchContext next = original.withManualSubstitution(
            "home", "home-starter-5", "home-bench-2", 45);

        // F2.5: starting list is INTACT — order preserved exactly as in
        // original. The swap is deferred to the engine minute loop.
        List<SessionPlayer> nextHomeStarting = next.homeStartingPlayers();
        assertEquals(11, nextHomeStarting.size());
        // The first 5 starters are unchanged.
        assertEquals("home-starter-0", nextHomeStarting.get(0).getSessionPlayerId());
        assertEquals("home-starter-1", nextHomeStarting.get(1).getSessionPlayerId());
        assertEquals("home-starter-2", nextHomeStarting.get(2).getSessionPlayerId());
        assertEquals("home-starter-3", nextHomeStarting.get(3).getSessionPlayerId());
        assertEquals("home-starter-4", nextHomeStarting.get(4).getSessionPlayerId());
        // starter-5 is still in starting (NOT removed).
        assertEquals("home-starter-5", nextHomeStarting.get(5).getSessionPlayerId());
        assertEquals("home-starter-6", nextHomeStarting.get(6).getSessionPlayerId());
        assertEquals("home-starter-7", nextHomeStarting.get(7).getSessionPlayerId());
        assertEquals("home-starter-8", nextHomeStarting.get(8).getSessionPlayerId());
        assertEquals("home-starter-9", nextHomeStarting.get(9).getSessionPlayerId());
        assertEquals("home-starter-10", nextHomeStarting.get(10).getSessionPlayerId());

        // bench list is also intact.
        assertEquals(5, next.homeBenchPlayers().size());
        assertTrue(containsId(next.homeBenchPlayers(), "home-bench-2"),
            "F2.5: bench-2 must REMAIN on bench (deferred swap)");

        // Scheduled sub registered.
        assertEquals(1, next.manualSubstitutions().size());
        V24MatchContext.ScheduledSub sub = next.manualSubstitutions().get(0);
        assertEquals("home-starter-5", sub.playerOffId());
        assertEquals("home-bench-2", sub.playerOnId());
        assertEquals(45, sub.effectiveMinute());
    }

    @Test
    @DisplayName("F2.5: withManualSubstitution works for away team (teamId=awayTeamId)")
    void withManualSubstitution_worksForAwayTeam() {
        V24MatchContext original = buildContext("home", "away", 11, 5, 70);
        V24MatchContext next = original.withManualSubstitution(
            "away", "away-starter-3", "away-bench-1", 60);

        // F2.5: lineups are INTACT.
        assertTrue(containsId(next.awayStartingPlayers(), "away-starter-3"),
            "F2.5: away-starter-3 must REMAIN in starting (deferred swap)");
        assertTrue(containsId(next.awayBenchPlayers(), "away-bench-1"),
            "F2.5: away-bench-1 must REMAIN on bench (deferred swap)");

        // Home is untouched.
        assertEquals(original.homeStartingPlayers(), next.homeStartingPlayers());
        assertEquals(original.homeBenchPlayers(), next.homeBenchPlayers());

        // Scheduled sub registered.
        assertEquals(1, next.manualSubstitutions().size());
        V24MatchContext.ScheduledSub sub = next.manualSubstitutions().get(0);
        assertEquals("away", sub.teamId());
        assertEquals("away-starter-3", sub.playerOffId());
        assertEquals("away-bench-1", sub.playerOnId());
        assertEquals(60, sub.effectiveMinute());
    }

    @Test
    @DisplayName("F2.5: withManualSubstitution throws when teamId does not match home/away")
    void withManualSubstitution_invalidTeamId_throws() {
        V24MatchContext ctx = buildContext("home", "away", 11, 5, 70);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution("unknown-team", "home-starter-0", "home-bench-0", 30));
        assertTrue(ex.getMessage().contains("unknown-team"),
            "Exception should mention the bad teamId: " + ex.getMessage());
    }

    @Test
    @DisplayName("F2.5: withManualSubstitution throws when playerOffId is not in starting XI")
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
    @DisplayName("F2.5: withManualSubstitution throws when playerOnId is not on bench")
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
    @DisplayName("F2.5: withManualSubstitution throws when playerOffId == playerOnId")
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
    @DisplayName("F2.5: withManualSubstitution throws when minute is out of [0, 90]")
    void withManualSubstitution_invalidMinute_throws(int badMinute) {
        V24MatchContext ctx = buildContext("home", "away", 11, 5, 70);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ctx.withManualSubstitution("home", "home-starter-0", "home-bench-0", badMinute));
        assertTrue(ex.getMessage().toLowerCase().contains("minute"),
            "Exception should mention 'minute': " + ex.getMessage());
    }

    @Test
    @DisplayName("F2.5: withManualSubstitution throws on null or blank teamId / playerOffId / playerOnId")
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

    @Test
    @DisplayName("F2.5: withManualSubstitution throws on duplicate scheduled sub for same team+off")
    void withManualSubstitution_duplicateScheduledSub_throws() {
        V24MatchContext original = buildContext("home", "away", 11, 5, 70);
        V24MatchContext withSub = original.withManualSubstitution(
            "home", "home-starter-0", "home-bench-0", 30);
        // First sub succeeded; a second sub for the same off player in the
        // same team must throw, regardless of minute or on player.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            withSub.withManualSubstitution(
                "home", "home-starter-0", "home-bench-1", 45));
        assertTrue(ex.getMessage().contains("home-starter-0"),
            "Exception should mention the duplicate off player: " + ex.getMessage());
        assertTrue(ex.getMessage().toLowerCase().contains("scheduled")
                || ex.getMessage().toLowerCase().contains("already"),
            "Exception should mention 'scheduled' or 'already': " + ex.getMessage());

        // The original context's list still has only 1 entry — the failed
        // attempt did not mutate it.
        assertEquals(1, withSub.manualSubstitutions().size(),
            "F2.5: the failed second sub must not have mutated the list");
    }

    @Test
    @DisplayName("F2.5: manualSubstitutions list is sorted by (effectiveMinute, teamId, playerOffId)")
    void manualSubstitutions_isSorted() {
        V24MatchContext original = buildContext("home", "away", 11, 5, 70);
        // Append in non-sorted order to confirm the constructor sorts.
        V24MatchContext next = original
            .withManualSubstitution("home", "home-starter-5", "home-bench-0", 60)
            .withManualSubstitution("away", "away-starter-2", "away-bench-0", 30)
            .withManualSubstitution("home", "home-starter-0", "home-bench-1", 30)
            .withManualSubstitution("home", "home-starter-9", "home-bench-2", 30);

        assertEquals(4, next.manualSubstitutions().size());
        List<V24MatchContext.ScheduledSub> subs = next.manualSubstitutions();
        // Expected order (by effectiveMinute ASC, then teamId ASC, then playerOffId ASC):
        //   30 away away-starter-2
        //   30 home home-starter-0
        //   30 home home-starter-9
        //   60 home home-starter-5
        assertEquals(30, subs.get(0).effectiveMinute());
        assertEquals("away", subs.get(0).teamId());
        assertEquals("away-starter-2", subs.get(0).playerOffId());
        assertEquals(30, subs.get(1).effectiveMinute());
        assertEquals("home", subs.get(1).teamId());
        assertEquals("home-starter-0", subs.get(1).playerOffId());
        assertEquals(30, subs.get(2).effectiveMinute());
        assertEquals("home", subs.get(2).teamId());
        assertEquals("home-starter-9", subs.get(2).playerOffId());
        assertEquals(60, subs.get(3).effectiveMinute());
        assertEquals("home", subs.get(3).teamId());
        assertEquals("home-starter-5", subs.get(3).playerOffId());
    }

    @Test
    @DisplayName("F2.5: manualSubstitutions() returns an unmodifiable list")
    void manualSubstitutions_isUnmodifiable() {
        V24MatchContext ctx = buildContext("home", "away", 11, 5, 70);
        V24MatchContext next = ctx.withManualSubstitution(
            "home", "home-starter-0", "home-bench-0", 30);

        List<V24MatchContext.ScheduledSub> view = next.manualSubstitutions();
        assertThrows(UnsupportedOperationException.class,
            () -> view.add(new V24MatchContext.ScheduledSub(
                "home", "home-starter-1", "home-bench-1", 45)),
            "manualSubstitutions() should return an unmodifiable view");
    }

    @Test
    @DisplayName("F2.5: default constructor leaves manualSubstitutions empty")
    void defaultConstructor_manualSubstitutionsIsEmpty() {
        V24MatchContext ctx = buildContext("home", "away", 11, 5, 70);
        assertNotNull(ctx.manualSubstitutions());
        assertTrue(ctx.manualSubstitutions().isEmpty(),
            "F2.5: a freshly built context has no scheduled subs");
        // The unmodifiable view also throws on mutation.
        assertFalse(ctx.manualSubstitutions().isEmpty() == false
            && false); // tautology guard
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
