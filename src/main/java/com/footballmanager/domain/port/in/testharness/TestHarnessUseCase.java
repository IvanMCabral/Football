package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import reactor.core.publisher.Mono;

import java.util.List;
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
