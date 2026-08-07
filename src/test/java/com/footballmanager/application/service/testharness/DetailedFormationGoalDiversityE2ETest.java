package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.simulation.detailed.BaselineState;
import com.footballmanager.application.service.simulation.detailed.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchData;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 *
 * but the resulting Î”xG (~0.05-0.13 per team per match) sat 1Ïƒ below the per-match
 * confirmed: 5 formations Ã— same squad â†’ byte-for-byte identical scores (5-1,
 * 2-5, 2-1, 3-0) â€” formation was a no-op for goals.
 *
 * {@code ShotXgCalculator.calculateXg(quality, formation)}. The modifier
 * is multiplicative on top of the existing shot-location / shooter / assist /
 * defense / gk / style chain (4-3-3 = 1.10, 4-2-3-1 = 1.12, 3-5-2 = 0.88,
 * 5-3-2 = 0.78, 3-4-3 = 1.07, 4-4-2 = 1.03 baseline).
 *
 * <p><b>This test verifies the acceptance criterion</b>: with the same squad
 * and 5 different formations applied, at least 2 of 4 seeded matches produce
 * distinct scores. The probe runs the same setFormation â†’ replayMatch flow
 * scores should now vary.
 *
 * <p><b>Why E2E (not unit):</b> the unit test
 * {@code ShotXgCalculatorFormationModifierTest} proves the modifier is
 * applied at the per-shot level, but the acceptance criterion is whether
 * the integrated engine pipeline produces distinct integer goal counts.
 * means a per-shot xG delta of ~30% must propagate to integer-score
 * variation across multiple shots (~15 per team per match). E2E is the
 * only level that catches this propagation end-to-end.
 *
 * <p><b>Squad reuse:</b> Real Madrid-style squad (1 GK + 4 DEF + 3 MID +
 * 2 WINGER + 1 ATT, 1 super-striker ATT=90) â€” same as
 * {@code DetailedFormationShotLocationE2ETest.careerWithFreshSquad} for
 *
 * <p><b>Profile gating:</b> same as the reference tests â€” Mockito-only,
 * no Spring context, no HTTP/auth/profile overhead.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("V25D25 FormationGoalDiversity â€” formation drives distinct goal counts (sprint V25D25 acceptance)")
class DetailedFormationGoalDiversityE2ETest {

    private static final UUID USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String MATCH_ID = "match-formation-goal-diversity-001";

    /** The 5 formations the acceptance probe exercises (per analisis-v25d25.md). */
    private static final String[] FORMATIONS = {
        "4-4-2",    // baseline (modifier=1.03)
        "4-3-3",    // attacking wide (modifier=1.10)
        "3-5-2",    // defensive back-three (modifier=0.88)
        "4-2-3-1",  // attacking single striker supported (modifier=1.12)
        "5-3-2"     // ultra-defensive back-five (modifier=0.78)
    };

    private static final long[] SEEDS = { 1L, 7L, 19L, 73L };

    @Mock private CareerRepository careerRepository;
    @Mock private CareerSessionService careerSessionService;
    @Mock private DetailedMatchStoragePort detailedMatchStoragePort;
    @Mock private BaselineStateStoragePort baselineStoragePort;
    // returns false from hasEngine, which is fine for the replay tests below
    // (none of them exercise the reset-round path).
    @Mock private com.footballmanager.application.engine.match.MatchEngineRegistry matchEngineRegistry;

    private MatchContextFactory matchContextFactory;
    private TestHarnessUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        matchContextFactory = new MatchContextFactory();
        useCase = new TestHarnessUseCaseImpl(
        careerRepository, careerSessionService,
        matchContextFactory, detailedMatchStoragePort, baselineStoragePort, matchEngineRegistry);
        lenient().when(careerSessionService.saveCareer(any(CareerSave.class))).thenReturn(Mono.empty());
        lenient().when(baselineStoragePort.save(anyString(), any(BaselineState.class)))
            .thenReturn(Mono.empty());
    }

    // ========== Test 1 â€” formation-diversity acceptance criterion ==========

    /**
     *
     * <p>For each seed, the 5 formations should produce a non-trivial
     * spread of integer goal counts (because the formation modifier shifts
     * per-shot xG by ~30% in the worst-vs-best formation pair, and that
     * shift propagates through 15+ Bernoulli shots into integer goal
     * counts). The acceptance criterion: at least 2 of 4 seeds show â‰¥2
     * distinct score tuples across the 5 formations.
     *
     * <p>The score-range check (max - min of homeGoals+awayGoals across
     * formations per seed) gives a magnitude floor: a Bernoulli-only
     * drift would still produce some range, but with the formation
     * modifier in place we expect a range â‰¥ 1 in at least 2 of 4 seeds.
     */
    @Test
    @DisplayName("5 formations Ã— 4 seeds on same squad: at least 2 of 4 seeds produce distinct scores")
    void fiveFormationsAcrossFourSeeds_produceDistinctScores() {
        // Capture (homeGoals, awayGoals) for each (formationIdx, seedIdx).
        int[][] homeGoals = new int[FORMATIONS.length][SEEDS.length];
        int[][] awayGoals = new int[FORMATIONS.length][SEEDS.length];

        for (int fi = 0; fi < FORMATIONS.length; fi++) {
            for (int si = 0; si < SEEDS.length; si++) {
                DetailedMatchData result =
                    replayWithFormation(FORMATIONS[fi], SEEDS[si]);
                homeGoals[fi][si] = result.homeGoals();
                awayGoals[fi][si] = result.awayGoals();
            }
        }

        // Print the grid for diagnostic purposes â€” useful when the test fails.
        printScoreGrid(homeGoals, awayGoals);

        // Count seeds whose 5-formation run shows distinct score tuples.
        int seedsWithDistinctScores = 0;
        int seedsWithRangeAtLeastOne = 0;
        for (int si = 0; si < SEEDS.length; si++) {
            Set<String> distinctScores = new HashSet<>();
            int minTotal = Integer.MAX_VALUE;
            int maxTotal = Integer.MIN_VALUE;
            for (int fi = 0; fi < FORMATIONS.length; fi++) {
                String tuple = homeGoals[fi][si] + "-" + awayGoals[fi][si];
                distinctScores.add(tuple);
                int total = homeGoals[fi][si] + awayGoals[fi][si];
                if (total < minTotal) minTotal = total;
                if (total > maxTotal) maxTotal = total;
            }
            int range = maxTotal - minTotal;
            if (distinctScores.size() >= 2) seedsWithDistinctScores++;
            if (range >= 1) seedsWithRangeAtLeastOne++;
        }

        // Determinism regression: for any formation, the 4 seeds MUST produce
        // distinct score patterns. If they don't, the seed is being ignored
        // (RNG broken) â€” that's a hard correctness regression.
        int formationsWithDistinctSeeds = 0;
        for (int fi = 0; fi < FORMATIONS.length; fi++) {
            Set<String> distinctScores = new HashSet<>();
            for (int si = 0; si < SEEDS.length; si++) {
                distinctScores.add(homeGoals[fi][si] + "-" + awayGoals[fi][si]);
            }
            if (distinctScores.size() >= 2) formationsWithDistinctSeeds++;
        }

        assertThat(formationsWithDistinctSeeds)
            .as("Regression guard: for each formation, the 4 seeds MUST produce "
                + "distinct score patterns (otherwise the seed is ignored and "
                + "the Bernoulli goal sampler is broken). "
                + "Observed: %d/5 formations had >=2 distinct scores.",
                formationsWithDistinctSeeds)
            .isEqualTo(5);

        assertThat(seedsWithDistinctScores)
            .as("Acceptance: at least 2 of 4 seeds must show distinct score tuples "
                + "across the 5 formations. Observed: %d/4 seeds with >=2 distinct "
                + "scores; %d/4 seeds with score range >= 1.",
                seedsWithDistinctScores, seedsWithRangeAtLeastOne)
            .isGreaterThanOrEqualTo(2);

        assertThat(seedsWithRangeAtLeastOne)
            .as("Acceptance: at least 2 of 4 seeds must show score-range "
                + "(max - min of total goals) >= 1 across the 5 formations.",
                seedsWithRangeAtLeastOne)
            .isGreaterThanOrEqualTo(2);
    }

    // ========== Test 2 â€” same formation + same seed = identical result (regression) ==========

    /**
     * Determinism regression guard: same formation + same seed must produce
     * byte-identical detailed match detail on consecutive replays. This is the
     * {@code DetailedFormationShotLocationE2ETest}) carried forward into
     * numbers or mutate shared state.
     */
    @Test
    @DisplayName("setFormation + replay(seed=N) twice produces identical result (determinism regression)")
    void setFormation_thenReplayWithSameSeed_secondTimeProducesIdenticalResult() {
        DetailedMatchData resultA = replayWithFormation("4-3-3", 42L);

        // Wipe injuries/yellows so the second replay sees a clean squad.
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(java.util.Optional.of(careerWithFreshSquad("4-3-3"))));
        lenient().when(careerSessionService.saveCareer(any(CareerSave.class))).thenReturn(Mono.empty());
        useCase.resetInjuries(USER_ID).block();

        DetailedMatchData resultB = replayWithFormation("4-3-3", 42L);

        assertThat(resultB.homeGoals()).isEqualTo(resultA.homeGoals());
        assertThat(resultB.awayGoals()).isEqualTo(resultA.awayGoals());
        assertThat(resultB.homeXg()).isEqualTo(resultA.homeXg());
        assertThat(resultB.awayXg()).isEqualTo(resultA.awayXg());
        assertThat(resultB.homeShots()).isEqualTo(resultA.homeShots());
        assertThat(resultB.awayShots()).isEqualTo(resultA.awayShots());
    }

    // ========== helpers (mirror DetailedFormationShotLocationE2ETest) ==========

    private DetailedMatchData replayWithFormation(String formation, long seed) {
        CareerSave freshCareer = careerWithFreshSquad(formation);
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(java.util.Optional.of(freshCareer)));
        lenient().when(careerSessionService.saveCareer(any(CareerSave.class))).thenReturn(Mono.empty());

        useCase.setFormation(USER_ID, formation).block();
        org.mockito.Mockito.clearInvocations(detailedMatchStoragePort);
        useCase.replayMatch(USER_ID, MATCH_ID, seed).block();

        ArgumentCaptor<DetailedMatchData> detailCaptor =
            ArgumentCaptor.forClass(DetailedMatchData.class);
        verify(detailedMatchStoragePort, times(1)).saveWithContext(
            any(com.footballmanager.domain.model.valueobject.CareerWriteContext.class),
            detailCaptor.capture());

        return detailCaptor.getValue();
    }

    private CareerSave careerWithFreshSquad(String userFormation) {
        CareerSave c = new CareerSave();
        c.setLifecycleGeneration("test-generation");
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

    private void wireSquad(CareerSave career, String teamId, List<SessionPlayer> players,
                           String formation) {
        SessionTeam team = new SessionTeam();
        team.setSessionTeamId(teamId);
        team.setName(teamId);
        team.setFormation(formation);
        career.addSessionTeam(team);
        for (SessionPlayer player : players) {
            career.addSessionPlayer(player);
            career.assignPlayerToTeam(player.getSessionPlayerId(), teamId);
        }
    }

    private void printScoreGrid(int[][] homeGoals, int[][] awayGoals) {
        System.out.println("\n[V25D25 diversity probe] homeGoals / awayGoals grid (rows=formation, cols=seed):");
        StringBuilder sb = new StringBuilder("       ");
        for (long seed : SEEDS) {
            sb.append(String.format("seed=%-4d ", seed));
        }
        System.out.println(sb);
        for (int fi = 0; fi < FORMATIONS.length; fi++) {
            sb = new StringBuilder(String.format("%-7s ", FORMATIONS[fi]));
            for (int si = 0; si < SEEDS.length; si++) {
                sb.append(String.format("%d-%d        ", homeGoals[fi][si], awayGoals[fi][si]));
            }
            System.out.println(sb);
        }
        System.out.println();
    }
}
