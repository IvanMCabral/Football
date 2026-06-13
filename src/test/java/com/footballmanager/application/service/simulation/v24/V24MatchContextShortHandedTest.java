package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V24D6U2: Engine-level tests for short-handed lineups.
 *
 * <p>Validates that V24MatchContext, V24MatchContextFactory, and
 * V24TeamMatchState accept lineups in {@code [MIN_AVAILABLE_PLAYERS, 11]}
 * and reject lineups outside that range.
 */
class V24MatchContextShortHandedTest {

    private static final int MIN = com.footballmanager.application.service.lineup.LineupRules.MIN_AVAILABLE_PLAYERS;
    private static final int MAX = 11;

    private final V24MatchContextFactory factory = new V24MatchContextFactory();

    // ========== T1: V24MatchContext accepts 7 players ==========

    @Test
    void v24MatchContext_accepts7Players() {
        V24MatchContext ctx = makeContext(7, 11);
        assertEquals(7, ctx.homeStartingPlayers().size());
        assertEquals(11, ctx.awayStartingPlayers().size());
    }

    // ========== T2: V24MatchContext rejects 6 players ==========

    @Test
    void v24MatchContext_rejects6Players() {
        List<SessionPlayer> homeStart = makePlayers("home", 6, 75);
        List<SessionPlayer> awayStart = makePlayers("away", 11, 75);
        SessionTeam homeTeam = makeTeam("home", "Home FC");
        SessionTeam awayTeam = makeTeam("away", "Away FC");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new V24MatchContext(
                        "m-1", "home", "away", homeTeam, awayTeam,
                        homeStart, awayStart,
                        List.of(), List.of(),
                        "4-3-3", "4-3-3",
                        TeamStyle.BALANCED, TeamStyle.BALANCED
                ));
        assertTrue(ex.getMessage().contains("7") || ex.getMessage().contains("home"),
                "Message should mention 7 or home: " + ex.getMessage());
    }

    // ========== T3: V24MatchContext rejects 12 players ==========

    @Test
    void v24MatchContext_rejects12Players() {
        List<SessionPlayer> homeStart = makePlayers("home", 12, 75);
        List<SessionPlayer> awayStart = makePlayers("away", 11, 75);
        SessionTeam homeTeam = makeTeam("home", "Home FC");
        SessionTeam awayTeam = makeTeam("away", "Away FC");

        assertThrows(IllegalArgumentException.class, () ->
                new V24MatchContext(
                        "m-1", "home", "away", homeTeam, awayTeam,
                        homeStart, awayStart,
                        List.of(), List.of(),
                        "4-3-3", "4-3-3",
                        TeamStyle.BALANCED, TeamStyle.BALANCED
                ));
    }

    // ========== T4: V24MatchContextFactory uses short-handed starting11 ==========

    @ParameterizedTest
    @ValueSource(ints = {7, 8, 9, 10})
    void v24MatchContextFactory_usesShortHandedStarting11(int homeCount) {
        CareerSave career = makeCareerWithStartingXiCount("c-" + homeCount, "home", "away",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70),
                homeCount, 11);
        MatchFixture fixture = new MatchFixture("m-" + homeCount, "home", "away", 1);
        SessionTeam homeTeam = makeTeam("home", "Home FC");
        SessionTeam awayTeam = makeTeam("away", "Away FC");

        V24MatchContext ctx = factory.build(career, fixture, homeTeam, awayTeam, 0L);
        assertEquals(homeCount, ctx.homeStartingPlayers().size(),
                "Factory should pass through short-handed starting XI size " + homeCount);
        assertEquals(11, ctx.awayStartingPlayers().size());
    }

    // ========== T5: V24DetailedMatchEngine simulates short-handed without crash ==========

    @Test
    void v24Engine_shortHanded_doesNotCrash() {
        // Build a 7v11 context. Run simulate to ensure no
        // IndexOutOfBounds / div-by-0 / NPE.
        List<SessionPlayer> homeStart = makePlayers("home", 7, 75);
        List<SessionPlayer> awayStart = makePlayers("away", 11, 75);
        SessionTeam homeTeam = makeTeam("home", "Home FC");
        SessionTeam awayTeam = makeTeam("away", "Away FC");
        V24MatchContext ctx = new V24MatchContext(
                "m-1", "home", "away", homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                "4-3-3", "4-3-3",
                TeamStyle.BALANCED, TeamStyle.BALANCED
        );
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult result = engine.simulate(ctx, 42L);
        assertNotNull(result);
        assertNotNull(result.timeline());
    }

    // ========== T6: V24TeamMatchState accepts short-handed starting list ==========

    @Test
    void v24TeamMatchState_acceptsShortHandedStarting() {
        SessionTeam homeTeam = makeTeam("home", "Home FC");
        List<SessionPlayer> homeStart = makePlayers("home", 7, 75);
        List<SessionPlayer> homeBench = makePlayers("home-bench", 5, 75);
        V24TeamMatchState state = V24TeamMatchState.create(
                homeTeam, homeStart, homeBench, TeamStyle.BALANCED);
        assertEquals(7, state.startingPlayers().size());
    }

    @Test
    void v24TeamMatchState_rejects6Starting() {
        SessionTeam homeTeam = makeTeam("home", "Home FC");
        List<SessionPlayer> homeStart = makePlayers("home", 6, 75);
        List<SessionPlayer> homeBench = makePlayers("home-bench", 5, 75);
        assertThrows(IllegalArgumentException.class, () ->
                V24TeamMatchState.create(homeTeam, homeStart, homeBench, TeamStyle.BALANCED));
    }

    @Test
    void v24TeamMatchState_rejects12Starting() {
        SessionTeam homeTeam = makeTeam("home", "Home FC");
        List<SessionPlayer> homeStart = makePlayers("home", 12, 75);
        List<SessionPlayer> homeBench = makePlayers("home-bench", 0, 75);
        assertThrows(IllegalArgumentException.class, () ->
                V24TeamMatchState.create(homeTeam, homeStart, homeBench, TeamStyle.BALANCED));
    }

    // ========== Helpers ==========

    private V24MatchContext makeContext(int homeSize, int awaySize) {
        List<SessionPlayer> homeStart = makePlayers("home", homeSize, 75);
        List<SessionPlayer> awayStart = makePlayers("away", awaySize, 75);
        SessionTeam homeTeam = makeTeam("home", "Home FC");
        SessionTeam awayTeam = makeTeam("away", "Away FC");
        return new V24MatchContext(
                "m-" + homeSize + "v" + awaySize,
                "home", "away", homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                "4-3-3", "4-3-3",
                TeamStyle.BALANCED, TeamStyle.BALANCED
        );
    }

    private CareerSave makeCareerWithStartingXiCount(String careerId, String homeTeamId, String awayTeamId,
                                                     List<SessionPlayer> homePlayers, List<SessionPlayer> awayPlayers,
                                                     int homeStarterCount, int awayStarterCount) {
        CareerSave career = new CareerSave();
        career.getData().setCareerId(careerId);
        for (SessionPlayer p : homePlayers) career.addSessionPlayer(p);
        for (SessionPlayer p : awayPlayers) career.addSessionPlayer(p);

        SessionTeam home = SessionTeam.fromRealTeam(UUID.randomUUID(), "world_" + homeTeamId,
                "Home", "Country", BigDecimal.ZERO, "4-3-3", null);
        home.setSessionTeamId(homeTeamId);
        SessionTeam away = SessionTeam.fromRealTeam(UUID.randomUUID(), "world_" + awayTeamId,
                "Away", "Country", BigDecimal.ZERO, "4-4-2", null);
        away.setSessionTeamId(awayTeamId);
        career.addSessionTeam(home);
        career.addSessionTeam(away);

        for (SessionPlayer p : homePlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), homeTeamId);
        }
        for (SessionPlayer p : awayPlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), awayTeamId);
        }

        List<String> homeStarterIds = homePlayers.subList(0, homeStarterCount).stream()
                .map(SessionPlayer::getSessionPlayerId).toList();
        List<String> awayStarterIds = awayPlayers.subList(0, awayStarterCount).stream()
                .map(SessionPlayer::getSessionPlayerId).toList();
        career.getTeamStarting11().put(homeTeamId, new ArrayList<>(homeStarterIds));
        career.getTeamStarting11().put(awayTeamId, new ArrayList<>(awayStarterIds));

        return career;
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
        SessionTeam t = SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world_" + id, name, "Country",
                BigDecimal.ZERO, "4-3-3", null);
        t.setSessionTeamId(id);
        return t;
    }
}
