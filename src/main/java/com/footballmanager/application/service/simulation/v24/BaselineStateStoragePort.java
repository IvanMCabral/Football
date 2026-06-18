package com.footballmanager.application.service.simulation.v24;

import java.util.List;
import java.util.Optional;

import reactor.core.publisher.Mono;

/**
 * F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE): Storage port interface for
 * {@link BaselineState} snapshots captured at V24 match start.
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
 * <p>This is a separate port from {@code V24DetailedMatchStoragePort} on
 * purpose: the keys, TTLs, and lifecycles are different. Sharing the port
 * would couple the two concerns.
 *
 * <p><b>V24D15-CLEANUP (BUG_COMPARE_404):</b> Write methods now return
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

    /**
     * Retrieve the baseline state for a match.
     *
     * @param careerId  the career this match belongs to
     * @param matchId   the match identifier
     * @return Optional containing the baseline state if found, empty otherwise
     * @throws IllegalArgumentException if careerId or matchId is null
     */
    Optional<BaselineState> findByMatchId(String careerId, String matchId);

    /**
     * Delete the baseline state for a single match. Called from
     * {@code RoundController.handleMatchFinished} after the
     * {@code V24DetailedMatchData} for the match has been persisted.
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