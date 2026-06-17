package com.footballmanager.domain.port.in.match;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * LIVE-MATCH-F1-POC: port-in interface for manual substitutions during a live match.
 *
 * <p>Phase 1 POC: manual substitutions are UI-only and do NOT alter the match result
 * (per D1=B decision). The substitution is recorded in the live session's
 * {@code accumulatedEvents} and the {@code V24PlayerMatchState} objects are mutated
 * (substituteOn/substituteOff) for downstream consumers (animation, stats display),
 * but {@code homeGoals}/{@code awayGoals} are NOT recalculated.
 *
 * <p>The actual engine refactor that would let user actions affect the result is
 * deferred to Phase 2.
 *
 * <p>FLAG 1 UX fix: the use case returns a {@link SubstitutionResult} (never
 * {@link Mono#empty()}) so the controller can forward real
 * {@code substitutionsRemaining} and {@code minuteApplied} to the frontend.
 * Validation failures (player already subbed, max subs reached, player not
 * found, etc.) are caught internally and returned as
 * {@link SubstitutionResult#failure(String)}; the Mono never errors on those
 * validation paths. Only infrastructure-level errors (DB outage, NPE, etc.)
 * propagate as {@code Mono.error}.
 *
 * <p>The controller/error layer translates remaining errors into HTTP statuses:
 * <ul>
 *   <li>Request-shape errors (invalid UUID path, blank playerOffId/playerOnId)
 *       → 400 BAD_REQUEST (controller-level validation, body is still a
 *       {@code SubstitutionResultDTO} with {@code success=false, error=...}).</li>
 *   <li>Use case validation failures → 200 OK with
 *       {@code SubstitutionResultDTO(success=false, error=...)} (FLAG 1 fix).</li>
 *   <li>Unexpected errors (NPE, DB, etc.) → 500 via the global error handler.</li>
 * </ul>
 */
public interface SubstitutionCommandUseCase {

    /**
     * Record a manual substitution for the given match.
     *
     * @param userId       authenticated userId (validated by the controller via
     *                     {@code ControllerHelper.getUserId})
     * @param matchId      the match UUID
     * @param teamId       teamId performing the substitution (home or away); may be
     *                     {@code null} or blank to be inferred from {@code playerOffId}
     * @param playerOffId  sessionPlayerId of the player being substituted off
     * @param playerOnId   sessionPlayerId of the player being substituted on (from bench)
     * @param minute       the match minute when the substitution happens; if
     *                     {@code null}, the use case uses the live session's current
     *                     minute as the authoritative source
     * @return {@code Mono<SubstitutionResult>} with {@code success=true} on success,
     *         or {@code success=false} with an {@code error} message on validation
     *         failure. The Mono itself only errors on infrastructure failures.
     */
    Mono<SubstitutionResult> executeSubstitution(
        UUID userId,
        UUID matchId,
        String teamId,
        String playerOffId,
        String playerOnId,
        Integer minute
    );
}
