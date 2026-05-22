package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngine;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.application.service.simulation.v24.V24MatchTimeline;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngineProvider;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.service.MatchSimulator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6K2: Diagnostic harness to measure actual V24 career mutation outputs
 * before any constant tuning.
 *
 * <p>This file contains TWO distinct diagnostic paths:
 *
 * <ol>
 *   <li><b>Synthetic / Sanity Diagnostic</b> — uses a minimal deterministic event provider
 *       that does NOT represent real football event distributions.
 *       Only verifies invariant bounds (energy in [0,100], form in [1,99], no negatives).
 *       Named with "_syntheticInvariant" suffix to make intent clear.</li>
 *
 *   <li><b>Real V24 Engine Diagnostic</b> — uses the actual V24DetailedMatchEngine
 *       (the same engine used in production) with deterministic seeds.
 *       Measures what the real model actually produces:
 *       injury event rate, card event rate, form movement, energy behavior.
 *       This is the actual balancing baseline measurement.</li>
 * </ol>
 *
 * <p>ALL mutation flags are enabled in both paths.
 * Neither path tunes constants — measurement only.
 */
class V24MutationBalancingDiagnosticTest {

    private static final String HOME = UUID.randomUUID().toString();
    private static final String AWAY = UUID.randomUUID().toString();

    // ========================================================================
    // PATH 1: SYNTHETIC / SANITY DIAGNOSTIC
    // NOT representative of real football — only verifies invariant bounds
    // ========================================================================

    /**
     * Synthetic diagnostic — NOT representative of real football.
     *
     * Uses a minimal deterministic event provider that produces
     * low-frequency events (yellow card ~1 per 7 matches, no injuries).
     *
     * Purpose: verify that the mutation pipeline produces valid output
     * (no negative values, no exceptions, valid ranges) regardless of
     * whether events are generated.
     *
     * This is a SANITY TEST, not a balancing measurement.
     */
    @Test
    void diagnostic_syntheticInvariant_preservesMutationBounds() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        // All mutation flags ON
        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null,
                false,  // useV23LeagueEngine
                true,   // useV24DetailedEngine
                false,  // persistDetail
                fakeStorage,
                true,   // mutateCareerState
                true,   // persistInjuries
                true,   // persistFatigue
                true,   // persistDiscipline
                true,   // persistForm
                new MinimalDeterministicEngine()  // minimal, non-representative events
        );

        CareerSave career = makeCareer(HOME, AWAY, HOME, AWAY, 11, 11);
        career.getData().setCareerId("synthetic_" + System.currentTimeMillis());
        career.getSeasonManager().setCurrentSeason(1);

        List<MatchFixture> fixtures = new ArrayList<>();
        for (int r = 1; r <= 38; r++) {
            fixtures.add(new MatchFixture("synth_r" + r, HOME, AWAY, r));
        }
        career.setTournamentState(makeTournamentState(fixtures));

        int totalInjuriesObserved = 0;
        int totalSuspensionsObserved = 0;

        for (int round = 1; round <= 38; round++) {
            int preInjured = countCurrentlyInjured(career);
            int preSuspended = countCurrentlySuspended(career);

            simulator.simulateLeagueRound(career, round);

            int postInjured = countCurrentlyInjured(career);
            int postSuspended = countCurrentlySuspended(career);

            if (postInjured > preInjured) totalInjuriesObserved += (postInjured - preInjured);
            if (postSuspended > preSuspended) totalSuspensionsObserved += (postSuspended - preSuspended);
        }

        // ===== PRINT SYnthetic REPORT (clearly labeled) =====
        int totalPlayers = countAllPlayers(career);
        double avgForm = computeAverageForm(career);
        int minForm = computeMinForm(career);
        int maxForm = computeMaxForm(career);
        double avgEnergy = computeAverageEnergy(career);
        double minEnergy = computeMinEnergy(career);
        double maxEnergy = computeMaxEnergy(career);

        System.out.println("\n========================================");
        System.out.println("V24D6K2 SYNTHETIC SANITY DIAGNOSTIC");
        System.out.println("========================================");
        System.out.println("NOTE: This diagnostic uses a minimal");
        System.out.println("deterministic provider. Events are NOT");
        System.out.println("representative of real football.");
        System.out.println("DO NOT use these numbers as baselines.");
        System.out.println("----------------------------------------");
        System.out.printf(Locale.US, "Season length: 38 rounds%n");
        System.out.printf(Locale.US, "Squad size: %d players%n", totalPlayers);
        System.out.println("----------------------------------------");
        System.out.println("INJURY (synthetic — NOT representative)");
        System.out.printf(Locale.US, "  New injuries (season): %d%n", totalInjuriesObserved);
        System.out.printf(Locale.US, "  Injured at season end: %d%n", countCurrentlyInjured(career));
        System.out.println("----------------------------------------");
        System.out.println("SUSPENSION (synthetic — NOT representative)");
        System.out.printf(Locale.US, "  New suspensions (season): %d%n", totalSuspensionsObserved);
        System.out.printf(Locale.US, "  Suspended at season end: %d%n", countCurrentlySuspended(career));
        System.out.println("----------------------------------------");
        System.out.println("ENERGY (Season End)");
        System.out.printf(Locale.US, "  Average: %.1f  Min: %.1f  Max: %.1f%n", avgEnergy, minEnergy, maxEnergy);
        System.out.println("----------------------------------------");
        System.out.println("FORM (Season End)");
        System.out.printf(Locale.US, "  Average: %.1f  Min: %d  Max: %d%n", avgForm, minForm, maxForm);
        System.out.println("========================================\n");

        // === Broad sanity assertions — invariants only ===
        assertTrue(totalPlayers > 0, "Squad must have players");

        for (SessionPlayer p : getAllPlayers(career)) {
            assertTrue(p.getEnergy() >= 0, "Energy must not be negative: " + p.getSessionPlayerId());
            assertTrue(p.getEnergy() <= 100, "Energy must not exceed 100: " + p.getSessionPlayerId());
            assertTrue(p.getForm() >= 1 && p.getForm() <= 99,
                    "Form must be in [1,99]: " + p.getSessionPlayerId() + " = " + p.getForm());
            if (p.getInjuryRemainingMatches() != null) {
                assertTrue(p.getInjuryRemainingMatches() >= 0,
                        "Injury remaining must not be negative: " + p.getSessionPlayerId());
            }
            if (p.getSuspensionRemainingMatches() != null) {
                assertTrue(p.getSuspensionRemainingMatches() >= 0,
                        "Suspension remaining must not be negative: " + p.getSessionPlayerId());
            }
        }

        assertNotNull(career.getTournamentState(), "Tournament state must not be null");
    }

    // ========================================================================
    // PATH 2A: REAL V24 ENGINE DIAGNOSTIC — FIXED XI STRESS TEST
    // Uses actual V24DetailedMatchEngine — worst-case, no rotation
    // ========================================================================

    /**
     * Real V24 engine stress diagnostic — WORST-CASE no-rotation.
     *
     * Uses V24DetailedMatchEngine with deterministic seeds.
     * REUSES the same fixed starting XI for all 50 matches.
     * This is a STRESS TEST — NOT representative of real gameplay.
     *
     * Purpose: measure worst-case attrition when no rotation occurs.
     * Compare with rotationAware diagnostic to see how rotation helps.
     */
    @Test
    void diagnostic_realV24Engine_fixedXIStress() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        // Use the REAL V24DetailedMatchEngine — same as production
        V24DetailedMatchEngine realEngine = new V24DetailedMatchEngine();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null,
                false,  // useV23LeagueEngine
                true,   // useV24DetailedEngine
                false,  // persistDetail
                fakeStorage,
                true,   // mutateCareerState
                true,   // persistInjuries
                true,   // persistFatigue
                true,   // persistDiscipline
                true,   // persistForm
                realEngine  // REAL engine
        );

        CareerSave career = makeCareer(HOME, AWAY, HOME, AWAY, 11, 11);
        career.getData().setCareerId("real_engine_" + System.currentTimeMillis());
        career.getSeasonManager().setCurrentSeason(1);

        // Build 50 matches with varying deterministic seeds
        // (38-round season with seed variation across rounds)
        List<MatchFixture> fixtures = new ArrayList<>();
        for (int r = 1; r <= 50; r++) {
            // Each match gets a distinct seed derived from round number
            fixtures.add(new MatchFixture("real_r" + r, HOME, AWAY, r));
        }
        career.setTournamentState(makeTournamentState(fixtures));

        // Aggregate counters
        int totalInjuriesObserved = 0;      // newlyInjuredTransitions (pre→post)
        int totalYellowCards = 0;
        int totalRedCards = 0;
        int totalNewSuspensions = 0;        // threshold + red-card combined

        // Per-round snapshots using Sets for uniqueness
        List<Double> energyPerRound = new ArrayList<>();
        List<Double> avgFormPerRound = new ArrayList<>();
        List<Integer> uniqueUnavailablePerRound = new ArrayList<>();
        List<Integer> currentlyInjuredPerRound = new ArrayList<>();
        List<Integer> currentlySuspendedPerRound = new ArrayList<>();

        for (int round = 1; round <= 50; round++) {
            Set<String> preInjuredIds = getCurrentlyInjuredPlayerIds(career);
            Set<String> preSuspendedIds = getCurrentlySuspendedPlayerIds(career);

            simulator.simulateLeagueRound(career, round);

            Set<String> postInjuredIds = getCurrentlyInjuredPlayerIds(career);
            Set<String> postSuspendedIds = getCurrentlySuspendedPlayerIds(career);

            // Newly injured transitions (player became injured this round)
            int newlyInjured = 0;
            for (String id : postInjuredIds) {
                if (!preInjuredIds.contains(id)) newlyInjured++;
            }
            totalInjuriesObserved += newlyInjured;

            // Newly suspended transitions
            int newlySuspended = 0;
            for (String id : postSuspendedIds) {
                if (!preSuspendedIds.contains(id)) newlySuspended++;
            }
            totalNewSuspensions += newlySuspended;

            // Per-round snapshots
            energyPerRound.add(computeAverageEnergy(career));
            avgFormPerRound.add(computeAverageForm(career));
            uniqueUnavailablePerRound.add(countUniqueUnavailable(career));
            currentlyInjuredPerRound.add(postInjuredIds.size());
            currentlySuspendedPerRound.add(postSuspendedIds.size());
        }

        // Count yellow/red cards from players' discipline state
        // (cards are tracked internally by V24DisciplineMutationApplier on SessionPlayer)
        int totalYellows = 0;
        int totalReds = 0;
        for (SessionPlayer p : getAllPlayers(career)) {
            if (p.getYellowCards() != null) totalYellows += p.getYellowCards();
            if (p.getRedCards() != null && p.getRedCards() > 0) totalReds++;
        }

        // End-of-season metrics
        double avgFormSeason = computeAverageForm(career);
        int minFormSeason = computeMinForm(career);
        int maxFormSeason = computeMaxForm(career);
        double avgEnergySeason = computeAverageEnergy(career);
        double minEnergySeason = computeMinEnergy(career);
        double maxEnergySeason = computeMaxEnergy(career);
        int playersAtLowEnergy = countPlayersBelowEnergy(career, 40);
        int playersAtCriticalEnergy = countPlayersBelowEnergy(career, 20);

        // Checkpoint energy and availability
        double[] checkpointEnergy = {
                energyPerRound.get(4),   // round 5
                energyPerRound.get(9),   // round 10
                energyPerRound.get(19),  // round 20
                energyPerRound.get(29)   // round 30
        };
        int[] checkpointUniqueUnavailable = {
                uniqueUnavailablePerRound.get(4),
                uniqueUnavailablePerRound.get(9),
                uniqueUnavailablePerRound.get(19),
                uniqueUnavailablePerRound.get(29)
        };
        int[] checkpointCurrentlyInjured = {
                currentlyInjuredPerRound.get(4),
                currentlyInjuredPerRound.get(9),
                currentlyInjuredPerRound.get(19),
                currentlyInjuredPerRound.get(29)
        };
        int[] checkpointCurrentlySuspended = {
                currentlySuspendedPerRound.get(4),
                currentlySuspendedPerRound.get(9),
                currentlySuspendedPerRound.get(19),
                currentlySuspendedPerRound.get(29)
        };

        // End-state counts (unique + overlapping)
        Set<String> endInjuredIds = getCurrentlyInjuredPlayerIds(career);
        Set<String> endSuspendedIds = getCurrentlySuspendedPlayerIds(career);
        Set<String> endUnavailableIds = new java.util.HashSet<>();
        endUnavailableIds.addAll(endInjuredIds);
        endUnavailableIds.addAll(endSuspendedIds);
        int uniqueUnavailableAtEnd = endUnavailableIds.size();
        int currentlyInjuredAtEnd = endInjuredIds.size();
        int currentlySuspendedAtEnd = endSuspendedIds.size();
        int overlappingInjuredAndSuspended = 0;
        for (String id : endInjuredIds) {
            if (endSuspendedIds.contains(id)) overlappingInjuredAndSuspended++;
        }

        // ===== PRINT FIXED XI STRESS REPORT =====
        System.out.println("\n========================================");
        System.out.println("V24D6K4 FIXED XI STRESS DIAGNOSTIC");
        System.out.println("========================================");
        System.out.println("WARNING: This diagnostic uses a FIXED");
        System.out.println("starting XI with NO rotation. This is");
        System.out.println("WORST-CASE — NOT representative of");
        System.out.println("real gameplay or expected outcomes.");
        System.out.println("Use ONLY for worst-case stress testing.");
        System.out.println("----------------------------------------");
        System.out.println("Engine: V24DetailedMatchEngine (production)");
        System.out.printf(Locale.US, "Matches simulated: 50%n");
        System.out.printf(Locale.US, "Squad size: %d players%n", countAllPlayers(career));
        System.out.println("Mode: FIXED XI — no rotation");
        System.out.println("Mutation flags: ALL ENABLED");
        System.out.println("----------------------------------------");
        System.out.println("INJURY (cumulative over run)");
        System.out.printf(Locale.US, "  Newly injured transitions: %d%n", totalInjuriesObserved);
        System.out.printf(Locale.US, "  Avg per match: %.3f%n", (double) totalInjuriesObserved / 50.0);
        System.out.printf(Locale.US, "  Currently injured at end: %d%n", currentlyInjuredAtEnd);
        System.out.println("----------------------------------------");
        System.out.println("CARDS (cumulative on players — yellow/red counts)");
        System.out.printf(Locale.US, "  Total yellow cards accumulated: %d%n", totalYellows);
        System.out.printf(Locale.US, "  Total red cards: %d%n", totalReds);
        System.out.printf(Locale.US, "  Avg yellows per player: %.2f%n", (double) totalYellows / countAllPlayers(career));
        System.out.println("----------------------------------------");
        System.out.println("SUSPENSIONS (cumulative over run)");
        System.out.printf(Locale.US, "  New suspensions detected: %d%n", totalNewSuspensions);
        System.out.printf(Locale.US, "  Avg per match: %.3f%n", (double) totalNewSuspensions / 50.0);
        System.out.printf(Locale.US, "  Currently suspended at end: %d%n", currentlySuspendedAtEnd);
        System.out.printf(Locale.US, "  Overlapping injured+suspended at end: %d%n", overlappingInjuredAndSuspended);
        System.out.println("----------------------------------------");
        System.out.println("AVAILABILITY CHECKPOINTS (unique unavailable per round)");
        System.out.printf(Locale.US, "  Round  5: injured=%d  suspended=%d  uniqueUnavailable=%d%n",
                checkpointCurrentlyInjured[0], checkpointCurrentlySuspended[0], checkpointUniqueUnavailable[0]);
        System.out.printf(Locale.US, "  Round 10: injured=%d  suspended=%d  uniqueUnavailable=%d%n",
                checkpointCurrentlyInjured[1], checkpointCurrentlySuspended[1], checkpointUniqueUnavailable[1]);
        System.out.printf(Locale.US, "  Round 20: injured=%d  suspended=%d  uniqueUnavailable=%d%n",
                checkpointCurrentlyInjured[2], checkpointCurrentlySuspended[2], checkpointUniqueUnavailable[2]);
        System.out.printf(Locale.US, "  Round 30: injured=%d  suspended=%d  uniqueUnavailable=%d%n",
                checkpointCurrentlyInjured[3], checkpointCurrentlySuspended[3], checkpointUniqueUnavailable[3]);
        System.out.println("----------------------------------------");
        System.out.println("ENERGY (End of 50-match run)");
        System.out.printf(Locale.US, "  Average: %.1f  Min: %.1f  Max: %.1f%n", avgEnergySeason, minEnergySeason, maxEnergySeason);
        System.out.printf(Locale.US, "  Players <40 energy: %d%n", playersAtLowEnergy);
        System.out.printf(Locale.US, "  Players <20 energy: %d%n", playersAtCriticalEnergy);
        System.out.println("----------------------------------------");
        System.out.println("FORM (End of 50-match run)");
        System.out.printf(Locale.US, "  Average: %.1f  Min: %d  Max: %d%n", avgFormSeason, minFormSeason, maxFormSeason);
        System.out.println("========================================\n");

        // Invariant assertion: unique unavailable must not exceed squad size
        assertTrue(uniqueUnavailableAtEnd <= countAllPlayers(career),
                "Unique unavailable (" + uniqueUnavailableAtEnd + ") must not exceed squad size ("
                        + countAllPlayers(career) + ")");

        // Interpretation note if zero events observed
        if (totalInjuriesObserved == 0) {
            System.out.println("[NOTE] Real engine produced 0 injuries over 50 matches.");
            System.out.println("  Possible reasons:");
            System.out.println("  1. Sample size still too small for BASE_INJURY_PROB=0.003");
            System.out.println("  2. V24DetailedMatchEngine may call injury model less frequently than once per player per match");
            System.out.println("  3. Injury model may require specific match situations to trigger");
            System.out.println("  4. Current implementation may not yet wire injury events into timeline");
            System.out.println("  DO NOT tune injury constants until call frequency is confirmed.\n");
        }
        if (totalYellows == 0 && totalReds == 0) {
            System.out.println("[NOTE] Real engine produced 0 yellow/red cards over 50 matches.");
            System.out.println("  Possible reasons:");
            System.out.println("  1. V24DisciplineModel may not emit card events in current implementation");
            System.out.println("  2. Card events may require specific match situations to trigger");
            System.out.println("  3. Discipline mutation may not be wired into V24DetailedMatchEngine timeline");
            System.out.println("  DO NOT tune discipline constants until event emission path is confirmed.\n");
        }

        // === Broad sanity assertions — invariants only ===
        assertTrue(countAllPlayers(career) > 0, "Squad must have players");

        for (SessionPlayer p : getAllPlayers(career)) {
            assertTrue(p.getEnergy() >= 0, "Energy must not be negative: " + p.getSessionPlayerId());
            assertTrue(p.getEnergy() <= 100, "Energy must not exceed 100: " + p.getSessionPlayerId());
            assertTrue(p.getForm() >= 1 && p.getForm() <= 99,
                    "Form must be in [1,99]: " + p.getSessionPlayerId() + " = " + p.getForm());
            if (p.getInjuryRemainingMatches() != null) {
                assertTrue(p.getInjuryRemainingMatches() >= 0,
                        "Injury remaining must not be negative: " + p.getSessionPlayerId());
            }
            if (p.getSuspensionRemainingMatches() != null) {
                assertTrue(p.getSuspensionRemainingMatches() >= 0,
                        "Suspension remaining must not be negative: " + p.getSessionPlayerId());
            }
        }

        assertNotNull(career.getTournamentState(), "Tournament state must not be null");
    }

    // ========================================================================
    // PATH 2B: REAL V24 ENGINE DIAGNOSTIC — ROTATION-AWARE (PRIMARY)
    // ========================================================================

    /**
     * Real V24 engine rotation-aware diagnostic — PRIMARY BALANCING MEASUREMENT.
     *
     * Uses V24DetailedMatchEngine with deterministic seeds.
     * Auto-selects starting XI each round using:
     * - isDiagnosticPlayerAvailable() — excludes injured/suspended players
     * - Energy-based preference — prefers higher-energy players
     * - Avoids energy <= 20 players when alternatives exist
     *
     * This is the expected gameplay measurement.
     * Compare with fixedXI stress test to see rotation effect.
     */
    @Test
    void diagnostic_realV24Engine_rotationAware_baselineMetricsForReview() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        V24DetailedMatchEngine realEngine = new V24DetailedMatchEngine();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null,
                false,  // useV23LeagueEngine
                true,   // useV24DetailedEngine
                false,  // persistDetail
                fakeStorage,
                true,   // mutateCareerState
                true,   // persistInjuries
                true,   // persistFatigue
                true,   // persistDiscipline
                true,   // persistForm
                realEngine
        );

        CareerSave career = makeCareer(HOME, AWAY, HOME, AWAY, 11, 11);
        career.getData().setCareerId("rotation_aware_" + System.currentTimeMillis());
        career.getSeasonManager().setCurrentSeason(1);

        List<MatchFixture> fixtures = new ArrayList<>();
        for (int r = 1; r <= 50; r++) {
            fixtures.add(new MatchFixture("rot_r" + r, HOME, AWAY, r));
        }
        career.setTournamentState(makeTournamentState(fixtures));

        // Build initial starting XI using rotation-aware select
        refreshStartingXIWithRotation(career, HOME);

        // Aggregate counters
        int totalInjuriesObserved = 0;
        int totalInjuryRecoveries = 0;
        int totalYellowCards = 0;
        int totalRedCards = 0;
        int totalNewSuspensions = 0;
        int totalLineupFailures = 0;
        int totalRotationsDone = 0;

        // Per-round snapshots
        List<Double> energyPerRound = new ArrayList<>();
        List<Double> avgFormPerRound = new ArrayList<>();
        List<Integer> uniqueUnavailablePerRound = new ArrayList<>();
        List<Integer> currentlyInjuredPerRound = new ArrayList<>();
        List<Integer> currentlySuspendedPerRound = new ArrayList<>();
        List<Integer> starterEnergyAvgPerRound = new ArrayList<>();
        List<Integer> benchEnergyAvgPerRound = new ArrayList<>();
        List<Integer> availablePlayersPerRound = new ArrayList<>();

        for (int round = 1; round <= 50; round++) {
            Set<String> preInjuredIds = getCurrentlyInjuredPlayerIds(career);
            Set<String> preSuspendedIds = getCurrentlySuspendedPlayerIds(career);

            simulator.simulateLeagueRound(career, round);

            Set<String> postInjuredIds = getCurrentlyInjuredPlayerIds(career);
            Set<String> postSuspendedIds = getCurrentlySuspendedPlayerIds(career);

            // Newly injured transitions
            int newlyInjured = 0;
            for (String id : postInjuredIds) {
                if (!preInjuredIds.contains(id)) newlyInjured++;
            }
            totalInjuriesObserved += newlyInjured;

            // Injury recoveries (previously injured, now recovered)
            for (String id : preInjuredIds) {
                if (!postInjuredIds.contains(id)) totalInjuryRecoveries++;
            }

            // Newly suspended transitions
            int newlySuspended = 0;
            for (String id : postSuspendedIds) {
                if (!preSuspendedIds.contains(id)) newlySuspended++;
            }
            totalNewSuspensions += newlySuspended;

            // Per-round snapshots
            energyPerRound.add(computeAverageEnergy(career));
            avgFormPerRound.add(computeAverageForm(career));
            uniqueUnavailablePerRound.add(countUniqueUnavailable(career));
            currentlyInjuredPerRound.add(postInjuredIds.size());
            currentlySuspendedPerRound.add(postSuspendedIds.size());

            // Count available players
            availablePlayersPerRound.add(countAvailablePlayers(career, HOME));

            // Starter vs bench energy
            List<SessionPlayer> currentXI = getCurrentStartingXI(career, HOME);
            Set<String> starterIds = currentXI.stream()
                    .map(SessionPlayer::getSessionPlayerId)
                    .collect(Collectors.toSet());

            int starterEnergySum = 0;
            int starterCount = 0;
            int benchEnergySum = 0;
            int benchCount = 0;
            for (SessionPlayer p : getAllPlayers(career)) {
                if (starterIds.contains(p.getSessionPlayerId())) {
                    starterEnergySum += p.getEnergy();
                    starterCount++;
                } else {
                    benchEnergySum += p.getEnergy();
                    benchCount++;
                }
            }
            starterEnergyAvgPerRound.add(starterCount > 0 ? starterEnergySum / starterCount : 0);
            benchEnergyAvgPerRound.add(benchCount > 0 ? benchEnergySum / benchCount : 0);

            // Refresh starting XI for next round
            int prevStarterCount = currentXI.size();
            refreshStartingXIWithRotation(career, HOME);
            List<SessionPlayer> newXI = getCurrentStartingXI(career, HOME);
            if (newXI.size() != prevStarterCount) totalRotationsDone++;

            // Energy-based rotation check
            totalRotationsDone += maybeRotateDueToEnergy(career, HOME);

            // Check if lineup was forced to use unavailable
            int forcedUnavailable = 0;
            for (SessionPlayer p : getCurrentStartingXI(career, HOME)) {
                if (!isDiagnosticPlayerAvailable(p)) forcedUnavailable++;
            }
            totalLineupFailures += forcedUnavailable;
        }

        // Count yellow/red cards
        int totalYellows = 0;
        int totalReds = 0;
        for (SessionPlayer p : getAllPlayers(career)) {
            if (p.getYellowCards() != null) totalYellows += p.getYellowCards();
            if (p.getRedCards() != null && p.getRedCards() > 0) totalReds++;
        }

        // End-state
        double avgFormSeason = computeAverageForm(career);
        int minFormSeason = computeMinForm(career);
        int maxFormSeason = computeMaxForm(career);
        double avgEnergySeason = computeAverageEnergy(career);
        double minEnergySeason = computeMinEnergy(career);
        double maxEnergySeason = computeMaxEnergy(career);
        int playersAtLowEnergy = countPlayersBelowEnergy(career, 40);
        int playersBelow20 = countPlayersBelowEnergy(career, 20);
        int playersBelow60 = countPlayersBelowEnergy(career, 60);

        // Checkpoint metrics
        int[] checkpointUniqueUnavailable = {
                uniqueUnavailablePerRound.get(4),
                uniqueUnavailablePerRound.get(9),
                uniqueUnavailablePerRound.get(19),
                uniqueUnavailablePerRound.get(29)
        };
        int[] checkpointCurrentlyInjured = {
                currentlyInjuredPerRound.get(4),
                currentlyInjuredPerRound.get(9),
                currentlyInjuredPerRound.get(19),
                currentlyInjuredPerRound.get(29)
        };

        // End-state unique counts
        Set<String> endInjuredIds = getCurrentlyInjuredPlayerIds(career);
        Set<String> endSuspendedIds = getCurrentlySuspendedPlayerIds(career);
        Set<String> endUnavailableIds = new HashSet<>();
        endUnavailableIds.addAll(endInjuredIds);
        endUnavailableIds.addAll(endSuspendedIds);
        int uniqueUnavailableAtEnd = endUnavailableIds.size();
        int currentlyInjuredAtEnd = endInjuredIds.size();
        int currentlySuspendedAtEnd = endSuspendedIds.size();

        // ===== PRINT ROTATION-AWARE REPORT =====
        System.out.println("\n========================================");
        System.out.println("V24D6K4 ROTATION-AWARE DIAGNOSTIC");
        System.out.println("========================================");
        System.out.println("Engine: V24DetailedMatchEngine (production)");
        System.out.printf(Locale.US, "Matches simulated: 50%n");
        System.out.printf(Locale.US, "Squad size: %d players%n", countAllPlayers(career));
        System.out.println("Mode: ROTATION-AWARE (energy + availability)");
        System.out.println("Mutation flags: ALL ENABLED");
        System.out.println("----------------------------------------");
        System.out.println("LINEUP SELECTION");
        System.out.printf(Locale.US, "  Total forced unavailable starters: %d%n", totalLineupFailures);
        System.out.printf(Locale.US, "  Available players at end: %d / %d%n",
                availablePlayersPerRound.get(49), countAllPlayers(career));
        System.out.println("----------------------------------------");
        System.out.println("INJURY (cumulative over run)");
        System.out.printf(Locale.US, "  Newly injured transitions: %d%n", totalInjuriesObserved);
        System.out.printf(Locale.US, "  Avg per match: %.3f%n", (double) totalInjuriesObserved / 50.0);
        System.out.printf(Locale.US, "  Players recovered from injury: %d%n", totalInjuryRecoveries);
        System.out.printf(Locale.US, "  Currently injured at end: %d%n", currentlyInjuredAtEnd);
        System.out.println("----------------------------------------");
        System.out.println("CARDS (cumulative on players)");
        System.out.printf(Locale.US, "  Total yellow cards accumulated: %d%n", totalYellows);
        System.out.printf(Locale.US, "  Total red cards: %d%n", totalReds);
        System.out.printf(Locale.US, "  Avg yellows per player: %.2f%n", (double) totalYellows / countAllPlayers(career));
        System.out.println("----------------------------------------");
        System.out.println("SUSPENSIONS (cumulative over run)");
        System.out.printf(Locale.US, "  New suspensions detected: %d%n", totalNewSuspensions);
        System.out.printf(Locale.US, "  Currently suspended at end: %d%n", currentlySuspendedAtEnd);
        System.out.println("----------------------------------------");
        System.out.println("AVAILABILITY CHECKPOINTS (unique unavailable)");
        System.out.printf(Locale.US, "  Round  5: injured=%d  uniqueUnavailable=%d%n",
                checkpointCurrentlyInjured[0], checkpointUniqueUnavailable[0]);
        System.out.printf(Locale.US, "  Round 10: injured=%d  uniqueUnavailable=%d%n",
                checkpointCurrentlyInjured[1], checkpointUniqueUnavailable[1]);
        System.out.printf(Locale.US, "  Round 20: injured=%d  uniqueUnavailable=%d%n",
                checkpointCurrentlyInjured[2], checkpointUniqueUnavailable[2]);
        System.out.printf(Locale.US, "  Round 30: injured=%d  uniqueUnavailable=%d%n",
                checkpointCurrentlyInjured[3], checkpointUniqueUnavailable[3]);
        System.out.println("----------------------------------------");
        System.out.println("ENERGY CHECKPOINTS (rotation-aware)");
        System.out.printf(Locale.US, "  Round  5: avg=%.1f  starters=%d  bench=%d%n",
                energyPerRound.get(4), starterEnergyAvgPerRound.get(4), benchEnergyAvgPerRound.get(4));
        System.out.printf(Locale.US, "  Round 10: avg=%.1f  starters=%d  bench=%d%n",
                energyPerRound.get(9), starterEnergyAvgPerRound.get(9), benchEnergyAvgPerRound.get(9));
        System.out.printf(Locale.US, "  Round 20: avg=%.1f  starters=%d  bench=%d%n",
                energyPerRound.get(19), starterEnergyAvgPerRound.get(19), benchEnergyAvgPerRound.get(19));
        System.out.printf(Locale.US, "  Round 30: avg=%.1f  starters=%d  bench=%d%n",
                energyPerRound.get(29), starterEnergyAvgPerRound.get(29), benchEnergyAvgPerRound.get(29));
        System.out.println("----------------------------------------");
        System.out.println("ENERGY (End of 50-match run)");
        System.out.printf(Locale.US, "  Average: %.1f  Min: %.1f  Max: %.1f%n", avgEnergySeason, minEnergySeason, maxEnergySeason);
        System.out.printf(Locale.US, "  Players <60 energy: %d%n", playersBelow60);
        System.out.printf(Locale.US, "  Players <40 energy: %d%n", playersAtLowEnergy);
        System.out.printf(Locale.US, "  Players <20 energy: %d%n", playersBelow20);
        System.out.println("----------------------------------------");
        System.out.println("FORM (End of 50-match run)");
        System.out.printf(Locale.US, "  Average: %.1f  Min: %d  Max: %d%n", avgFormSeason, minFormSeason, maxFormSeason);
        System.out.println("========================================\n");

        // === Key comparison ===
        System.out.println("[ROTATION COMPARISON]");
        System.out.printf(Locale.US, "  Injury recoveries triggered: %s%n", totalInjuryRecoveries > 0 ? "YES" : "NO");
        System.out.printf(Locale.US, "  Unique unavailable at end (rotation): %d%n", uniqueUnavailableAtEnd);
        System.out.println("  Compare to FIXED XI stress (22/22 unavailable by round 30)");
        System.out.println("========================================\n");

        // Conclusion
        if (totalInjuriesObserved >= 40 && uniqueUnavailableAtEnd >= 20) {
            System.out.println("[CONCLUSION] Rotation did NOT prevent severe attrition.");
            System.out.println("  K5 should consider conservative tuning.\n");
        } else if (totalInjuryRecoveries == 0 && uniqueUnavailableAtEnd > 10) {
            System.out.println("[CONCLUSION] Injury recovery NOT triggering.");
            System.out.println("  Verify isDiagnosticPlayerAvailable() excludes injured.\n");
        } else {
            System.out.println("[CONCLUSION] Rotation is helping. Model behavior acceptable.\n");
        }

        // === Broad sanity assertions — invariants only ===
        assertTrue(countAllPlayers(career) > 0, "Squad must have players");

        for (SessionPlayer p : getAllPlayers(career)) {
            assertTrue(p.getEnergy() >= 0, "Energy must not be negative: " + p.getSessionPlayerId());
            assertTrue(p.getEnergy() <= 100, "Energy must not exceed 100: " + p.getSessionPlayerId());
            assertTrue(p.getForm() >= 1 && p.getForm() <= 99,
                    "Form must be in [1,99]: " + p.getSessionPlayerId() + " = " + p.getForm());
            if (p.getInjuryRemainingMatches() != null) {
                assertTrue(p.getInjuryRemainingMatches() >= 0,
                        "Injury remaining must not be negative: " + p.getSessionPlayerId());
            }
            if (p.getSuspensionRemainingMatches() != null) {
                assertTrue(p.getSuspensionRemainingMatches() >= 0,
                        "Suspension remaining must not be negative: " + p.getSessionPlayerId());
            }
        }

        assertTrue(uniqueUnavailableAtEnd <= countAllPlayers(career),
                "Unique unavailable must not exceed squad size");

        assertNotNull(career.getTournamentState(), "Tournament state must not be null");
    }

    // ========================================================================
    // PATH 2B HELPER: Rotation-Aware Starting XI Management
    // ========================================================================

    private boolean isDiagnosticPlayerAvailable(SessionPlayer player) {
        if (Boolean.TRUE.equals(player.getInjured())) return false;
        if (player.getInjuryRemainingMatches() != null && player.getInjuryRemainingMatches() > 0) return false;
        if (Boolean.TRUE.equals(player.getSuspended())) return false;
        if (player.getSuspensionRemainingMatches() != null && player.getSuspensionRemainingMatches() > 0) return false;
        return true;
    }

    private int countAvailablePlayers(CareerSave career, String teamId) {
        int count = 0;
        for (SessionPlayer p : getAllPlayers(career)) {
            if (isDiagnosticPlayerAvailable(p)) count++;
        }
        return count;
    }

    private List<SessionPlayer> getCurrentStartingXI(CareerSave career, String teamId) {
        List<String> starterIds = career.getTeamStarting11().get(teamId);
        if (starterIds == null) return new ArrayList<>();
        List<SessionPlayer> starters = new ArrayList<>();
        for (String id : starterIds) {
            SessionPlayer p = career.getSessionPlayer(id);
            if (p != null) starters.add(p);
        }
        return starters;
    }

    private void refreshStartingXIWithRotation(CareerSave career, String teamId) {
        List<SessionPlayer> allPlayers = getAllPlayers(career);

        List<SessionPlayer> available = new ArrayList<>();
        List<SessionPlayer> unavailable = new ArrayList<>();
        for (SessionPlayer p : allPlayers) {
            if (isDiagnosticPlayerAvailable(p)) available.add(p);
            else unavailable.add(p);
        }

        available.sort(Comparator
                .comparingInt((SessionPlayer p) -> p.getEnergy()).reversed()
                .thenComparing(SessionPlayer::getSessionPlayerId));

        List<SessionPlayer> newStarters = new ArrayList<>();
        List<SessionPlayer> lowEnergyStarters = new ArrayList<>();

        for (SessionPlayer p : available) {
            if (newStarters.size() >= 11) break;
            if (p.getEnergy() <= 20) lowEnergyStarters.add(p);
            else newStarters.add(p);
        }

        if (newStarters.size() < 11) {
            int needed = 11 - newStarters.size();
            int take = Math.min(needed, lowEnergyStarters.size());
            for (int i = 0; i < take; i++) newStarters.add(lowEnergyStarters.get(i));
        }

        if (newStarters.size() < 11) {
            int needed = 11 - newStarters.size();
            unavailable.sort(Comparator.comparingInt((SessionPlayer p) -> p.getEnergy()).reversed());
            int take = Math.min(needed, unavailable.size());
            for (int i = 0; i < take; i++) newStarters.add(unavailable.get(i));
        }

        List<String> newStarterIds = newStarters.stream()
                .map(SessionPlayer::getSessionPlayerId)
                .collect(Collectors.toList());
        career.getTeamStarting11().put(teamId, newStarterIds);
    }

    private int maybeRotateDueToEnergy(CareerSave career, String teamId) {
        List<SessionPlayer> currentXI = getCurrentStartingXI(career, teamId);
        if (currentXI.isEmpty()) return 0;

        int minStarterEnergy = currentXI.stream()
                .mapToInt(SessionPlayer::getEnergy).min().orElse(100);

        if (minStarterEnergy >= 30) return 0;

        Set<String> starterIds = currentXI.stream()
                .map(SessionPlayer::getSessionPlayerId)
                .collect(Collectors.toSet());

        int bestBenchEnergy = 0;
        for (SessionPlayer p : getAllPlayers(career)) {
            if (!starterIds.contains(p.getSessionPlayerId()) && isDiagnosticPlayerAvailable(p)) {
                if (p.getEnergy() > bestBenchEnergy) bestBenchEnergy = p.getEnergy();
            }
        }

        if (bestBenchEnergy > minStarterEnergy + 15) {
            SessionPlayer lowestStarter = currentXI.stream()
                    .min(Comparator.comparingInt(SessionPlayer::getEnergy))
                    .orElse(null);
            SessionPlayer bestBenchPlayer = null;
            for (SessionPlayer p : getAllPlayers(career)) {
                if (!starterIds.contains(p.getSessionPlayerId()) && isDiagnosticPlayerAvailable(p)) {
                    if (p.getEnergy() == bestBenchEnergy) {
                        bestBenchPlayer = p;
                        break;
                    }
                }
            }

            if (lowestStarter != null && bestBenchPlayer != null) {
                List<String> currentStarterIds = new ArrayList<>(career.getTeamStarting11().get(teamId));
                int idx = currentStarterIds.indexOf(lowestStarter.getSessionPlayerId());
                if (idx >= 0) {
                    currentStarterIds.set(idx, bestBenchPlayer.getSessionPlayerId());
                    career.getTeamStarting11().put(teamId, currentStarterIds);
                    return 1;
                }
            }
        }
        return 0;
    }

    // ========================================================================
    // SHARED SANITY DIAGNOSTICS (synthetic — invariant bounds only)
    // ========================================================================

    /**
     * Sanity diagnostic: verify energy stays within [0, 100] for all players across rounds.
     * Uses minimal synthetic provider — this is NOT representative of real energy drain.
     */
    @Test
    void diagnostic_syntheticInvariant_energyStaysWithinBounds() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, true, true, true,
                new MinimalDeterministicEngine()
        );

        CareerSave career = makeCareer(HOME, AWAY, HOME, AWAY, 11, 11);
        career.getData().setCareerId("energy_bounds_" + System.currentTimeMillis());
        career.getSeasonManager().setCurrentSeason(1);

        List<MatchFixture> fixtures = new ArrayList<>();
        for (int r = 1; r <= 20; r++) {
            fixtures.add(new MatchFixture("e_r" + r, HOME, AWAY, r));
        }
        career.setTournamentState(makeTournamentState(fixtures));

        for (int round = 1; round <= 20; round++) {
            simulator.simulateLeagueRound(career, round);

            for (SessionPlayer p : getAllPlayers(career)) {
                assertTrue(p.getEnergy() >= 0,
                        "Energy must not be negative: " + p.getSessionPlayerId() + " at round " + round);
                assertTrue(p.getEnergy() <= 100,
                        "Energy must not exceed 100: " + p.getSessionPlayerId() + " at round " + round);
            }
        }
    }

    /**
     * Sanity diagnostic: verify form stays within [1, 99] for all players across rounds.
     * Uses minimal synthetic provider — this is NOT representative of real form movement.
     */
    @Test
    void diagnostic_syntheticInvariant_formStaysWithinBounds() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, true, true, true,
                new MinimalDeterministicEngine()
        );

        CareerSave career = makeCareer(HOME, AWAY, HOME, AWAY, 11, 11);
        career.getData().setCareerId("form_bounds_" + System.currentTimeMillis());
        career.getSeasonManager().setCurrentSeason(1);

        List<MatchFixture> fixtures = new ArrayList<>();
        for (int r = 1; r <= 20; r++) {
            fixtures.add(new MatchFixture("f_r" + r, HOME, AWAY, r));
        }
        career.setTournamentState(makeTournamentState(fixtures));

        for (int round = 1; round <= 20; round++) {
            simulator.simulateLeagueRound(career, round);

            for (SessionPlayer p : getAllPlayers(career)) {
                assertTrue(p.getForm() >= 1 && p.getForm() <= 99,
                        "Form must be in [1,99]: " + p.getSessionPlayerId()
                                + " at round " + round + " = " + p.getForm());
            }
        }
    }

    /**
     * Sanity diagnostic: verify injury/suspension remaining counts never go negative.
     * Uses minimal synthetic provider — invariant only, not a balancing measurement.
     */
    @Test
    void diagnostic_syntheticInvariant_remainingCountsNeverNegative() {
        FakeMatchSimulator fakeSim = new FakeMatchSimulator();
        FakeStoragePort fakeStorage = new FakeStoragePort();

        LeagueSimulator simulator = new LeagueSimulator(
                fakeSim, null, false, true, false, fakeStorage,
                true, true, true, true, true,
                new MinimalDeterministicEngine()
        );

        CareerSave career = makeCareer(HOME, AWAY, HOME, AWAY, 11, 11);
        career.getData().setCareerId("remaining_noneg_" + System.currentTimeMillis());
        career.getSeasonManager().setCurrentSeason(1);

        List<MatchFixture> fixtures = new ArrayList<>();
        for (int r = 1; r <= 20; r++) {
            fixtures.add(new MatchFixture("r_r" + r, HOME, AWAY, r));
        }
        career.setTournamentState(makeTournamentState(fixtures));

        for (int round = 1; round <= 20; round++) {
            simulator.simulateLeagueRound(career, round);

            for (SessionPlayer p : getAllPlayers(career)) {
                if (p.getInjuryRemainingMatches() != null) {
                    assertTrue(p.getInjuryRemainingMatches() >= 0,
                            "Injury remaining must not be negative: "
                                    + p.getSessionPlayerId() + " at round " + round);
                }
                if (p.getSuspensionRemainingMatches() != null) {
                    assertTrue(p.getSuspensionRemainingMatches() >= 0,
                            "Suspension remaining must not be negative: "
                                    + p.getSessionPlayerId() + " at round " + round);
                }
            }
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Returns unique player IDs who are currently unavailable
     * (injured OR suspended, counting each player once even if both).
     */
    private int countUniqueUnavailable(CareerSave career) {
        Set<String> unavailable = new java.util.HashSet<>();
        for (SessionPlayer p : getAllPlayers(career)) {
            if (Boolean.TRUE.equals(p.getInjured()) ||
                    (p.getInjuryRemainingMatches() != null && p.getInjuryRemainingMatches() > 0) ||
                    Boolean.TRUE.equals(p.getSuspended()) ||
                    (p.getSuspensionRemainingMatches() != null && p.getSuspensionRemainingMatches() > 0)) {
                unavailable.add(p.getSessionPlayerId());
            }
        }
        return unavailable.size();
    }

    /**
     * Returns the set of player IDs who are currently injured.
     */
    private Set<String> getCurrentlyInjuredPlayerIds(CareerSave career) {
        Set<String> ids = new java.util.HashSet<>();
        for (SessionPlayer p : getAllPlayers(career)) {
            if (Boolean.TRUE.equals(p.getInjured()) ||
                    (p.getInjuryRemainingMatches() != null && p.getInjuryRemainingMatches() > 0)) {
                ids.add(p.getSessionPlayerId());
            }
        }
        return ids;
    }

    /**
     * Returns the set of player IDs who are currently suspended.
     */
    private Set<String> getCurrentlySuspendedPlayerIds(CareerSave career) {
        Set<String> ids = new java.util.HashSet<>();
        for (SessionPlayer p : getAllPlayers(career)) {
            if (Boolean.TRUE.equals(p.getSuspended()) ||
                    (p.getSuspensionRemainingMatches() != null && p.getSuspensionRemainingMatches() > 0)) {
                ids.add(p.getSessionPlayerId());
            }
        }
        return ids;
    }

    private int countCurrentlyInjured(CareerSave career) {
        return getCurrentlyInjuredPlayerIds(career).size();
    }

    private int countCurrentlySuspended(CareerSave career) {
        return getCurrentlySuspendedPlayerIds(career).size();
    }

    private int countUnavailable(CareerSave career) {
        // Legacy alias — use countUniqueUnavailable for new code
        return countUniqueUnavailable(career);
    }

    private int countAllPlayers(CareerSave career) {
        return getAllPlayers(career).size();
    }

    private List<SessionPlayer> getAllPlayers(CareerSave career) {
        return new ArrayList<>(career.getSessionPlayers().values());
    }

    private double computeAverageEnergy(CareerSave career) {
        List<SessionPlayer> players = getAllPlayers(career);
        if (players.isEmpty()) return 0;
        int sum = 0;
        for (SessionPlayer p : players) sum += p.getEnergy();
        return (double) sum / players.size();
    }

    private double computeMinEnergy(CareerSave career) {
        return getAllPlayers(career).stream()
                .mapToInt(SessionPlayer::getEnergy).min().orElse(0);
    }

    private double computeMaxEnergy(CareerSave career) {
        return getAllPlayers(career).stream()
                .mapToInt(SessionPlayer::getEnergy).max().orElse(0);
    }

    private double computeAverageForm(CareerSave career) {
        List<SessionPlayer> players = getAllPlayers(career);
        if (players.isEmpty()) return 0;
        int sum = 0;
        for (SessionPlayer p : players) sum += (p.getForm() != null ? p.getForm() : 50);
        return (double) sum / players.size();
    }

    private int computeMinForm(CareerSave career) {
        return getAllPlayers(career).stream()
                .mapToInt(p -> p.getForm() != null ? p.getForm() : 50).min().orElse(50);
    }

    private int computeMaxForm(CareerSave career) {
        return getAllPlayers(career).stream()
                .mapToInt(p -> p.getForm() != null ? p.getForm() : 50).max().orElse(50);
    }

    private int countPlayersAboveEnergy(CareerSave career, int threshold) {
        int count = 0;
        for (SessionPlayer p : getAllPlayers(career)) {
            if (p.getEnergy() > threshold) count++;
        }
        return count;
    }

    private int countPlayersBelowEnergy(CareerSave career, int threshold) {
        int count = 0;
        for (SessionPlayer p : getAllPlayers(career)) {
            if (p.getEnergy() < threshold) count++;
        }
        return count;
    }

    // ========================================================================
    // Factory Methods
    // ========================================================================

    private static CareerSave makeCareer(String homeTeamId, String awayTeamId,
                                          String homeStartingTeamId, String awayStartingTeamId,
                                          int homeStarterCount, int awayStarterCount) {
        CareerSave save = new CareerSave();
        save.getData().setCareerId(homeTeamId + "_" + awayTeamId);
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();

        for (String tid : List.of(homeTeamId, awayTeamId)) {
            UUID uuid = UUID.fromString(tid);
            SessionTeam team = SessionTeam.fromRealTeam(uuid, "world_" + tid,
                    "Team " + tid, "Country", BigDecimal.ZERO, "4-3-3", null);
            team.setSessionTeamId(tid);
            tm.addSessionTeam(team);
        }

        List<SessionPlayer> homePlayers = new ArrayList<>();
        for (int i = 0; i < homeStarterCount; i++) {
            SessionPlayer p = SessionPlayer.custom("p_" + homeTeamId + "_" + i, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setEnergy(85);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(p.getSessionPlayerId(), homeTeamId);
            homePlayers.add(p);
        }

        List<SessionPlayer> awayPlayers = new ArrayList<>();
        for (int i = 0; i < awayStarterCount; i++) {
            SessionPlayer p = SessionPlayer.custom("p_" + awayTeamId + "_" + i, 25, "MID",
                    75, 75, 75, 75, 75, 75, BigDecimal.valueOf(1000));
            p.setEnergy(85);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(p.getSessionPlayerId(), awayTeamId);
            awayPlayers.add(p);
        }

        save.setTeamManager(tm);
        save.setPlayerManager(pm);

        List<String> homeStarterIds = new ArrayList<>();
        for (int i = 0; i < homeStarterCount; i++) homeStarterIds.add(homePlayers.get(i).getSessionPlayerId());
        List<String> awayStarterIds = new ArrayList<>();
        for (int i = 0; i < awayStarterCount; i++) awayStarterIds.add(awayPlayers.get(i).getSessionPlayerId());
        save.getTeamStarting11().put(homeStartingTeamId, homeStarterIds);
        save.getTeamStarting11().put(awayStartingTeamId, awayStarterIds);

        save.setTournamentState(new TournamentState());
        return save;
    }

    private static TournamentState makeTournamentState(List<MatchFixture> fixtures) {
        TournamentState ts = new TournamentState();
        ts.setCurrentRound(1);
        for (MatchFixture f : fixtures) ts.getFixtures().add(f);
        return ts;
    }

    // ========================================================================
    // Fake Classes
    // ========================================================================

    private static class FakeMatchSimulator implements MatchSimulator {
        boolean simulateQuickCalled = false;

        @Override
        public MatchState simulateReal(MatchState state, int toMinute) {
            return state;
        }

        @Override
        public MatchResult simulateQuick(String homeTeamId, String awayTeamId, int homeOvr, int awayOvr) {
            simulateQuickCalled = true;
            return new MatchResult(1, 1);
        }
    }

    private static class FakeStoragePort implements V24DetailedMatchStoragePort {
        boolean saveCalled = false;

        @Override
        public void save(String careerId, V24DetailedMatchData detail) {
            saveCalled = true;
        }

        @Override
        public java.util.Optional<V24DetailedMatchData> findByMatchId(String careerId, String matchId) {
            return java.util.Optional.empty();
        }

        @Override
        public void deleteByCareerId(String careerId) {
        }
    }

    /**
     * Minimal deterministic engine — NOT representative of real football.
     *
     * Produces low-frequency events intentionally:
     * - Yellow card: ~1 per 7 matches
     * - No injuries
     * - Uniform ratings → neutral form delta
     *
     * Use ONLY for invariant/sanity bounds testing.
     * Do NOT use for balancing baseline measurements.
     */
    private static class MinimalDeterministicEngine implements V24DetailedMatchEngineProvider {
        private final long seed;

        MinimalDeterministicEngine() {
            this.seed = 42L;
        }

        @Override
        public V24DetailedMatchResult simulate(V24MatchContext context, long seed) {
            long effectiveSeed = seed ^ this.seed;

            int homeGoals = (int) ((effectiveSeed % 3L) + 1L);
            int awayGoals = (int) (((effectiveSeed >> 4) % 3L));

            V24MatchTimeline timeline = new V24MatchTimeline();

            // Occasionally emit a yellow card (seed-dependent, ~1 per 7 matches)
            if ((effectiveSeed % 7L) == 0) {
                List<SessionPlayer> homeStarters = context.homeStartingPlayers();
                if (!homeStarters.isEmpty()) {
                    int playerIdx = (int) (effectiveSeed % homeStarters.size());
                    SessionPlayer player = homeStarters.get(playerIdx);
                    timeline.addEvent(new V24MatchEvent(
                            (int) (30 + (effectiveSeed % 30)),
                            V24MatchEventType.YELLOW_CARD,
                            context.homeTeamId(),
                            player.getSessionPlayerId(),
                            player.getName(),
                            null, null, 0.0, "Foul"));
                }
            }

            return V24DetailedMatchResult.builder()
                    .matchId(context.matchId())
                    .homeTeamId(context.homeTeamId())
                    .awayTeamId(context.awayTeamId())
                    .homeGoals(homeGoals)
                    .awayGoals(awayGoals)
                    .homeXg(0.8 + (effectiveSeed % 10) * 0.1)
                    .awayXg(0.5 + ((effectiveSeed >> 3) % 10) * 0.1)
                    .homeShots(4 + (int) (effectiveSeed % 5))
                    .awayShots(3 + (int) ((effectiveSeed >> 2) % 4))
                    .homePossession(48 + (int) (effectiveSeed % 10))
                    .awayPossession(52 - (int) (effectiveSeed % 10))
                    .timeline(timeline)
                    .summary("Synthetic deterministic match")
                    .build();
        }
    }
}