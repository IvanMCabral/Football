package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class V24MatchContextFactoryTest {

    private final V24MatchContextFactory factory = new V24MatchContextFactory();

    // ========== Happy-path tests ==========

    @Test
    void buildsContextFromValidCareerFixture() {
        CareerSave career = makeCareer("career-1", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        MatchFixture fixture = makeFixture("match-1", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        V24MatchContext ctx = factory.build(career, fixture, homeTeam, awayTeam, 42L);

        assertEquals("match-1", ctx.matchId());
        assertEquals("home-t1", ctx.homeTeamId());
        assertEquals("away-t2", ctx.awayTeamId());
        assertEquals(11, ctx.homeStartingPlayers().size());
        assertEquals(11, ctx.awayStartingPlayers().size());
        assertNotNull(ctx.homeBenchPlayers());
        assertNotNull(ctx.awayBenchPlayers());
        assertEquals("4-3-3", ctx.homeFormation());
        assertEquals("4-4-2", ctx.awayFormation());
    }

    @Test
    void defaultsStylesToBalanced() {
        CareerSave career = makeCareer("career-2", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        MatchFixture fixture = makeFixture("match-2", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        V24MatchContext ctx = factory.build(career, fixture, homeTeam, awayTeam, 0L);

        assertEquals(TeamStyle.BALANCED, ctx.homeStyle());
        assertEquals(TeamStyle.BALANCED, ctx.awayStyle());
    }

    @Test
    void usesProvidedStylesWhenOverloadProvided() {
        CareerSave career = makeCareer("career-3", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        MatchFixture fixture = makeFixture("match-3", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        V24MatchContext ctx = factory.buildWithStyles(
                career, fixture, homeTeam, awayTeam,
                TeamStyle.ATTACKING, TeamStyle.DEFENSIVE, 0L);

        assertEquals(TeamStyle.ATTACKING, ctx.homeStyle());
        assertEquals(TeamStyle.DEFENSIVE, ctx.awayStyle());
    }

    @Test
    void usesFormationFromSessionTeam() {
        CareerSave career = makeCareer("career-4", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        MatchFixture fixture = makeFixture("match-4", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "3-5-2");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-2-3-1");

        V24MatchContext ctx = factory.build(career, fixture, homeTeam, awayTeam, 0L);

        assertEquals("3-5-2", ctx.homeFormation());
        assertEquals("4-2-3-1", ctx.awayFormation());
    }

    // ========== Null-input rejection tests ==========

    @Test
    void rejectsNullCareer() {
        MatchFixture fixture = makeFixture("match-x", "h", "a", 1);
        SessionTeam home = makeTeam("h", "Home", "4-3-3");
        SessionTeam away = makeTeam("a", "Away", "4-3-3");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.build(null, fixture, home, away, 0L));
        assertTrue(ex.getMessage().contains("career"));
    }

    @Test
    void rejectsNullFixture() {
        CareerSave career = makeCareer("c", "h", "a", makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        SessionTeam home = makeTeam("h", "Home", "4-3-3");
        SessionTeam away = makeTeam("a", "Away", "4-3-3");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.build(career, null, home, away, 0L));
        assertTrue(ex.getMessage().contains("fixture"));
    }

    @Test
    void rejectsNullHomeTeam() {
        CareerSave career = makeCareer("c", "h", "a", makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        MatchFixture fixture = makeFixture("match-x", "h", "a", 1);
        SessionTeam away = makeTeam("a", "Away", "4-3-3");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.build(career, fixture, null, away, 0L));
        assertTrue(ex.getMessage().contains("homeTeam"));
    }

    @Test
    void rejectsNullAwayTeam() {
        CareerSave career = makeCareer("c", "h", "a", makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        MatchFixture fixture = makeFixture("match-x", "h", "a", 1);
        SessionTeam home = makeTeam("h", "Home", "4-3-3");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.build(career, fixture, home, null, 0L));
        assertTrue(ex.getMessage().contains("awayTeam"));
    }

    // ========== Starting XI validation tests ==========

    @Test
    void derivesStartingXiFromSquadWhenMissingHome() {
        // V24D6M11: When CareerSave has no starting XI, factory derives from squad.
        // Squad must have ≥11 players for this to succeed.
        CareerSave career = makeCareerWithNoStarting11("career-5", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        MatchFixture fixture = makeFixture("match-5", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        // Should succeed by deriving starting XI from squad's first 11 players
        V24MatchContext ctx = factory.build(career, fixture, homeTeam, awayTeam, 0L);
        assertEquals(11, ctx.homeStartingPlayers().size());
        assertEquals(11, ctx.awayStartingPlayers().size());
    }

    @Test
    void derivesStartingXiFromSquadWhenMissingAway() {
        // V24D6M11: Away starting XI missing — should derive from squad.
        CareerSave career = makeCareerWithNoAwayStarting11("career-6", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        MatchFixture fixture = makeFixture("match-6", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        V24MatchContext ctx = factory.build(career, fixture, homeTeam, awayTeam, 0L);
        assertEquals(11, ctx.homeStartingPlayers().size());
        assertEquals(11, ctx.awayStartingPlayers().size());
    }

    @Test
    void derivesFromSquadWhenStartingXiLessThanEleven() {
        // V24D6M11: Explicit starting XI with <11 players is supplemented from squad.
        CareerSave career = makeCareerWithStartingXi("career-7", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70),
                "home-t1", 10, "away-t2", 11);
        MatchFixture fixture = makeFixture("match-7", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        V24MatchContext ctx = factory.build(career, fixture, homeTeam, awayTeam, 0L);
        assertEquals(11, ctx.homeStartingPlayers().size());
    }

    @Test
    void rejectsStartingXiWithMoreThanEleven() {
        // More than 11 in explicit starting XI is still rejected.
        CareerSave career = makeCareerWithStartingXi("career-8", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70),
                "home-t1", 12, "away-t2", 11);
        MatchFixture fixture = makeFixture("match-8", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.build(career, fixture, homeTeam, awayTeam, 0L));
        assertTrue(ex.getMessage().contains("11") || ex.getMessage().contains("home"));
    }

    @Test
    void rejectsUnknownPlayerIdInStartingXi() {
        // Unknown playerId in explicit starting XI is still rejected.
        CareerSave career = makeCareer("career-9", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        // Inject a bad player ID into home starting XI
        career.getTeamStarting11().get("home-t1").set(0, "unknown-player-id");
        MatchFixture fixture = makeFixture("match-9", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.build(career, fixture, homeTeam, awayTeam, 0L));
        assertTrue(ex.getMessage().contains("not found") || ex.getMessage().contains("unknown"));
    }

    @Test
    void rejectsDuplicateStarterIds() {
        List<SessionPlayer> homePlayers = makePlayers("h", 15, 75);
        // Make the 11th player a duplicate of the 1st player
        String duplicateId = homePlayers.get(0).getSessionPlayerId();
        homePlayers.get(10).setSessionPlayerId(duplicateId);

        CareerSave career = makeCareerWithStartingXi("career-10", "home-t1", "away-t2",
                homePlayers, makePlayers("a", 15, 70),
                "home-t1", 11, "away-t2", 11);
        MatchFixture fixture = makeFixture("match-10", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.build(career, fixture, homeTeam, awayTeam, 0L));
        assertTrue(ex.getMessage().contains("duplicate"));
    }

    // ========== Bench tests ==========

    @Test
    void benchExcludesStartingXi() {
        CareerSave career = makeCareer("career-11", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        MatchFixture fixture = makeFixture("match-11", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        V24MatchContext ctx = factory.build(career, fixture, homeTeam, awayTeam, 0L);

        List<String> starterIds = ctx.homeStartingPlayers().stream()
                .map(SessionPlayer::getSessionPlayerId)
                .toList();
        for (SessionPlayer benchPlayer : ctx.homeBenchPlayers()) {
            assertFalse(starterIds.contains(benchPlayer.getSessionPlayerId()),
                    "Bench player should not be in starting XI");
        }
    }

    // ========== canBuild tests ==========

    @Test
    void canBuildReturnsTrueForValidInput() {
        CareerSave career = makeCareer("career-12", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        MatchFixture fixture = makeFixture("match-12", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        assertTrue(factory.canBuild(career, fixture, homeTeam, awayTeam));
    }

    @Test
    void canBuildReturnsFalseForInvalidInputAndDoesNotThrow() {
        // Completely invalid career — should return false, not throw
        assertFalse(factory.canBuild(null, null, null, null));
    }

    // ========== Immutability tests ==========

    @Test
    void doesNotMutateCareerSave() {
        CareerSave career = makeCareer("career-13", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        Map<String, List<String>> originalStarting11 = deepCopyStarting11(career.getTeamStarting11());
        MatchFixture fixture = makeFixture("match-13", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        factory.build(career, fixture, homeTeam, awayTeam, 0L);

        assertEquals(originalStarting11, career.getTeamStarting11());
    }

    @Test
    void doesNotMutateSessionPlayers() {
        CareerSave career = makeCareer("career-14", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        Map<String, SessionPlayer> originalPlayers = new HashMap<>(career.getSessionPlayers());
        MatchFixture fixture = makeFixture("match-14", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");

        factory.build(career, fixture, homeTeam, awayTeam, 0L);

        assertEquals(originalPlayers, career.getSessionPlayers());
    }

    // ========== Determinism test ==========

    @Test
    void deterministicSameInputProducesSameContext() {
        CareerSave career = makeCareer("career-15", "home-t1", "away-t2",
                makePlayers("h", 15, 75), makePlayers("a", 15, 70));
        MatchFixture fixture = makeFixture("match-15", "home-t1", "away-t2", 1);
        SessionTeam homeTeam = makeTeam("home-t1", "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam("away-t2", "Away FC", "4-4-2");
        long seed = 12345L;

        V24MatchContext ctx1 = factory.build(career, fixture, homeTeam, awayTeam, seed);
        V24MatchContext ctx2 = factory.build(career, fixture, homeTeam, awayTeam, seed);

        assertEquals(ctx1.matchId(), ctx2.matchId());
        assertEquals(ctx1.homeTeamId(), ctx2.homeTeamId());
        assertEquals(ctx1.awayTeamId(), ctx2.awayTeamId());
        assertEquals(ctx1.homeFormation(), ctx2.homeFormation());
        assertEquals(ctx1.awayFormation(), ctx2.awayFormation());
        assertEquals(ctx1.homeStartingPlayers().size(), ctx2.homeStartingPlayers().size());
        assertEquals(ctx1.awayStartingPlayers().size(), ctx2.awayStartingPlayers().size());
    }

    // ========== Factory helpers ==========

    private CareerSave makeCareer(String careerId, String homeTeamId, String awayTeamId,
                                 List<SessionPlayer> homePlayers, List<SessionPlayer> awayPlayers) {
        return makeCareerWithStartingXi(careerId, homeTeamId, awayTeamId,
                homePlayers, awayPlayers, homeTeamId, 11, awayTeamId, 11);
    }

    private CareerSave makeCareerWithStartingXi(String careerId, String homeTeamId, String awayTeamId,
                                                List<SessionPlayer> homePlayers, List<SessionPlayer> awayPlayers,
                                                String homeStartingTeamId, int homeStarterCount,
                                                String awayStartingTeamId, int awayStarterCount) {
        CareerSave career = new CareerSave();
        career.getData().setCareerId(careerId);

        for (SessionPlayer p : homePlayers) career.addSessionPlayer(p);
        for (SessionPlayer p : awayPlayers) career.addSessionPlayer(p);

        // Register teams
        SessionTeam home = SessionTeam.fromRealTeam(UUID.randomUUID(), "world_" + homeTeamId,
                "Home", "Country", BigDecimal.ZERO, "4-3-3", null);
        home.setSessionTeamId(homeTeamId);
        SessionTeam away = SessionTeam.fromRealTeam(UUID.randomUUID(), "world_" + awayTeamId,
                "Away", "Country", BigDecimal.ZERO, "4-4-2", null);
        away.setSessionTeamId(awayTeamId);
        career.addSessionTeam(home);
        career.addSessionTeam(away);

        // Add all players to squads
        for (SessionPlayer p : homePlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), homeTeamId);
        }
        for (SessionPlayer p : awayPlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), awayTeamId);
        }

        // Set starting XI (first `homeStarterCount` / `awayStarterCount` players)
        List<String> homeStarterIds = homePlayers.subList(0, homeStarterCount).stream()
                .map(SessionPlayer::getSessionPlayerId).toList();
        List<String> awayStarterIds = awayPlayers.subList(0, awayStarterCount).stream()
                .map(SessionPlayer::getSessionPlayerId).toList();
        career.getTeamStarting11().put(homeStartingTeamId, new ArrayList<>(homeStarterIds));
        career.getTeamStarting11().put(awayStartingTeamId, new ArrayList<>(awayStarterIds));

        return career;
    }

    private CareerSave makeCareerWithNoStarting11(String careerId, String homeTeamId, String awayTeamId,
                                                   List<SessionPlayer> homePlayers, List<SessionPlayer> awayPlayers) {
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

        // No starting XI for home team — away has 11
        career.getTeamStarting11().put(awayTeamId, new ArrayList<>(
                awayPlayers.subList(0, 11).stream()
                        .map(SessionPlayer::getSessionPlayerId).toList()));

        return career;
    }

    private CareerSave makeCareerWithNoAwayStarting11(String careerId, String homeTeamId, String awayTeamId,
                                                      List<SessionPlayer> homePlayers, List<SessionPlayer> awayPlayers) {
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

        // Home has 11, away has none
        career.getTeamStarting11().put(homeTeamId, new ArrayList<>(
                homePlayers.subList(0, 11).stream()
                        .map(SessionPlayer::getSessionPlayerId).toList()));
        // away starting XI intentionally omitted

        return career;
    }

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

    private MatchFixture makeFixture(String matchId, String homeTeamId, String awayTeamId, int round) {
        return new MatchFixture(matchId, homeTeamId, awayTeamId, round);
    }

    private SessionTeam makeTeam(String id, String name, String formation) {
        SessionTeam team = SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world_" + id, name, "Country",
                BigDecimal.ZERO, formation, null);
        team.setSessionTeamId(id);
        return team;
    }

    private Map<String, List<String>> deepCopyStarting11(Map<String, List<String>> original) {
        Map<String, List<String>> copy = new HashMap<>();
        for (Map.Entry<String, List<String>> e : original.entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copy;
    }
}