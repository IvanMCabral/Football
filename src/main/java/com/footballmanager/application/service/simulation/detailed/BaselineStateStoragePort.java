package com.footballmanager.application.service.simulation.detailed;

import java.util.List;
import java.util.Optional;

import reactor.core.publisher.Mono;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;

/**
 * {@link BaselineState} snapshots captured at detailed match start.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Persisting the baseline state with a 7-day TTL.</li>
 *   <li>Idempotent overwrite on subsequent {@code save} calls (same
 *       {@code careerId, matchId} key).</li>
 *   <li>Atomic per-match delete on match finish (see
 *       {@link #delete(String, String)}).</li>
 * </ul>
 *
 * purpose: the keys, TTLs, and lifecycles are different. Sharing the port
 * would couple the two concerns.
 *
 * {@link Mono} so the adapter can apply reactive retry + read-after-write
 * without spawning a blocking executor. The previous {@code void} signature
 * swallowed timeouts silently via {@code CompletableFuture.get(5s)}, causing
 * baselines to never persist under load and {@code GET /compare} to 404.
 *
 * <p>{@link #findByMatchId} remains blocking/Optional because the read path
 * is a single Redis GET — wrapping it in Mono would force every consumer
 * (including the synchronous {@code MatchComparisonService}) into async
 * without any benefit.
 */
public interface BaselineStateStoragePort {

    /**
     * Persist a baseline-state snapshot.
     * Idempotent: subsequent calls for the same (careerId, matchId) overwrite
     * with the new value. The {@code createdAt} field of the stored value is
     * preserved (the first write wins for createdAt).
     *
     * <p>Returns a {@link Mono} that emits when the baseline is durably stored
     * (after retry + read-after-write validation) or errors with
     * {@link BaselinePersistenceException} if the Redis write fails after all
     * retries. Call sites that should not block the live match should
     * {@code .onErrorResume(...)} and log a warning.
     *
     * @param careerId  the career this match belongs to
     * @param state     the baseline state snapshot
     * @return Mono that completes when the baseline is persisted, or errors
     *         with {@link BaselinePersistenceException} on persistent failure
     */
    Mono<Void> save(String careerId, BaselineState state);

    default Mono<Void> saveWithContext(CareerWriteContext context, BaselineState state) {
        return save(context.careerId(), state);
    }

/**
     * Retrieve the baseline state for a match.
     *
     * is called from Reactor non-blocking threads (parallel-N). The
     * previous {@code Optional<BaselineState>} return type FORCED the
     * adapter to use {@code .blockOptional(...)} which threw
     * {@code IllegalStateException("blockOptional() is blocking, which
     * is not supported in thread parallel-N")} on every call from a
     * Reactor parallel scheduler — silently caught and converted to
     * {@code Optional.empty()}, making the /compare endpoint 404 even
     * when the baseline existed in Redis.
     *
     * <p>Fix: return {@code Mono<Optional<BaselineState>>} so the call
     * composes correctly with the Reactor scheduler. Empty {@code Mono}
     * means Redis failure; present {@code Mono<Optional.empty()>} means
     * "not found in Redis"; present {@code Mono<Optional.of(state)>}
     * means "found".
     *
     * @param careerId  the career the match belongs to
     * @param matchId   the match identifier
     * @return Mono emitting Optional.of(state) on hit, Optional.empty() on miss
     * @throws IllegalArgumentException if careerId or matchId is null
     */
    Mono<Optional<BaselineState>> findByMatchId(String careerId, String matchId);

    /**
     * Delete the baseline state for a single match. Called from
     * {@code RoundController.handleMatchFinished} after the
     *
     * <p>Implementations should be a no-op if the key does not exist
     * (Redis {@code DEL} is naturally idempotent).
     *
     * @param careerId  the career this match belongs to
     * @param matchId   the match identifier
     * @return Mono that completes when the delete is acknowledged by Redis,
     *         or errors with {@link BaselinePersistenceException} on
     *         persistent failure
     */
    Mono<Void> delete(String careerId, String matchId);

    /**
     * Delete all baseline states for a given career. Typically called when
     * a career is deleted. Uses a Redis KEYS scan, acceptable for
     * small-to-medium-sized careers.
     *
     * @param careerId  the career whose baselines to delete
     * @return Mono that emits the number of keys deleted, or errors with
     *         {@link BaselinePersistenceException} on persistent failure
     */
    Mono<Long> deleteByCareerId(String careerId);
}
