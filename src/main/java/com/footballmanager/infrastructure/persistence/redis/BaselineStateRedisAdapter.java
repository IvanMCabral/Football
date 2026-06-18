package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.application.service.simulation.v24.BaselinePersistenceException;
import com.footballmanager.application.service.simulation.v24.BaselineState;
import com.footballmanager.application.service.simulation.v24.BaselineStateStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE): Redis adapter for
 * {@link BaselineStateStoragePort}.
 *
 * <p>Stores baseline snapshots at keys:
 * {@code career:{careerId}:match-baseline:{matchId}}
 *
 * <p>TTL: <b>7 days</b> ({@link #BASELINE_TTL}), aligned with the longest
 * expected delay between match end and "compare" view (7d > typical
 * manager session length). After 7d the baseline expires and the compare
 * endpoint will return 404. The {@code V24DetailedMatchData} for the
 * match has no TTL by design, so 7d is the effective retention ceiling.
 *
 * <p><b>V24D15-CLEANUP (BUG_COMPARE_404):</b> Writes are now fully reactive
 * (Mono + retry + read-after-write). The previous implementation wrapped
 * the blocking Redis I/O in {@code CompletableFuture.runAsync(...).get(5s)}
 * which silently swallowed timeouts and saturated the executor under load,
 * causing baselines to never persist and {@code /compare} to 404.
 *
 * <p>The new pattern:
 * <ol>
 *   <li>Subscribe on {@link Schedulers#boundedElastic()} so the WebFlux
 *       Netty event loop is never blocked.</li>
 *   <li>Apply a bounded exponential backoff retry
 *       (3 attempts, 200ms initial, 2s max) for transient Redis
 *       failures ({@link RedisConnectionFailureException},
 *       {@link QueryTimeoutException}).</li>
 *   <li>After the SET, perform a read-after-write (GET) to confirm the
 *       value landed — protects against ack-without-write races during
 *       cluster failovers.</li>
 *   <li>On persistent failure, propagate as
 *       {@link BaselinePersistenceException} (NO silent swallow). Call
 *       sites log a warn and continue so the live match is not blocked.</li>
 * </ol>
 *
 * <p>The read path ({@link #findByMatchId}) remains blocking/Optional
 * because the GET is a single round-trip and {@code MatchComparisonService}
 * is synchronous — adding Mono here would force unnecessary async churn
 * for a low-latency call (~1-2ms).
 */
@Slf4j
@Repository
public class BaselineStateRedisAdapter implements BaselineStateStoragePort {

    private static final String KEY_PREFIX = "career:";
    private static final String KEY_MATCH_BASELINE = ":match-baseline:";
    /** F6 Sprint 2: 7-day TTL, decided by Iván 2026-06-18. */
    public static final Duration BASELINE_TTL = Duration.ofDays(7);
    /** V24D15-CLEANUP: bounded retry budget for transient Redis errors. */
    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(200);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(2);
    /** V24D15-CLEANUP: hard ceiling on the reactive chain (covers slow retries). */
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(10);

    private final ReactiveRedisTemplate<String, BaselineState> redisTemplate;

    public BaselineStateRedisAdapter(
            @Qualifier("v24MatchBaselineStateRedisTemplate")
            ReactiveRedisTemplate<String, BaselineState> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String buildKey(String careerId, String matchId) {
        return KEY_PREFIX + careerId + KEY_MATCH_BASELINE + matchId;
    }

    private String buildPattern(String careerId) {
        return KEY_PREFIX + careerId + KEY_MATCH_BASELINE + "*";
    }

    /**
     * V24D15-CLEANUP (BUG_COMPARE_404): Reactive save with retry +
     * read-after-write. Errors propagate as
     * {@link BaselinePersistenceException} so callers can decide whether
     * to fail or continue (the compare endpoint will 404 if the baseline
     * never lands).
     */
    @Override
    public Mono<Void> save(String careerId, BaselineState state) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        if (state == null) {
            return Mono.error(new IllegalArgumentException("state must not be null"));
        }
        if (!careerId.equals(state.careerId())) {
            return Mono.error(new IllegalArgumentException(
                    "careerId argument '" + careerId + "' does not match state.careerId '"
                            + state.careerId() + "'"));
        }
        String matchId = state.matchId();
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("state.matchId must not be blank"));
        }
        String key = buildKey(careerId, matchId);

        return redisTemplate.opsForValue()
                .set(key, state, BASELINE_TTL)
                .timeout(WRITE_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .filter(this::isRetryableRedisError)
                        .onRetryExhaustedThrow((spec, signal) ->
                                new BaselinePersistenceException(careerId, matchId, signal.failure())))
                // Read-after-write: confirm the SET actually landed. This
                // catches ack-without-write races during cluster failover
                // (Redis returns OK from SET but the key never reaches a
                // replica in time, then reads from the same connection
                // miss it). One extra GET per save is cheap insurance.
                .flatMap(saved -> redisTemplate.opsForValue()
                        .get(key)
                        .timeout(WRITE_TIMEOUT)
                        // Mono.empty() never invokes doOnNext, so we use
                        // switchIfEmpty to convert a null/missing read
                        // back into an explicit BaselinePersistenceException.
                        .switchIfEmpty(Mono.error(new BaselinePersistenceException(
                                careerId, matchId,
                                new IllegalStateException(
                                        "read-after-write miss for key=" + key))))
                        .retryWhen(Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
                                .maxBackoff(MAX_BACKOFF)
                                .filter(this::isRetryableRedisError))
                        .then())
                .doOnError(e -> log.error(
                        "[F6-BASELINE-REDIS] Failed to save baseline key={}, careerId={}, matchId={}: {}",
                        key, careerId, matchId, e.getMessage()))
                .onErrorMap(e -> (e instanceof BaselinePersistenceException) ? e
                        : new BaselinePersistenceException(careerId, matchId, e))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Optional<BaselineState> findByMatchId(String careerId, String matchId) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        String key = buildKey(careerId, matchId);
        try {
            BaselineState state = redisTemplate.opsForValue()
                    .get(key)
                    .timeout(WRITE_TIMEOUT)
                    .doOnError(e -> log.warn(
                            "[F6-BASELINE-REDIS] Failed to read baseline key={}: {}",
                            key, e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .blockOptional(Duration.ofSeconds(5))
                    .orElse(null);
            return Optional.ofNullable(state);
        } catch (Exception e) {
            log.warn("[F6-BASELINE-REDIS] Failed to read baseline key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Mono<Void> delete(String careerId, String matchId) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId must not be blank"));
        }
        String key = buildKey(careerId, matchId);
        return redisTemplate.delete(key)
                .timeout(WRITE_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .filter(this::isRetryableRedisError))
                .doOnError(e -> log.warn(
                        "[F6-BASELINE-REDIS] Failed to delete baseline key={}: {}", key, e.getMessage()))
                .onErrorMap(e -> new BaselinePersistenceException(careerId, matchId, e))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Long> deleteByCareerId(String careerId) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        String pattern = buildPattern(careerId);
        return redisTemplate.keys(pattern)
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.just(0L);
                    }
                    return redisTemplate.delete(keys.toArray(new String[0]));
                })
                .timeout(WRITE_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .filter(this::isRetryableRedisError))
                .doOnError(e -> log.warn(
                        "[F6-BASELINE-REDIS] Failed to deleteByCareerId for pattern={}: {}",
                        pattern, e.getMessage()))
                .onErrorMap(e -> new BaselinePersistenceException(careerId, "*", e))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * V24D15-CLEANUP (BUG_COMPARE_404): classifier for retryable Redis
     * failures. Connection issues and timeouts are worth retrying; logic
     * errors (e.g. read-after-write miss wrapped in an ISE) are NOT.
     */
    private boolean isRetryableRedisError(Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof BaselinePersistenceException) {
            return false;
        }
        if (t instanceof RedisConnectionFailureException
                || t instanceof QueryTimeoutException
                || t instanceof TimeoutException) {
            return true;
        }
        Throwable cause = t.getCause();
        return cause != null && cause != t && isRetryableRedisError(cause);
    }
}