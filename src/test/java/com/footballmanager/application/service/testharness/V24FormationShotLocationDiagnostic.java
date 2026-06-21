package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V24D23-A diagnostic — prints observed xG/shots per formation across
 * multiple seeds to gather evidence before deciding on re-tuning.
 *
 * <p>Run with: {@code mvn test -Dtest=V24FormationShotLocationDiagnostic}.
 *
 * <p>Output is intentionally human-readable (not assertions) so the
 * operator can read the per-seed numbers and decide whether the
 * formation modifier pipeline is strong enough to clear the 0.05
 * threshold in the B1 test, or whether Fase 4 re-tuning is needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("V24D23-A diagnostic — per-formation xG/shots across seeds (evidence for B3 escalation)")
class V24FormationShotLocationDiagnostic {

    private static final UUID USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String MATCH_ID = "match-formation-shotloc-diag-001";

    @Mock private CareerRepository careerRepository;
    @Mock private CareerSessionService careerSessionService;
    @Mock private V24DetailedMatchStoragePort v24StoragePort;

    private V24MatchContextFactory v24ContextFactory;
    private TestHarnessUseCaseImpl useCase;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        v24ContextFactory = new V24MatchContextFactory();
        useCase = new TestHarnessUseCaseImpl(
            careerRepository, careerSessionService,
            v24ContextFactory, v24StoragePort);
    }

    @Test
    @DisplayName("diagnostic — per-seed xG/shots for 4 formations × 6 seeds")
    void diagnosticPrintPerSeedMetrics() {
        String[] formations = { "4-3-3", "4-4-2", "3-5-2", "4-2-3-1" };
        long[] seeds = { 1L, 7L, 19L, 42L, 73L, 137L };

        System.out.println();
        System.out.println("=== V24D23-A DIAGNOSTIC — per-formation xG/shots by seed ===");
        System.out.printf("%-6s %-8s %-9s %-9s %-7s %-7s%n",
            "seed", "formation", "homeXg", "awayXg", "homeSh", "awaySh");
        System.out.println("-------------------------------------------------------");

        for (long seed : seeds) {
            for (String f : formations) {
                V24DetailedMatchData d = replayWithFormation(f, seed);
                System.out.printf("%-6d %-8s %-9.3f %-9.3f %-7d %-7d%n",
                    seed, f, d.homeXg(), d.awayXg(), d.homeShots(), d.awayShots());
            }
        }

        // Also print the homeXg delta between consecutive formations at each seed.
        System.out.println();
        System.out.println("=== homeXg deltas (compared against 4-3-3 baseline at same seed) ===");
        System.out.printf("%-6s %-9s %-9s %-9s%n",
            "seed", "4-4-2 vs 4-3-3", "3-5-2 vs 4-3-3", "4-2-3-1 vs 4-3-3");
        System.out.println("---------------------------------------------");

        for (long seed : seeds) {
            V24DetailedMatchData d433 = replayWithFormation("4-3-3", seed);
            V24DetailedMatchData d442 = replayWithFormation("4-4-2", seed);
            V24DetailedMatchData d352 = replayWithFormation("3-5-2", seed);
            V24DetailedMatchData d4231 = replayWithFormation("4-2-3-1", seed);
            System.out.printf("%-6d %-9.4f %-9.4f %-9.4f%n",
                seed,
                Math.abs(d433.homeXg() - d442.homeXg()),
                Math.abs(d433.homeXg() - d352.homeXg()),
                Math.abs(d433.homeXg() - d4231.homeXg()));
        }
        System.out.println();
    }

    private V24DetailedMatchData replayWithFormation(String formation, long seed) {
        CareerSave freshCareer = careerWithFreshSquad(formation);
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(java.util.Optional.of(freshCareer)));
        when(careerRepository.save(any(CareerSave.class))).thenReturn(Mono.empty());

        useCase.setFormation(USER_ID, formation).block();
        org.mockito.Mockito.clearInvocations(v24StoragePort);
        useCase.replayMatch(USER_ID, MATCH_ID, seed).block();

        ArgumentCaptor<V24DetailedMatchData> detailCaptor =
            ArgumentCaptor.forClass(V24DetailedMatchData.class);
        verify(v24StoragePort, times(1)).save(anyString(), detailCaptor.capture());

        return detailCaptor.getValue();
    }

    private CareerSave careerWithFreshSquad(String userFormation) {
        CareerSave c = new CareerSave();
        c.setUserId(USER_ID);
        c.setUserSessionTeamId("user-team-id");
        c.setCurrentSeason(1);

        List<SessionPlayer> userPlayers = new ArrayList<>();
        userPlayers.add(playerWithPosition("u-p0", "GK", 30, 30, 50, 50, 50, 50));
        for (int i = 1; i <= 4; i++) {
            userPlayers.add(playerWithPosition("u-p" + i, "DEF", 50, 70, 60, 70, 60, 60));
        }
        for (int i = 5; i <= 7; i++) {
            userPlayers.add(playerWithPosition("u-p" + i, "MID", 70, 60, 80, 70, 70, 70));
        }
        for (int i = 8; i <= 9; i++) {
            userPlayers.add(playerWithPosition("u-p" + i, "WINGER", 80, 40, 75, 85, 70, 70));
        }
        userPlayers.add(playerWithPosition("u-p10", "ATT", 90, 30, 70, 80, 65, 65));

        List<SessionPlayer> rivalPlayers = new ArrayList<>();
        rivalPlayers.add(playerWithPosition("r-p0", "GK", 30, 30, 50, 50, 50, 50));
        for (int i = 1; i <= 4; i++) {
            rivalPlayers.add(playerWithPosition("r-p" + i, "DEF", 50, 70, 60, 70, 60, 60));
        }
        for (int i = 5; i <= 8; i++) {
            rivalPlayers.add(playerWithPosition("r-p" + i, "MID", 70, 60, 80, 70, 70, 70));
        }
        for (int i = 9; i <= 10; i++) {
            rivalPlayers.add(playerWithPosition("r-p" + i, "ATT", 80, 30, 70, 75, 65, 65));
        }

        wireSquad(c, "user-team-id", userPlayers, userFormation);
        wireSquad(c, "rival-1", rivalPlayers, "4-4-2");

        MatchFixture completed = new MatchFixture(MATCH_ID, "user-team-id", "rival-1", 1);
        completed.complete(new MatchFixture.MatchResultData(0, 0, 0, 0, 0, 0));
        c.getTournamentState().setFixtures(List.of(completed));

        return c;
    }

    private SessionPlayer playerWithPosition(String id, String position,
                                             int attack, int defense, int technique,
                                             int speed, int stamina, int mentality) {
        SessionPlayer p = new SessionPlayer();
        p.setSessionPlayerId(id);
        p.setName("Player " + id);
        p.setAge(25);
        p.setPosition(position);
        p.setAttack(attack);
        p.setDefense(defense);
        p.setTechnique(technique);
        p.setSpeed(speed);
        p.setStamina(stamina);
        p.setMentality(mentality);
        p.setMarketValue(BigDecimal.valueOf(70000L));
        p.setInjured(false);
        p.setInjuryType(null);
        p.setInjuryRemainingMatches(0);
        p.setYellowCards(0);
        p.setRedCards(0);
        p.setSuspended(false);
        p.setSuspensionRemainingMatches(0);
        return p;
    }

    @SuppressWarnings("unchecked")
    private void wireSquad(CareerSave career, String teamId, List<SessionPlayer> players,
                           String formation) {
        try {
            Field tmField = CareerSave.class.getDeclaredField("teamManager");
            tmField.setAccessible(true);
            Object teamManager = tmField.get(career);

            Field pmField = CareerSave.class.getDeclaredField("playerManager");
            pmField.setAccessible(true);
            Object playerManager = pmField.get(career);

            SessionTeam team = new SessionTeam();
            team.setSessionTeamId(teamId);
            team.setName(teamId);
            team.setFormation(formation);
            java.lang.reflect.Method addSessionTeam =
                teamManager.getClass().getMethod("addSessionTeam", SessionTeam.class);
            addSessionTeam.invoke(teamManager, team);

            java.lang.reflect.Method addSessionPlayer =
                playerManager.getClass().getMethod("addSessionPlayer", SessionPlayer.class);
            java.lang.reflect.Method assign =
                teamManager.getClass().getMethod(
                    "assignPlayerToSquad", String.class, String.class);

            for (SessionPlayer p : players) {
                addSessionPlayer.invoke(playerManager, p);
                assign.invoke(teamManager, p.getSessionPlayerId(), teamId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to wire squad via reflection", e);
        }
    }
}