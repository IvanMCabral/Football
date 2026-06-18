package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.application.service.simulation.v24.BaselineState;
import com.footballmanager.application.service.simulation.v24.BaselineStateStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
 * <p>Uses the same ExecutorService pattern as
 * {@link V24DetailedMatchRedisAdapter} to keep blocking Redis I/O off the
 * WebFlux Netty event loop.
 */
@Slf4j
@Repository
public class BaselineStateRedisAdapter implements BaselineStateStoragePort {

    private static final String KEY_PREFIX = "career:";
    private static final String KEY_MATCH_BASELINE = ":match-baseline:";
    /** F6 Sprint 2: 7-day TTL, decided by Iván 2026-06-18. */
    public static final Duration BASELINE_TTL = Duration.ofDays(7);

    private final ReactiveRedisTemplate<String, BaselineState> redisTemplate;
    private final ExecutorService executor = Executors.newFixedThreadPool(
            4, r -> {
                Thread t = new Thread(r, "v24-baseline-redis-io");
                t.setDaemon(true);
                return t;
            });

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

    @Override
    public void save(String careerId, BaselineState state) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (!careerId.equals(state.careerId())) {
            throw new IllegalArgumentException(
                    "careerId argument '" + careerId + "' does not match state.careerId '"
                            + state.careerId() + "'");
        }
        String matchId = state.matchId();
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("state.matchId must not be blank");
        }
        String key = buildKey(careerId, matchId);
        try {
            CompletableFuture.runAsync(() ->
                    redisTemplate.opsForValue().set(key, state, BASELINE_TTL).block(), executor)
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[F6-BASELINE-REDIS] Failed to save baseline key={}, careerId={}, matchId={}: {}",
                    key, careerId, matchId, e.getMessage());
        }
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
            BaselineState state = CompletableFuture.supplyAsync(() ->
                    redisTemplate.opsForValue().get(key).block(), executor)
                    .get(5, TimeUnit.SECONDS);
            return Optional.ofNullable(state);
        } catch (Exception e) {
            log.warn("[F6-BASELINE-REDIS] Failed to read baseline key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(String careerId, String matchId) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        String key = buildKey(careerId, matchId);
        try {
            CompletableFuture.runAsync(() ->
                    redisTemplate.delete(key).block(), executor)
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[F6-BASELINE-REDIS] Failed to delete baseline key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public void deleteByCareerId(String careerId) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        String pattern = buildPattern(careerId);
        try {
            CompletableFuture.runAsync(() ->
                    redisTemplate.keys(pattern).collectList().flatMap(keys -> {
                        if (keys.isEmpty()) {
                            return Mono.just(0L);
                        }
                        return redisTemplate.delete(keys.toArray(new String[0]));
                    }).block(), executor)
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[F6-BASELINE-REDIS] Failed to deleteByCareerId for pattern={}: {}",
                    pattern, e.getMessage());
        }
    }
}
