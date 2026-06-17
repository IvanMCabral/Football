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
 * <p>Pattern follows {@link PauseMatchUseCase}/{@link ResumeMatchUseCase}/
 * {@link StopMatchUseCase}: {@code Mono<Void>} return + reactive signature.
 *
 * <p>The controller/error layer translates domain errors into HTTP statuses:
 * <ul>
 *   <li>IllegalArgumentException → 400 BAD_REQUEST</li>
 *   <li>IllegalStateException → 409 CONFLICT</li>
 * </ul>
 */
public interface SubstitutionCommandUseCase {

    /**
     * Record a manual substitution for the given match.
     *
     * @param userId       authenticated userId (validated by the controller via
     *                     {@code ControllerHelper.getUserId})
     * @param matchId      the match UUID
     * @param teamId       teamId performing the substitution (home or away)
     * @param playerOffId  sessionPlayerId of the player being substituted off
     * @param playerOnId   sessionPlayerId of the player being substituted on (from bench)
     * @param minute       the match minute when the substitution happens (validated by the
     *                     service against V24LiveSession.currentMinute)
     * @return {@code Mono<Void>} on success; {@code Mono.error(IllegalArgumentException)}
     *         for invalid input; {@code Mono.error(IllegalStateException)} for business rule
     *         violations (max subs reached, player already subbed, etc.)
     */
    Mono<Void> executeSubstitution(
        UUID userId,
        UUID matchId,
        String teamId,
        String playerOffId,
        String playerOnId,
        Integer minute
    );
}
