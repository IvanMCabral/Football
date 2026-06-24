package com.footballmanager.application.service.simulation.v24;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V24D14-LIVE-FIX-1.7: verifies the formation read path in
 * {@link V24MatchContextFactory#build} prefers the persisted formation
 * stored in {@link CareerSave#getTeamStarting11Formation()} over the
 * legacy {@code SessionTeam.getFormation()} fallback. Without this fix
 * the V24 engine ran every match with the default team formation
 * (typically 4-3-3), ignoring the formation the manager selected via
 * {@code /lineup/manual-select}.
 */
class V24MatchContextFactoryFormationTest {

    private static final String HOME_ID = "home-team-formation";
    private static final String AWAY_ID = "away-team-formation";

    private final V24MatchContextFactory factory = new V24MatchContextFactory();

    // ========== Test 1: persisted formation wins over SessionTeam ==========

    @Test
    void persistedFormationOverridesSessionTeamFormation() {
        CareerSave career = makeCareerWithFormationMap(
                "career-form-1", HOME_ID, AWAY_ID,
                Map.of(HOME_ID, "4-4-2", AWAY_ID, "3-5-2"));
        MatchFixture fixture = new MatchFixture("match-form-1", HOME_ID, AWAY_ID, 1);
        // SessionTeam.getFormation() returns DIFFERENT values from what was persisted.
        SessionTeam homeTeam = makeTeam(HOME_ID, "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam(AWAY_ID, "Away FC", "4-2-3-1");

        V24MatchContext ctx = factory.build(career, fixture, homeTeam, awayTeam, 42L);

        assertEquals("4-4-2", ctx.homeFormation(),
                "homeFormation must come from career.teamStarting11Formation (4-4-2), not SessionTeam (4-3-3)");
        assertEquals("3-5-2", ctx.awayFormation(),
                "awayFormation must come from career.teamStarting11Formation (3-5-2), not SessionTeam (4-2-3-1)");
    }

    // ========== Test 2: fallback to SessionTeam when persisted map absent/empty ==========

    @Test
    void fallsBackToSessionTeamFormationWhenPersistedMapIsEmpty() {
        // Save from sprint 1.5 or earlier — teamStarting11Formation map is empty.
        CareerSave career = makeCareerWithFormationMap(
                "career-form-2", HOME_ID, AWAY_ID, Map.of());
        MatchFixture fixture = new MatchFixture("match-form-2", HOME_ID, AWAY_ID, 1);
        SessionTeam homeTeam = makeTeam(HOME_ID, "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam(AWAY_ID, "Away FC", "4-4-2");

        V24MatchContext ctx = factory.build(career, fixture, homeTeam, awayTeam, 42L);

        assertEquals("4-3-3", ctx.homeFormation(),
                "homeFormation must fall back to SessionTeam.getFormation() (4-3-3) when no persisted formation exists");
        assertEquals("4-4-2", ctx.awayFormation(),
                "awayFormation must fall back to SessionTeam.getFormation() (4-4-2) when no persisted formation exists");
    }

    @Test
    void fallsBackToSessionTeamFormationWhenPersistedMapMissingForTeam() {
        // Career persisted only the HOME formation (e.g. user selected lineup only for own team).
        // AWAY should fall back to SessionTeam.
        CareerSave career = makeCareerWithFormationMap(
                "career-form-3", HOME_ID, AWAY_ID, Map.of(HOME_ID, "5-3-2"));
        MatchFixture fixture = new MatchFixture("match-form-3", HOME_ID, AWAY_ID, 1);
        SessionTeam homeTeam = makeTeam(HOME_ID, "Home FC", "4-3-3");
        SessionTeam awayTeam = makeTeam(AWAY_ID, "Away FC", "4-4-2");

        V24MatchContext ctx = factory.build(career, fixture, homeTeam, awayTeam, 42L);

        assertEquals("5-3-2", ctx.homeFormation(),
                "homeFormation must come from persisted map (5-3-2)");
        assertEquals("4-4-2", ctx.awayFormation(),
                "awayFormation must fall back to SessionTeam (4-4-2) when not in persisted map");
    }

    // ========== Fixture helpers (kept local to avoid coupling with the larger test class) ==========

    private CareerSave makeCareerWithFormationMap(String careerId, String homeTeamId, String awayTeamId,
                                                  Map<String, String> formationMap) {
        CareerSave career = new CareerSave();
        career.getData().setCareerId(careerId);

        List<SessionPlayer> homePlayers = makePlayers("h", 15, 75);
        List<SessionPlayer> awayPlayers = makePlayers("a", 15, 70);
        for (SessionPlayer p : homePlayers) career.addSessionPlayer(p);
        for (SessionPlayer p : awayPlayers) career.addSessionPlayer(p);

        SessionTeam home = SessionTeam.fromRealTeam(UUID.randomUUID(),
                "world_" + homeTeamId, "Home", "Country", BigDecimal.ZERO, "4-3-3", null);
        home.setSessionTeamId(homeTeamId);
        SessionTeam away = SessionTeam.fromRealTeam(UUID.randomUUID(),
                "world_" + awayTeamId, "Away", "Country", BigDecimal.ZERO, "4-4-2", null);
        away.setSessionTeamId(awayTeamId);
        career.addSessionTeam(home);
        career.addSessionTeam(away);

        for (SessionPlayer p : homePlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), homeTeamId);
        }
        for (SessionPlayer p : awayPlayers) {
            career.getTeamManager().assignPlayerToSquad(p.getSessionPlayerId(), awayTeamId);
        }

        // Persisted starting XI (11 each)
        career.getTeamStarting11().put(homeTeamId,
                homePlayers.subList(0, 11).stream().map(SessionPlayer::getSessionPlayerId).toList());
        career.getTeamStarting11().put(awayTeamId,
                awayPlayers.subList(0, 11).stream().map(SessionPlayer::getSessionPlayerId).toList());

        // The formation map under test (may be empty for fallback cases).
        Map<String, String> persistedFormations = new HashMap<>(formationMap);
        career.setTeamStarting11Formation(persistedFormations);

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

    private SessionTeam makeTeam(String id, String name, String formation) {
        SessionTeam team = SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world_" + id, name, "Country",
                BigDecimal.ZERO, formation, null);
        team.setSessionTeamId(id);
        return team;
    }
}