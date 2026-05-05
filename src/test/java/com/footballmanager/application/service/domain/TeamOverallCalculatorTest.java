package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.entity.CareerSave;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10B: TeamOverallCalculator unit tests.
 * Validates OVR calculation logic without touching MatchEngineImpl.
 */
class TeamOverallCalculatorTest {

    // ========== Helper factories ==========

    private SessionPlayer makePlayer(String id, int overall) {
        SessionPlayer p = SessionPlayer.custom(
                "Player_" + id,
                25,
                "MID",
                overall, overall, overall, overall, overall, overall,
                BigDecimal.valueOf(overall * 100000)
        );
        p.setSessionPlayerId(id);
        return p;
    }

    private static CareerPlayerManager makePlayerManager(List<SessionPlayer> players) {
        CareerPlayerManager pm = new CareerPlayerManager();
        for (SessionPlayer p : players) {
            pm.addSessionPlayer(p);
        }
        return pm;
    }

    private static CareerTeamManager makeTeamManager(String teamId, List<String> playerIds) {
        CareerTeamManager tm = new CareerTeamManager();
        // Only set squad — no SessionTeam needed for OVR calculation
        tm.setSquad(teamId, new java.util.ArrayList<>(playerIds));
        return tm;
    }

    private static CareerSave makeCareerSave(
            CareerTeamManager tm,
            CareerPlayerManager pm,
            java.util.Map<String, List<String>> starting11) {
        CareerSave save = new CareerSave();
        save.setTeamManager(tm);
        save.setPlayerManager(pm);
        if (starting11 != null) {
            save.setTeamStarting11(new java.util.HashMap<>(starting11));
        }
        return save;
    }

    // ========== Test 1: calculateFromPlayerIds returns average ==========

    @Test
    void calculateFromPlayerIds_returnsAverageOfSessionPlayerOverall() {
        List<SessionPlayer> players = List.of(
                makePlayer("p1", 70),
                makePlayer("p2", 80),
                makePlayer("p3", 90)
        );
        CareerPlayerManager pm = makePlayerManager(players);
        Function<String, SessionPlayer> provider = pm::getSessionPlayer;

        List<String> ids = List.of("p1", "p2", "p3");
        int ovr = TeamOverallCalculator.calculateFromPlayerIds(ids, provider);

        // (70 + 80 + 90) / 3 = 80
        assertEquals(80, ovr);
    }

    // ========== Test 2: calculateFromPlayerIds ignores missing ==========

    @Test
    void calculateFromPlayerIds_ignoresMissingPlayers() {
        List<SessionPlayer> players = List.of(
                makePlayer("p1", 70),
                makePlayer("p3", 90)  // p2 missing
        );
        CareerPlayerManager pm = makePlayerManager(players);
        Function<String, SessionPlayer> provider = pm::getSessionPlayer;

        List<String> ids = List.of("p1", "p2", "p3");
        int ovr = TeamOverallCalculator.calculateFromPlayerIds(ids, provider);

        // (70 + 90) / 2 = 80
        assertEquals(80, ovr);
    }

    // ========== Test 3: calculateFromPlayerIds empty returns 50 ==========

    @Test
    void calculateFromPlayerIds_emptyReturns50() {
        CareerPlayerManager pm = makePlayerManager(List.of());
        Function<String, SessionPlayer> provider = pm::getSessionPlayer;

        int ovr = TeamOverallCalculator.calculateFromPlayerIds(List.of(), provider);

        assertEquals(50, ovr);
    }

    // ========== Test 4: calculateFromPlayerIds all missing returns 50 ==========

    @Test
    void calculateFromPlayerIds_allMissingReturns50() {
        CareerPlayerManager pm = makePlayerManager(List.of());
        Function<String, SessionPlayer> provider = pm::getSessionPlayer;

        List<String> ids = List.of("missing1", "missing2");
        int ovr = TeamOverallCalculator.calculateFromPlayerIds(ids, provider);

        assertEquals(50, ovr);
    }

    // ========== Test 5: calculateFromSessionTeam delegates correctly ==========

    @Test
    void calculateFromSessionTeam_delegatesToCareerTeamManagerEquivalent() {
        String teamId = "team1";
        List<SessionPlayer> players = List.of(
                makePlayer("p1", 70),
                makePlayer("p2", 80),
                makePlayer("p3", 90)
        );
        List<String> playerIds = List.of("p1", "p2", "p3");

        CareerTeamManager tm = makeTeamManager(teamId, playerIds);
        CareerPlayerManager pm = makePlayerManager(players);

        int ovr = TeamOverallCalculator.calculateFromSessionTeam(teamId, tm, pm);

        // (70 + 80 + 90) / 3 = 80
        assertEquals(80, ovr);
    }

    // ========== Test 6: calculateFromStartingXI uses starting XI ==========

    @Test
    void calculateFromStartingXI_usesStartingXIWhenPresent() {
        String teamId = "team1";
        List<SessionPlayer> allPlayers = List.of(
                makePlayer("p1", 60),  // bench
                makePlayer("p2", 75),  // bench
                makePlayer("p3", 85),  // starting
                makePlayer("p4", 95)   // starting
        );
        List<String> squadIds = List.of("p1", "p2", "p3", "p4");
        // Starting XI: p3, p4 only
        java.util.Map<String, List<String>> starting11 = new java.util.HashMap<>();
        starting11.put(teamId, List.of("p3", "p4"));

        CareerTeamManager tm = makeTeamManager(teamId, squadIds);
        CareerPlayerManager pm = makePlayerManager(allPlayers);
        CareerSave career = makeCareerSave(tm, pm, starting11);

        int ovr = TeamOverallCalculator.calculateFromStartingXI(teamId, career);

        // (85 + 95) / 2 = 90
        assertEquals(90, ovr);
    }

    // ========== Test 7: calculateFromStartingXI falls back to squad ==========

    @Test
    void calculateFromStartingXI_fallsBackToSquadWhenNoStartingXI() {
        String teamId = "team1";
        List<SessionPlayer> players = List.of(
                makePlayer("p1", 70),
                makePlayer("p2", 80)
        );
        List<String> playerIds = List.of("p1", "p2");

        CareerTeamManager tm = makeTeamManager(teamId, playerIds);
        CareerPlayerManager pm = makePlayerManager(players);
        // No starting XI map set
        CareerSave career = makeCareerSave(tm, pm, null);

        int ovr = TeamOverallCalculator.calculateFromStartingXI(teamId, career);

        // Falls back to squad: (70 + 80) / 2 = 75
        assertEquals(75, ovr);
    }

    // ========== Test 8: calculateFallback matches MatchEngineImpl formula ==========

    @Test
    void calculateFallbackFromSquadSize_matchesMatchEngineFormula() {
        // 0 players -> 70
        assertEquals(70, TeamOverallCalculator.calculateFallbackFromSquadSize(0));
        // 1 player -> 70 + min(20, 1/2=0) = 70
        assertEquals(70, TeamOverallCalculator.calculateFallbackFromSquadSize(1));
        // 10 players -> 70 + min(20, 10/2=5) = 75
        assertEquals(75, TeamOverallCalculator.calculateFallbackFromSquadSize(10));
        // 20 players -> 70 + min(20, 20/2=10) = 80
        assertEquals(80, TeamOverallCalculator.calculateFallbackFromSquadSize(20));
        // 40 players -> 70 + min(20, 40/2=20) = 90
        assertEquals(90, TeamOverallCalculator.calculateFallbackFromSquadSize(40));
        // 100 players -> 70 + min(20, 100/2=50) = 90 (capped)
        assertEquals(90, TeamOverallCalculator.calculateFallbackFromSquadSize(100));
    }

    // ========== Test 9: clamp low and high values ==========

    @Test
    void clampsLowAndHighValuesIfNeeded() {
        // Very low average should clamp to 1
        List<SessionPlayer> lowPlayers = List.of(
                makePlayer("lp1", 1),
                makePlayer("lp2", 1)
        );
        CareerPlayerManager pm = makePlayerManager(lowPlayers);
        int lowOvr = TeamOverallCalculator.calculateFromPlayerIds(
                List.of("lp1", "lp2"), pm::getSessionPlayer);
        assertEquals(1, lowOvr);

        // Very high average should clamp to 100
        List<SessionPlayer> highPlayers = List.of(
                makePlayer("hp1", 100),
                makePlayer("hp2", 100)
        );
        CareerPlayerManager pm2 = makePlayerManager(highPlayers);
        int highOvr = TeamOverallCalculator.calculateFromPlayerIds(
                List.of("hp1", "hp2"), pm2::getSessionPlayer);
        assertEquals(100, highOvr);
    }

    // ========== Test 10: deterministic for same inputs ==========

    @Test
    void deterministicForSameInputs() {
        List<SessionPlayer> players = List.of(
                makePlayer("p1", 72),
                makePlayer("p2", 78),
                makePlayer("p3", 85)
        );
        CareerPlayerManager pm = makePlayerManager(players);
        Function<String, SessionPlayer> provider = pm::getSessionPlayer;
        List<String> ids = List.of("p1", "p2", "p3");

        int ovr1 = TeamOverallCalculator.calculateFromPlayerIds(ids, provider);
        int ovr2 = TeamOverallCalculator.calculateFromPlayerIds(ids, provider);
        int ovr3 = TeamOverallCalculator.calculateFromPlayerIds(ids, provider);

        assertEquals(ovr1, ovr2);
        assertEquals(ovr2, ovr3);
    }
}
