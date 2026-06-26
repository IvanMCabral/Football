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
}
