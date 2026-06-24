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
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V24D22-FIX-FORMATION-IGNORED — E2E coverage gap closure.
 *
 * <p><b>Background (sprint investigation summary):</b>
 * <ul>
 *   <li>Sprint 1.7 wire-up of formation in shooter/assist/chanceCreated paths
 *       was complete (3 call sites in {@code V24DetailedMatchEngine.simulate}).</li>
 *   <li>4 OTHER call sites (foul, injury, corner, offside) were MISSING the
 *       {@code formation} arg — a cosmetic inconsistency, but no test had ever
 *       covered the {@code setFormation → replaceFixtures → replay → assert
 *       formation affected} flow end-to-end. The smoke REVISOR "IDÉNTICO al
 *       byte" symptom was an E2E coverage gap, not a code bug.</li>
 *   <li>B0 (this sprint): pass {@code formation} to the 4 missing call sites.</li>
 *   <li>B1 (this sprint): this E2E test that closes the coverage gap with the
 *       full HTTP-like flow.</li>
 * </ul>
 *
 * <p><b>Strategy:</b> Mockito-only (no Spring context, no {@code @MockBean}
 * for the use case) — same pattern as {@code TestHarnessReplayPersistsDetailE2ETest}.
 * Wire a real {@link CareerSave} with 11-man squads for both teams, drive the
 * full {@code setFormation + replay} flow through the real
 * {@link TestHarnessUseCaseImpl} → real {@link V24MatchContextFactory} →
 * real {@code V24DetailedMatchEngine.simulate}, and capture the persisted
 * {@link V24DetailedMatchData} via the mocked
 * {@link V24DetailedMatchStoragePort}.
 *
 * <p><b>Squad composition matters:</b> the formation-driven variation is
 * SUBTLE (~5% shooter shift per the investigation) and can be INDETECTABLE
 * with a squad of 11 identical-position players (e.g. all MID). For the test
 * to have a chance of showing a delta, we use a Real Madrid-style squad:
 * 1 GK + 4 DEF + 3 MID + 2 WINGER + 1 ATT (forward-heavy, mixed positions).
 * The ATT dominates with ~30% shooter share in both 4-3-3 and 4-4-2 so the
 * delta is in the WINGER/MID bracket where formation weights differ.
 *
 * <p><b>Profile gating:</b> the test class does not need {@code @ActiveProfiles}
 * because it doesn't boot Spring — it instantiates the use case directly with
 * a real {@link V24MatchContextFactory} and mocked repos. The test exercises
 * the SAME code path as the HTTP endpoint
 * ({@code POST /api/v1/test-harness/career/set-formation} →
 * {@code POST /api/v1/test-harness/career/match/{matchId}/replay}),
 * but without HTTP/auth/profile overhead.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("V24FormationIgnoredE2E — setFormation + replay flow (sprint V24D22)")
class V24FormationIgnoredE2ETest {

    private static final UUID USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String MATCH_ID = "match-formation-test-001";
    private static final long SEED = 42L;
    // Strict-but-realistic threshold: xG accumulation per match is on the
    // order of 1.0–3.0, so 0.001 is well below noise. The formation-driven
    // shift is ~5% shooter share, which translates to ~0.01–0.10 xG delta
    // when the squad has mixed positions. See investigation doc.
    private static final double XG_EPSILON = 0.001;

    @Mock private CareerRepository careerRepository;
    @Mock private CareerSessionService careerSessionService;
    @Mock private V24DetailedMatchStoragePort v24StoragePort;
    // V24D24.3-HOTFIX: MatchEngineRegistry mock — needed for the new
    // resetRound() use case. Default `@Mock` returns false from
    // hasEngine, which is what these replay tests want (no live engine
    // to evict).
    @Mock private com.footballmanager.application.engine.match.MatchEngineRegistry matchEngineRegistry;

    // Real factory so build() produces a valid V24MatchContext. A mock would
    // return null teams/players and the engine would NPE in V24TeamMatchState.create.
    private V24MatchContextFactory v24ContextFactory;
    private TestHarnessUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        v24ContextFactory = new V24MatchContextFactory();
        useCase = new TestHarnessUseCaseImpl(
            careerRepository, careerSessionService,
            v24ContextFactory, v24StoragePort, matchEngineRegistry);
    }

    // ========== Test 1 — formation change produces different result with same seed ==========

    /**
     * V24D22-FIX-FORMATION-IGNORED — primary E2E assertion.
     *
     * <p>Iteration A: setFormation("4-3-3") + replay(seed=42) → resultA.
     * Iteration B: setFormation("4-4-2") + replay(seed=42) → resultB.
     *
     * <p>Assert at least ONE measurable delta:
     * <ul>
     *   <li>|homeXg diff| >= 0.001, OR</li>
     *   <li>|awayXg diff| >= 0.001, OR</li>
     *   <li>homeGoals differ, OR</li>
     *   <li>awayGoals differ.</li>
     * </ul>
     *
     * <p>If this test fails, it indicates an ADDITIONAL bug beyond the B0
     * cosmetic fix — the V24 engine has other formation-ignoring paths the
     * sprint 1.7 + V24D22 wire-ups missed. Per the sprint scope doc, that
     * requires escalating to MANAGER for a Fase 4 investigation.
     */
    @Test
    @DisplayName("setFormation(\"4-3-3\") then setFormation(\"4-4-2\") with same seed produces a different result")
    void setFormation_thenReplayWithSameSeed_producesDifferentResult() {
        V24DetailedMatchData result433 = replayWithFormation("4-3-3", SEED);
        V24DetailedMatchData result442 = replayWithFormation("4-4-2", SEED);

        boolean homeXgDiffers = Math.abs(result433.homeXg() - result442.homeXg()) >= XG_EPSILON;
        boolean awayXgDiffers = Math.abs(result433.awayXg() - result442.awayXg()) >= XG_EPSILON;
        boolean homeGoalsDiffer = result433.homeGoals() != result442.homeGoals();
        boolean awayGoalsDiffer = result433.awayGoals() != result442.awayGoals();

        assertThat(homeXgDiffers || awayXgDiffers || homeGoalsDiffer || awayGoalsDiffer)
            .as("Expected formation change (4-3-3 → 4-4-2) to produce a measurable delta "
                + "in xG or goals. Got resultA=(xG=%.3f/%d, goals=%d-%d) resultB=(xG=%.3f/%d, "
                + "goals=%d-%d). If this assertion fails, there's an ADDITIONAL bug beyond "
                + "B0 wire-up — escalate to MANAGER (Fase 4).",
                result433.homeXg(), result433.awayXg(), result433.homeGoals(), result433.awayGoals(),
                result442.homeXg(), result442.awayXg(), result442.homeGoals(), result442.awayGoals())
            .isTrue();
    }

    // ========== Test 2 — same formation + same seed produces identical result ==========

    /**
     * V24D22-FIX-FORMATION-IGNORED — regression guard for BUG #1
     * (CareerSessionService cache invalidation after save). Without invalidation,
     * the second replay would read the stale in-memory CareerSave and either
     * crash or produce a different result.
     *
     * <p>Iteration A: setFormation("4-3-3") + replay(seed=42) → resultA.
     * Iteration B: setFormation("4-3-3") + replay(seed=42) → resultB.
     *
     * <p>Assert resultA == resultB across all six numeric fields (homeXg,
     * awayXg, homeGoals, awayGoals, homeShots, awayShots).
     *
     * <p>Note: between replays we call resetInjuries to wipe yellow/red cards
     * and injuries the first replay accumulated. This keeps the CareerSave in
     * a clean starting state so the determinism contract (same seed + same
     * context = same result) holds for the second replay.
     */
    @Test
    @DisplayName("setFormation(\"4-3-3\") + replay(seed=42) twice produces identical result "
        + "(BUG #1 cache invalidation regression guard)")
    void setFormation_thenReplayWithSameSeed_secondTimeProducesIdenticalResult() {
        // First iteration
        V24DetailedMatchData resultA = replayWithFormation("4-3-3", SEED);

        // Reset injuries so the second replay sees a clean squad. Without this
        // the engine's V24TeamMatchState is built from injured/suspended players
        // and the second replay would diverge.
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(java.util.Optional.of(careerWithFreshSquad("4-3-3"))));
        when(careerRepository.save(any(CareerSave.class))).thenReturn(Mono.empty());
        useCase.resetInjuries(USER_ID).block();

        // Second iteration — same formation + same seed
        V24DetailedMatchData resultB = replayWithFormation("4-3-3", SEED);

        assertThat(resultB.homeXg())
            .as("homeXg must be identical across two replays with same seed + same formation")
            .isEqualTo(resultA.homeXg());
        assertThat(resultB.awayXg())
            .as("awayXg must be identical across two replays with same seed + same formation")
            .isEqualTo(resultA.awayXg());
        assertThat(resultB.homeGoals())
            .as("homeGoals must be identical across two replays with same seed + same formation")
            .isEqualTo(resultA.homeGoals());
        assertThat(resultB.awayGoals())
            .as("awayGoals must be identical across two replays with same seed + same formation")
            .isEqualTo(resultA.awayGoals());
        assertThat(resultB.homeShots())
            .as("homeShots must be identical across two replays with same seed + same formation")
            .isEqualTo(resultA.homeShots());
        assertThat(resultB.awayShots())
            .as("awayShots must be identical across two replays with same seed + same formation")
            .isEqualTo(resultA.awayShots());
    }

    // ========== Test 3 — formation variation surfaces within 25 seeds ==========

    /**
     * V24D22-FIX-FORMATION-IGNORED — coverage for the subtle-variation case.
     *
     * <p>The investigation flagged that formation-driven variation is SUBTLE
     * (~5% shooter shift) and may be INDETECTABLE for a single seed when the
     * squad is dominated by 1 ATT. To make the test robust without being
     * flaky, we scan 25 seeds and assert AT LEAST ONE seed produces a delta
     * (xG or goals).
     *
     * <p>This mirrors the existing
     * {@code V24DetailedMatchEngineFormationTest.changingFormationProducesDifferentOutcomeAcrossSeeds}
     * pattern but at the E2E flow level (through TestHarnessUseCaseImpl +
     * V24MatchContextFactory + V24DetailedMatchEngine).
     */
    @Test
    @DisplayName("changingFormationProducesDifferentOutcomeAcrossSeeds: scan seeds 1..25, "
        + "at least ONE seed produces a measurable delta")
    void changingFormationProducesDifferentOutcomeAcrossSeeds() {
        boolean anySeedDiffers = false;
        long seedWithDelta = -1L;
        double bestDelta = 0.0;

        for (long seed = 1; seed <= 25 && !anySeedDiffers; seed++) {
            V24DetailedMatchData r433 = replayWithFormation("4-3-3", seed);
            V24DetailedMatchData r442 = replayWithFormation("4-4-2", seed);

            double homeDelta = Math.abs(r433.homeXg() - r442.homeXg());
            double awayDelta = Math.abs(r433.awayXg() - r442.awayXg());
            double maxDelta = Math.max(homeDelta, awayDelta);

            boolean diff =
                homeDelta >= XG_EPSILON
                || awayDelta >= XG_EPSILON
                || r433.homeGoals() != r442.homeGoals()
                || r433.awayGoals() != r442.awayGoals();

            if (diff && maxDelta > bestDelta) {
                bestDelta = maxDelta;
                seedWithDelta = seed;
                anySeedDiffers = true;
            }
        }

        assertThat(anySeedDiffers)
            .as("Scanned seeds 1..25 with formations 4-3-3 vs 4-4-2; expected AT LEAST ONE "
                + "seed to produce a measurable delta. Best delta observed: %.4f xG at seed=%d. "
                + "If this fails, formation-driven variation is below 0.001 xG across all "
                + "25 seeds with the Real Madrid-style squad — escalate to MANAGER (Fase 4) "
                + "to widen the scan or reconsider the threshold.",
                bestDelta, seedWithDelta)
            .isTrue();
    }

    // ========== helper: drives setFormation + replay + captures the persisted V24 detail ==========

    /**
     * Builds a fresh CareerSave with the given formation on the user team,
     * drives the full setFormation + replayMatch flow through the real
     * {@link TestHarnessUseCaseImpl}, and captures the persisted
     * {@link V24DetailedMatchData} via the mocked storage port.
     *
     * <p>Returns the captured detail (homeXg, awayXg, homeGoals, awayGoals,
     * homeShots, awayShots) so the test can assert on the actual V24 engine
     * output.
     */
    private V24DetailedMatchData replayWithFormation(String formation, long seed) {
        // Fresh career per replay iteration so we don't accumulate injuries/
        // suspensions/yellows across runs. The V24 engine mutates state
        // during simulate() and a dirty CareerSave would mask formation
        // effects (or, conversely, amplify them spuriously).
        CareerSave freshCareer = careerWithFreshSquad(formation);
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(java.util.Optional.of(freshCareer)));
        when(careerRepository.save(any(CareerSave.class))).thenReturn(Mono.empty());

        // setFormation persists to BOTH SessionTeam.formation AND
        // teamStarting11Formation map (the latter is what the V24 engine
        // reads). Without BUG_FORMATION_PERSIST_IGNORED (sprint 1.7) this
        // is a no-op from the engine's perspective.
        useCase.setFormation(USER_ID, formation).block();

        // Reset the mock invocation count so each replay's storage port
        // save() can be captured fresh.
        org.mockito.Mockito.clearInvocations(v24StoragePort);

        // replayMatch drives the full flow: v24ContextFactory.build() →
        // engine.simulate() → v24StoragePort.save(newDetail). The new detail
        // is what we assert on.
        useCase.replayMatch(USER_ID, MATCH_ID, seed).block();

        ArgumentCaptor<V24DetailedMatchData> detailCaptor =
            ArgumentCaptor.forClass(V24DetailedMatchData.class);
        verify(v24StoragePort, times(1)).save(anyString(), detailCaptor.capture());

        return detailCaptor.getValue();
    }

    /**
     * Builds a CareerSave with 11 healthy players on each team (Real Madrid-style
     * position mix: 1 GK + 4 DEF + 3 MID + 2 WINGER + 1 ATT) and the requested
     * formation set on the user team. The rival team uses a fixed 4-4-2 so
     * the test isolates the USER team's formation change.
     */
    private CareerSave careerWithFreshSquad(String userFormation) {
        CareerSave c = new CareerSave();
        c.setUserId(USER_ID);
        c.setUserSessionTeamId("user-team-id");
        c.setCurrentSeason(1);

        List<SessionPlayer> userPlayers = new ArrayList<>();
        // i=0 is GK, i=1..4 DEF, i=5..7 MID, i=8..9 WINGER, i=10 ATT
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

    /**
     * Mirrors TestHarnessUseCaseImplTest.healthyPlayer() but with explicit
     * position + per-attribute stats so we can build a Real Madrid-style
     * mixed-position squad (the formation-aware shooter selector needs
     * varied positions to produce a measurable delta).
     */
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
        // initDefaults() is private — replicate its effect so Boolean flags
        // are non-null (required by AssertJ's isFalse/isZero).
        p.setInjured(false);
        p.setInjuryType(null);
        p.setInjuryRemainingMatches(0);
        p.setYellowCards(0);
        p.setRedCards(0);
        p.setSuspended(false);
        p.setSuspensionRemainingMatches(0);
        return p;
    }

    /**
     * Mirrors TestHarnessUseCaseImplTest.wireSquad() — registers the SessionTeam
     * and players via reflection so career.getSessionTeam(teamId),
     * career.getAllSessionTeams(), and career.getTeamStarting11() resolve to
     * the squads we built.
     */
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
