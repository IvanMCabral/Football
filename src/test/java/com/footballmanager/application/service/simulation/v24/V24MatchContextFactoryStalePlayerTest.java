package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE-MATCH-F5.2 BUG-003 regression test.
 *
 * <p>Repro: in Fecha 2+ (after a previous round), the orchestrator's
 * per-round mutations (suspensions, injuries, sales) may remove a player
 * from the CareerSave.playerManager. The CareerSave.teamStarting11 still
 * references the removed player. The previous F5.1 code threw
 * {@code IllegalArgumentException("home starting XI player not found: ...")}
 * which bubbled up to GlobalExceptionHandler as HTTP 422
 * LINEUP_VALIDATION_ERROR, blocking Fecha 2+.
 *
 * <p>The F5.2 fix in V24MatchContextFactory: if teamStarting11 has stale
 * player IDs (player not found in CareerSave.playerManager), the factory
 * either:
 * <ul>
 *   <li>Accepts the partial lineup (if enough valid IDs remain), or</li>
 *   <li>Falls back to deriveStartingXIfromSquad (if too many stale IDs).</li>
 * </ul>
 *
 * <p>This test asserts both code paths.
 */
class V24MatchContextFactoryStalePlayerTest {

    private final V24MatchContextFactory factory = new V24MatchContextFactory();

    @Test
    @DisplayName("BUG-003: ANY stale playerId in teamStarting11 triggers full fallback to squad")
    void stalePlayer_triggersFullFallback() {
        // Career with 15 home players. teamStarting11 has 10 valid IDs
        // plus 1 stale ID (playerId of a player that was removed from
        // playerManager). The factory should fall back to the squad
        // completely — even 1 stale reference means the teamStarting11
        // is untrustworthy (other refs could be stale too).
        CareerSave career = new CareerSave();
        career.getData().setCareerId("career-bug-003-partial");
        String homeTeamId = "home-t1";
        String awayTeamId = "away-t2";

        List<SessionPlayer> homePlayers = makePlayers("h", 12, 75);
        List<SessionPlayer> awayPlayers = makePlayers("a", 12, 70);
        for (SessionPlayer p : homePlayers) career.addSessionPlayer(p);
        for (SessionPlayer p : awayPlayers) career.addSessionPlayer(p);

        SessionTeam home = makeTeam(homeTeamId, "Home", "4-3-3");
        SessionTeam away = makeTeam(awayTeamId, "Away", "4-4-2");
        career.addSessionTeam(home);
        career.addSessionTeam(away);

        for (SessionPlayer p : homePlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), homeTeamId);
        }
        for (SessionPlayer p : awayPlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), awayTeamId);
        }

        // Set home starting XI to 10 valid IDs + 1 stale ID ("removed_player_99")
        List<String> homeStarterIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            homeStarterIds.add(homePlayers.get(i).getSessionPlayerId());
        }
        homeStarterIds.add("removed_player_99");  // stale
        career.getTeamStarting11().put(homeTeamId, homeStarterIds);

        // Away XI: 11 valid
        List<String> awayStarterIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            awayStarterIds.add(awayPlayers.get(i).getSessionPlayerId());
        }
        career.getTeamStarting11().put(awayTeamId, awayStarterIds);

        MatchFixture fixture = new MatchFixture("match-bug-003-partial", homeTeamId, awayTeamId, 2);

        // F5.2 fix: should NOT throw IAE. Falls back to squad completely
        // (12 home players, take first 11).
        V24MatchContext ctx = factory.build(career, fixture, home, away, 42L);
        assertNotNull(ctx);
        assertEquals(11, ctx.homeStartingPlayers().size(),
            "BUG-003: expected 11 home starters via squad fallback (1 stale triggered "
            + "full fallback), got " + ctx.homeStartingPlayers().size());
        assertEquals(11, ctx.awayStartingPlayers().size(),
            "BUG-003: away team should have 11 starters unchanged, got "
            + ctx.awayStartingPlayers().size());
    }

    @Test
    @DisplayName("BUG-003: too many stale IDs triggers fallback to deriveStartingXIfromSquad")
    void stalePlayer_allStaleFallsBackToSquad() {
        // Career with 15 home players. teamStarting11 has only stale IDs
        // (all referencing removed players). The factory should fall back
        // to deriveStartingXIfromSquad and use the squad (the first 11
        // players in the squad).
        CareerSave career = new CareerSave();
        career.getData().setCareerId("career-bug-003-all-stale");
        String homeTeamId = "home-t1";
        String awayTeamId = "away-t2";

        List<SessionPlayer> homePlayers = makePlayers("h", 15, 75);
        List<SessionPlayer> awayPlayers = makePlayers("a", 15, 70);
        for (SessionPlayer p : homePlayers) career.addSessionPlayer(p);
        for (SessionPlayer p : awayPlayers) career.addSessionPlayer(p);

        SessionTeam home = makeTeam(homeTeamId, "Home", "4-3-3");
        SessionTeam away = makeTeam(awayTeamId, "Away", "4-4-2");
        career.addSessionTeam(home);
        career.addSessionTeam(away);

        for (SessionPlayer p : homePlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), homeTeamId);
        }
        for (SessionPlayer p : awayPlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), awayTeamId);
        }

        // Set home starting XI to 11 STALE IDs.
        List<String> homeStarterIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            homeStarterIds.add("stale_removed_player_" + i);
        }
        career.getTeamStarting11().put(homeTeamId, homeStarterIds);

        // Away XI: 11 valid
        List<String> awayStarterIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            awayStarterIds.add(awayPlayers.get(i).getSessionPlayerId());
        }
        career.getTeamStarting11().put(awayTeamId, awayStarterIds);

        MatchFixture fixture = new MatchFixture("match-bug-003-all-stale", homeTeamId, awayTeamId, 2);

        // F5.2 fix: should NOT throw IAE. Falls back to squad (15 home
        // players, take first 11).
        V24MatchContext ctx = factory.build(career, fixture, home, away, 42L);
        assertNotNull(ctx);
        assertEquals(11, ctx.homeStartingPlayers().size(),
            "BUG-003: expected 11 home starters via squad fallback, got "
            + ctx.homeStartingPlayers().size());
        // The fallback should use the first 11 players in the squad.
        for (int i = 0; i < 11; i++) {
            assertEquals(homePlayers.get(i).getSessionPlayerId(),
                ctx.homeStartingPlayers().get(i).getSessionPlayerId(),
                "BUG-003: fallback should use squad order, position " + i);
        }
    }

    @Test
    @DisplayName("BUG-003: valid teamStarting11 (no stale IDs) still works (regression check)")
    void noStale_worksAsBefore() {
        // Happy path: all 11 IDs in teamStarting11 are valid. The factory
        // should still resolve them without any warning.
        CareerSave career = new CareerSave();
        career.getData().setCareerId("career-bug-003-happy");
        String homeTeamId = "home-t1";
        String awayTeamId = "away-t2";

        List<SessionPlayer> homePlayers = makePlayers("h", 15, 75);
        List<SessionPlayer> awayPlayers = makePlayers("a", 15, 70);
        for (SessionPlayer p : homePlayers) career.addSessionPlayer(p);
        for (SessionPlayer p : awayPlayers) career.addSessionPlayer(p);

        SessionTeam home = makeTeam(homeTeamId, "Home", "4-3-3");
        SessionTeam away = makeTeam(awayTeamId, "Away", "4-4-2");
        career.addSessionTeam(home);
        career.addSessionTeam(away);

        for (SessionPlayer p : homePlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), homeTeamId);
        }
        for (SessionPlayer p : awayPlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), awayTeamId);
        }

        List<String> homeStarterIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            homeStarterIds.add(homePlayers.get(i).getSessionPlayerId());
        }
        career.getTeamStarting11().put(homeTeamId, homeStarterIds);

        List<String> awayStarterIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            awayStarterIds.add(awayPlayers.get(i).getSessionPlayerId());
        }
        career.getTeamStarting11().put(awayTeamId, awayStarterIds);

        MatchFixture fixture = new MatchFixture("match-bug-003-happy", homeTeamId, awayTeamId, 1);

        V24MatchContext ctx = factory.build(career, fixture, home, away, 42L);
        assertNotNull(ctx);
        assertEquals(11, ctx.homeStartingPlayers().size());
        assertEquals(11, ctx.awayStartingPlayers().size());
    }

    @Test
    @DisplayName("BUG-003: null/blank playerId in teamStarting11 STILL throws IAE (preserves user-data validation)")
    void nullPlayerId_stillThrowsIAE() {
        // Defensive: if the user explicitly put a null/blank ID in their
        // teamStarting11, we should still throw IAE so the user can fix it
        // explicitly. The F5.2 fix only handles STALE references (player
        // was removed from playerManager), not null/blank entries.
        CareerSave career = new CareerSave();
        career.getData().setCareerId("career-bug-003-null");
        String homeTeamId = "home-t1";
        String awayTeamId = "away-t2";

        List<SessionPlayer> homePlayers = makePlayers("h", 12, 75);
        List<SessionPlayer> awayPlayers = makePlayers("a", 12, 70);
        for (SessionPlayer p : homePlayers) career.addSessionPlayer(p);
        for (SessionPlayer p : awayPlayers) career.addSessionPlayer(p);

        SessionTeam home = makeTeam(homeTeamId, "Home", "4-3-3");
        SessionTeam away = makeTeam(awayTeamId, "Away", "4-4-2");
        career.addSessionTeam(home);
        career.addSessionTeam(away);

        for (SessionPlayer p : homePlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), homeTeamId);
        }
        for (SessionPlayer p : awayPlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), awayTeamId);
        }

        List<String> homeStarterIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            homeStarterIds.add(homePlayers.get(i).getSessionPlayerId());
        }
        homeStarterIds.add(null);  // explicit null
        career.getTeamStarting11().put(homeTeamId, homeStarterIds);

        List<String> awayStarterIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            awayStarterIds.add(awayPlayers.get(i).getSessionPlayerId());
        }
        career.getTeamStarting11().put(awayTeamId, awayStarterIds);

        MatchFixture fixture = new MatchFixture("match-bug-003-null", homeTeamId, awayTeamId, 2);

        try {
            factory.build(career, fixture, home, away, 42L);
            assertTrue(false, "Expected IllegalArgumentException for null playerId");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null") || e.getMessage().contains("blank"),
                "Expected IAE about null/blank playerId, got: " + e.getMessage());
        }
    }

    // ========== Helpers ==========

    private List<SessionPlayer> makePlayers(String prefix, int count, int ovr) {
        List<SessionPlayer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SessionPlayer p = SessionPlayer.custom(
                    prefix + "_p" + i, 25, "MID",
                    ovr, ovr, ovr, ovr, ovr, ovr,
                    BigDecimal.valueOf(ovr * 1000));
            list.add(p);
        }
        return list;
    }

    private SessionTeam makeTeam(String id, String name, String formation) {
        SessionTeam team = SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world_" + id, name, "Country",
                BigDecimal.ZERO, formation, null);
        team.setSessionTeamId(id);
        return team;
    }
}
