package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V24D20-TESTHARNESS — UseCase for the smoke-test harness.
 *
 * <p>Provides deterministic state mutations required by REVISOR to run
 * controlled comparative smokes (Bloque A: same rival × N formations,
 * Bloque B: same rival × same formation × N runs).
 *
 * <p><b>Profile-gated:</b> only invoked via {@code TestHarnessController}
 * which is {@code @Profile({"dev","local","test"})}. Never wired into
 * production flows.
 *
 * <p><b>Mutations:</b>
 * <ul>
 *   <li>{@link #replaceFixtures} — overwrite {@code tournamentState.fixtures}
 *       with caller-provided list (custom round-robin for smoke flows)</li>
 *   <li>{@link #resetInjuries} — clear injured/suspended/yellow/red flags
 *       on the entire squad (deterministic "squad sano")</li>
 *   <li>{@link #setFormation} — change the user team's formation, persisting
 *       to BOTH {@code SessionTeam.formation} AND {@code teamStarting11Formation}
 *       map (the V24 engine reads from the latter — sprint 1.7 regression
 *       fix).</li>
 *   <li>{@link #createCustom} — wipe + re-create career with caller-provided
 *       league/team/difficulty/gameSpeed/teamsPerDivision; guarantees squad
 *       is healthy on creation.</li>
 *   <li>{@link #snapshot} — return the current {@link CareerSave} so the
 *       caller can build a debug dump.</li>
 * </ul>
 */
public interface TestHarnessUseCase {

    Mono<Void> replaceFixtures(UUID userId, List<CustomFixture> fixtures);

    Mono<Void> resetInjuries(UUID userId);

    Mono<Void> setFormation(UUID userId, String formation);

    // V25D28: change the user team's tactical style (BALANCED/ATTACKING/DEFENSIVE/COUNTER/POSSESSION).
    // Persists to SessionTeam.style; engine reads it via V24MatchContextFactory.build() in replay path.
    Mono<Void> setStyle(UUID userId, TeamStyle style);

    // V25D29: mutate SessionPlayer stats (attack/defense/technique/speed/stamina/mentality)
    // for a specific player in the current career. Null fields are left unchanged.
    // Persists to Redis; engine reads updated stats on next replay via aggregateAttackerStat /
    // aggregateDefenderStat which feed formationOffensiveModifier / formationDefensiveModifier.
    //
    // V25D35: extended with two optional fields for the V25D31 physical + skill metadata:
    //   - heightCm: nullable, [160, 210]; null = leave unchanged (matches SessionPlayer.setHeightCm).
    //   - skillLevels: nullable sparse Map<PlayerSkill, Integer>; each entry bounds-checked to
    //     [0, 99]; null OR empty = no-op (does NOT clear existing skills). Setting a skill
    //     to 0 via the map removes that entry from SessionPlayer's sparse skill map (matches
    //     SessionPlayer.setSkillLevel(skill, 0) semantics).
    //
    // Backward-compat: callers built against V25D29 (only 6 stats) keep working bit-a-bit
    // when heightCm and skillLevels are null — the UseCase treats both as "leave unchanged".
    Mono<Void> injectPlayerStats(UUID userId, String playerId,
                                 Integer attack, Integer defense,
                                 Integer technique, Integer speed,
                                 Integer stamina, Integer mentality,
                                 Integer heightCm,
                                 Map<PlayerSkill, Integer> skillLevels);

    Mono<CareerSave> createCustom(UUID userId, String worldLeagueId, String worldTeamId,
                                  String difficulty, String gameSpeed, int teamsPerDivision);

    Mono<CareerSave> snapshot(UUID userId);

    /**
     * V24D20-SANDBOX-V2-MVP: Re-simulate a single match with a new
     * (caller-provided or auto-generated) seed. Resets the fixture to
     * PENDING, re-runs the V24 engine, persists the new result, and
     * updates the CareerSave + invalidates the cache.
     *
     * <p>Used by REVISOR to run "what-if" experiments without creating
     * a new fixture (the matchId is preserved).
     *
     * @param userId       the career owner
     * @param matchId      the match to replay (must exist in the current
     *                     tournament fixtures)
     * @param seedOverride optional seed; if null, {@code System.currentTimeMillis()}
     *                     is used (non-deterministic across runs)
     * @return the updated {@link MatchFixture} with the new result
     */
    Mono<MatchFixture> replayMatch(UUID userId, String matchId, Long seedOverride);

    Mono<List<FormationMatrixRow>> runFormationMatrix(UUID userId, String matchId, Long seedOverride);

    Mono<List<FormationMatrixSummaryRow>> runFormationMatrixSummary(UUID userId, String matchId, long seedStart, int seedCount);

    Mono<List<ScenarioMatrixRow>> runScenarioMatrix(UUID userId, String matchId, Long seedOverride);

    Mono<List<ScenarioMatrixSummaryRow>> runScenarioMatrixSummary(UUID userId, String matchId, long seedStart, int seedCount, String scenarioGroup, String controlledTeamSide);

    Mono<PlayerSwapMatrixSummaryRow> runPlayerSwapMatrixSummary(
        UUID userId,
        String matchId,
        String starterPlayerId,
        String benchPlayerId,
        String slotId,
        long seedStart,
        int seedCount);

    Mono<PositionPixelMatrixSummaryRow> runPositionPixelMatrixSummary(
        UUID userId,
        String matchId,
        String playerId,
        Double targetXPercent,
        Double targetYPercent,
        long seedStart,
        int seedCount);

    Mono<LabMutationResult> prepareOffensiveUpgradeLab(UUID userId);

    Mono<LabMutationResult> restoreOffensiveUpgradeLab(UUID userId);

    Mono<LabMutationResult> prepareDefensiveDowngradeLab(UUID userId);

    Mono<LabMutationResult> restoreDefensiveDowngradeLab(UUID userId);

    Mono<LabMutationResult> prepareWeakWideDefendersLab(UUID userId);

    Mono<LabMutationResult> restoreWeakWideDefendersLab(UUID userId);

    Mono<LabMutationResult> prepareOpponentWeakWideDefendersLab(UUID userId, String matchId);

    Mono<LabMutationResult> restoreOpponentWeakWideDefendersLab(UUID userId, String matchId);

    Mono<LabMutationResult> prepareOpponentWeakLeftDefenderLab(UUID userId, String matchId);

    Mono<LabMutationResult> restoreOpponentWeakLeftDefenderLab(UUID userId, String matchId);

    Mono<LabMutationResult> prepareOpponentWeakRightDefenderLab(UUID userId, String matchId);

    Mono<LabMutationResult> restoreOpponentWeakRightDefenderLab(UUID userId, String matchId);

    Mono<LabMutationResult> prepareOpponentWeakCenterBacksLab(UUID userId, String matchId);

    Mono<LabMutationResult> restoreOpponentWeakCenterBacksLab(UUID userId, String matchId);

    Mono<LabMutationResult> prepareWeakLeftDefenderLab(UUID userId);

    Mono<LabMutationResult> restoreWeakLeftDefenderLab(UUID userId);

    Mono<LabMutationResult> prepareWeakRightDefenderLab(UUID userId);

    Mono<LabMutationResult> restoreWeakRightDefenderLab(UUID userId);

    Mono<LabMutationResult> prepareWeakCenterBacksLab(UUID userId);

    Mono<LabMutationResult> restoreWeakCenterBacksLab(UUID userId);

    /**
     * V24D24.3-HOTFIX: Reset every fixture of a round back to PENDING,
     * clear its {@link MatchFixture.MatchResultData}, remove the cached
     * {@code MatchSession} from {@code MatchEngineRegistry} so the next
     * {@code /match-engine/rounds/start} call creates a fresh engine
     * (the previous engine returned its cached session and the V24
     * simulation never re-ran — see
     * {@code MatchEngineRegistry.startEngine} line 25-30 and
     * {@code MatchFixture.startSimulation} line 88-93), and clear the
     * V24 detail entries from Redis.
     *
     * <p>Designed to be called by the test-harness frontend RIGHT BEFORE
     * {@code /match-engine/rounds/start} so {@code "Simulate round"} is
     * idempotent — same input (roundId, formations, seed) always
     * produces a fresh simulation rather than re-reading the cached
     * result.
     *
     * @param userId  the career owner
     * @param roundId deterministic round UUID (matches what
     *                {@code FixtureQueryHelper.deriveRoundId} produced)
     * @return a Mono completing when the reset is persisted; emits an
     *         error if no fixture of the round is in COMPLETED state
     *         (nothing to reset) or the round is unknown.
     */
    Mono<Void> resetRound(UUID userId, String roundId);

    /**
     * Spec for a custom fixture in {@link #replaceFixtures}.
     *
     * @param matchId optional — if {@code null}, a new UUID is generated
     *               (the typical case for fresh smoke runs).
     */
    record CustomFixture(String homeTeamId, String awayTeamId, int round, String matchId) {
        public CustomFixture {
            if (homeTeamId == null || homeTeamId.isBlank()) {
                throw new IllegalArgumentException("homeTeamId is required");
            }
            if (awayTeamId == null || awayTeamId.isBlank()) {
                throw new IllegalArgumentException("awayTeamId is required");
            }
            if (homeTeamId.equals(awayTeamId)) {
                throw new IllegalArgumentException(
                    "homeTeamId and awayTeamId must differ (got '" + homeTeamId + "')");
            }
            if (round < 1) {
                throw new IllegalArgumentException("round must be >= 1 (got " + round + ")");
            }
        }
    }

    record ScenarioMatrixRow(
        String scenario,
        String description,
        String formation,
        TeamStyle initialStyle,
        Integer changeMinute,
        TeamStyle changedStyle,
        String actionType,
        String actionDetail,
        int homeGoals,
        int awayGoals,
        double homeXg,
        double awayXg,
        int homeShots,
        int awayShots,
        int homePossession,
        int awayPossession,
        int homeCentralShots,
        int homeWideShots,
        int homeLongShots,
        int awayCentralShots,
        int awayWideShots,
        int awayLongShots,
        double homeCentralXg,
        double homeWideXg,
        double homeLongXg,
        int homeLeftWideShots,
        int homeRightWideShots,
        double homeLeftWideXg,
        double homeRightWideXg,
        double awayCentralXg,
        double awayWideXg,
        double awayLongXg,
        int awayLeftWideShots,
        int awayRightWideShots,
        double awayLeftWideXg,
        double awayRightWideXg,
        long tacticalChanges,
        long substitutions
    ) {}

    record FormationMatrixRow(
        String formation,
        int homeGoals,
        int awayGoals,
        double homeXg,
        double awayXg,
        int homeShots,
        int awayShots,
        int homePossession,
        int awayPossession,
        int homeCentralShots,
        int homeWideShots,
        int homeLongShots,
        int awayCentralShots,
        int awayWideShots,
        int awayLongShots,
        double shapePossessionMultiplier,
        double shapeAttackVolumeMultiplier,
        double shapeDefensiveResistanceMultiplier,
        double shapeAttackLeft,
        double shapeAttackCenter,
        double shapeAttackRight,
        double shapeDefenseLeft,
        double shapeDefenseCenter,
        double shapeDefenseRight
    ) {}

    record FormationMatrixSummaryRow(
        String formation,
        long seedStart,
        long seedEnd,
        int seedCount,
        double avgGoalsFor,
        double avgGoalsAgainst,
        double avgGoalDiff,
        double avgPossessionFor,
        double avgShotsFor,
        double avgShotsAgainst,
        double avgShotDiff,
        double avgXgFor,
        double avgXgAgainst,
        double avgXgDiff,
        double avgCentralShotsFor,
        double avgWideShotsFor,
        double avgLongShotsFor,
        double avgCentralShotsAgainst,
        double avgWideShotsAgainst,
        double avgLongShotsAgainst,
        double avgShapePossessionMultiplier,
        double avgShapeAttackVolumeMultiplier,
        double avgShapeDefensiveResistanceMultiplier,
        double avgShapeAttackLeft,
        double avgShapeAttackCenter,
        double avgShapeAttackRight,
        double avgShapeDefenseLeft,
        double avgShapeDefenseCenter,
        double avgShapeDefenseRight
    ) {}

    record ScenarioMatrixSummaryRow(
        String scenario,
        String actionType,
        String actionDetail,
        int seedCount,
        double avgUserXgDelta,
        double minUserXgDelta,
        double maxUserXgDelta,
        double avgOpponentXgDelta,
        double avgUserShotsDelta,
        double avgOpponentShotsDelta,
        double avgUserPossessionDelta,
        double avgUserCentralDelta,
        double avgUserWideDelta,
        double avgOpponentCentralDelta,
        double avgOpponentWideDelta,
        double avgUserCentralXgDelta,
        double avgUserWideXgDelta,
        double avgOpponentCentralXgDelta,
        double avgOpponentWideXgDelta,
        double avgUserLeftWideDelta,
        double avgUserRightWideDelta,
        double avgOpponentLeftWideDelta,
        double avgOpponentRightWideDelta,
        double avgUserLeftWideXgDelta,
        double avgUserRightWideXgDelta,
        double avgOpponentLeftWideXgDelta,
        double avgOpponentRightWideXgDelta,
        String baselineScenario
    ) {}

    record PlayerSwapMatrixSummaryRow(
        String matchId,
        String formation,
        String slotId,
        long seedStart,
        long seedEnd,
        int seedCount,
        String baselinePlayerId,
        String baselinePlayerName,
        String baselinePlayerPosition,
        Integer baselinePlayerOverall,
        String swapPlayerId,
        String swapPlayerName,
        String swapPlayerPosition,
        Integer swapPlayerOverall,
        double baselineAvgGoalsFor,
        double baselineAvgGoalsAgainst,
        double baselineAvgGoalDiff,
        double baselineAvgShotsFor,
        double baselineAvgShotsAgainst,
        double baselineAvgPossessionFor,
        double baselineAvgXgFor,
        double baselineAvgXgAgainst,
        double baselineAvgXgDiff,
        double baselineAvgCentralShotsFor,
        double baselineAvgWideShotsFor,
        double baselineAvgLongShotsFor,
        double baselineAvgCentralShotsAgainst,
        double baselineAvgWideShotsAgainst,
        double baselineAvgLongShotsAgainst,
        double baselineAvgCentralXgFor,
        double baselineAvgWideXgFor,
        double baselineAvgLongXgFor,
        double baselineAvgCentralXgAgainst,
        double baselineAvgWideXgAgainst,
        double baselineAvgLongXgAgainst,
        double swappedAvgGoalsFor,
        double swappedAvgGoalsAgainst,
        double swappedAvgGoalDiff,
        double swappedAvgShotsFor,
        double swappedAvgShotsAgainst,
        double swappedAvgPossessionFor,
        double swappedAvgXgFor,
        double swappedAvgXgAgainst,
        double swappedAvgXgDiff,
        double swappedAvgCentralShotsFor,
        double swappedAvgWideShotsFor,
        double swappedAvgLongShotsFor,
        double swappedAvgCentralShotsAgainst,
        double swappedAvgWideShotsAgainst,
        double swappedAvgLongShotsAgainst,
        double swappedAvgCentralXgFor,
        double swappedAvgWideXgFor,
        double swappedAvgLongXgFor,
        double swappedAvgCentralXgAgainst,
        double swappedAvgWideXgAgainst,
        double swappedAvgLongXgAgainst,
        double deltaGoalsFor,
        double deltaGoalsAgainst,
        double deltaGoalDiff,
        double deltaShotsFor,
        double deltaShotsAgainst,
        double deltaPossessionFor,
        double deltaXgFor,
        double deltaXgAgainst,
        double deltaXgDiff,
        double deltaCentralShotsFor,
        double deltaWideShotsFor,
        double deltaLongShotsFor,
        double deltaCentralShotsAgainst,
        double deltaWideShotsAgainst,
        double deltaLongShotsAgainst,
        double deltaCentralXgFor,
        double deltaWideXgFor,
        double deltaLongXgFor,
        double deltaCentralXgAgainst,
        double deltaWideXgAgainst,
        double deltaLongXgAgainst,
        double preAutoSubDeltaShotsFor,
        double preAutoSubDeltaShotsAgainst,
        double preAutoSubDeltaXgFor,
        double preAutoSubDeltaXgAgainst,
        double preAutoSubDeltaXgDiff
    ) {}

    record PositionPixelMatrixSummaryRow(
        String matchId,
        String formation,
        String playerId,
        String playerName,
        String playerPosition,
        String slotId,
        double fromXPercent,
        double fromYPercent,
        double targetXPercent,
        double targetYPercent,
        long seedStart,
        long seedEnd,
        int seedCount,
        double baselineAvgGoalsFor,
        double baselineAvgGoalsAgainst,
        double baselineAvgGoalDiff,
        double baselineAvgShotsFor,
        double baselineAvgShotsAgainst,
        double baselineAvgPossessionFor,
        double baselineAvgXgFor,
        double baselineAvgXgAgainst,
        double baselineAvgXgDiff,
        double baselineAvgCentralShotsFor,
        double baselineAvgWideShotsFor,
        double baselineAvgLongShotsFor,
        double baselineAvgCentralShotsAgainst,
        double baselineAvgWideShotsAgainst,
        double baselineAvgLongShotsAgainst,
        double baselineAvgCentralXgFor,
        double baselineAvgWideXgFor,
        double baselineAvgLongXgFor,
        double baselineAvgCentralXgAgainst,
        double baselineAvgWideXgAgainst,
        double baselineAvgLongXgAgainst,
        double movedAvgGoalsFor,
        double movedAvgGoalsAgainst,
        double movedAvgGoalDiff,
        double movedAvgShotsFor,
        double movedAvgShotsAgainst,
        double movedAvgPossessionFor,
        double movedAvgXgFor,
        double movedAvgXgAgainst,
        double movedAvgXgDiff,
        double movedAvgCentralShotsFor,
        double movedAvgWideShotsFor,
        double movedAvgLongShotsFor,
        double movedAvgCentralShotsAgainst,
        double movedAvgWideShotsAgainst,
        double movedAvgLongShotsAgainst,
        double movedAvgCentralXgFor,
        double movedAvgWideXgFor,
        double movedAvgLongXgFor,
        double movedAvgCentralXgAgainst,
        double movedAvgWideXgAgainst,
        double movedAvgLongXgAgainst,
        double deltaGoalsFor,
        double deltaGoalsAgainst,
        double deltaGoalDiff,
        double deltaShotsFor,
        double deltaShotsAgainst,
        double deltaPossessionFor,
        double deltaXgFor,
        double deltaXgAgainst,
        double deltaXgDiff,
        double deltaCentralShotsFor,
        double deltaWideShotsFor,
        double deltaLongShotsFor,
        double deltaCentralShotsAgainst,
        double deltaWideShotsAgainst,
        double deltaLongShotsAgainst,
        double deltaCentralXgFor,
        double deltaWideXgFor,
        double deltaLongXgFor,
        double deltaCentralXgAgainst,
        double deltaWideXgAgainst,
        double deltaLongXgAgainst
    ) {}

    record LabMutationResult(
        String labKey,
        String message,
        Map<String, Object> details
    ) {}
}
