package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
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
 * V24D4B: Redis adapter for V24DetailedMatchStoragePort.
 *
 * <p>Stores V24DetailedMatchData snapshots at keys:
 * {@code career:{careerId}:match-detail:{matchId}}
 *
 * <p>V24D6M12: Uses a dedicated ExecutorService with fixed daemon threads
 * to execute blocking Redis I/O outside the WebFlux Netty event loop.
 * Blocking calls are wrapped in CompletableFuture and submitted to the executor,
 * then awaited with get(timeout). This is the only approach that reliably
 * avoids "block() is blocking" violations on Netty event loop threads.
 *
 * <p>The ExecutorService approach is used instead of Schedulers.boundedElastic()
 * because Mono.fromCallable() + subscribeOn() + block() still produced the
 * "thread parallel-X" error in this Netty/R2DBC context, despite the pattern
 * working in MatchEngineImpl for pure CPU-bound work.
 */
@Slf4j
@Repository
public class V24DetailedMatchRedisAdapter implements V24DetailedMatchStoragePort {

    private static final String KEY_PREFIX = "career:";
    private static final String KEY_MATCH_DETAIL = ":match-detail:";

    private final ReactiveRedisTemplate<String, V24DetailedMatchData> redisTemplate;
    // Larger pool to parallelize multi-key retrieval without pipelining
    private final ExecutorService executor = Executors.newFixedThreadPool(
            8, r -> {
                Thread t = new Thread(r, "v24-redis-io");
                t.setDaemon(true);
                return t;
            });

    public V24DetailedMatchRedisAdapter(
            @Qualifier("v24DetailedMatchDataRedisTemplate") ReactiveRedisTemplate<String, V24DetailedMatchData> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String buildKey(String careerId, String matchId) {
        return KEY_PREFIX + careerId + KEY_MATCH_DETAIL + matchId;
    }

    private String buildPattern(String careerId) {
        return KEY_PREFIX + careerId + KEY_MATCH_DETAIL + "*";
    }

    @Override
    public void save(String careerId, V24DetailedMatchData detail) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        if (detail == null) {
            throw new IllegalArgumentException("detail must not be null");
        }
        String matchId = detail.matchId();
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("detail.matchId must not be blank");
        }
        String key = buildKey(careerId, matchId);
        try {
            CompletableFuture.runAsync(() ->
                    redisTemplate.opsForValue().set(key, detail).block(), executor)
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Redis unavailable — log and continue rather than crashing the round
            log.error("[V24-REDIS] Failed to save match detail key={}, careerId={}, matchId={}: {}",
                    key, careerId, matchId, e.getMessage());
            // Do not throw — caller handles gracefully via warning log
        }
    }

    @Override
    public Optional<V24DetailedMatchData> findByMatchId(String careerId, String matchId) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        String key = buildKey(careerId, matchId);
        try {
            V24DetailedMatchData data = CompletableFuture.supplyAsync(() ->
                    redisTemplate.opsForValue().get(key).block(), executor)
                    .get(5, TimeUnit.SECONDS);
            return Optional.ofNullable(data);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Retrieve all V24DetailedMatchData for a career.
     *
     * <p>Uses Redis KEYS scan to find matching keys, then parallel GET for values.
     * Deserialization failures are logged per-key and skipped.
     * Timeout on the KEYS phase is treated as a failure (ERROR log), not as "no data".
     */
    @Override
    public List<V24DetailedMatchData> findByCareerId(String careerId) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        String pattern = buildPattern(careerId);
        log.info("[V24-REDIS] findByCareerId careerId={}, pattern={}", careerId, pattern);
        try {
            long start = System.nanoTime();
            // KEYS with explicit 30s timeout — if it times out, treat as error not empty
            @SuppressWarnings("unchecked")
            List<String> keys = (List<String>) CompletableFuture.supplyAsync(() ->
                    redisTemplate.keys(pattern).collectList().block(Duration.ofSeconds(30)), executor)
                    .get(35, TimeUnit.SECONDS);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            if (keys == null) {
                log.error("[V24-REDIS] KEYS returned null for careerId={}, pattern={} (took {}ms) — treating as error",
                        careerId, pattern, elapsed);
                return List.of();
            }
            log.info("[V24-REDIS] KEYS for careerId={} took {}ms, found {} keys", careerId, elapsed, keys.size());
            if (keys.isEmpty()) {
                log.info("[V24-REDIS] no keys found for pattern={}", pattern);
                return List.of();
            }
            log.info("[V24-REDIS] fetching values for {} keys in parallel", keys.size());

            // Fetch all values in parallel using individual futures
            List<CompletableFuture<V24DetailedMatchData>> futures = new ArrayList<>(keys.size());
            for (String key : keys) {
                CompletableFuture<V24DetailedMatchData> f = CompletableFuture.supplyAsync(() -> {
                    try {
                        V24DetailedMatchData d = redisTemplate.opsForValue().get(key)
                                .block(Duration.ofSeconds(5));
                        return d;
                    } catch (Exception e) {
                        log.warn("[V24-REDIS] deserialization failed for key={}: {}", key, e.getMessage());
                        return null;
                    }
                }, executor);
                futures.add(f);
            }
            // Wait for all with generous total timeout
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
            List<V24DetailedMatchData> results = new ArrayList<>();
            for (String key : keys) {
                try {
                    V24DetailedMatchData d = futures.get(keys.indexOf(key)).get(1, TimeUnit.SECONDS);
                    if (d != null) {
                        results.add(d);
                    }
                } catch (Exception e) {
                    log.warn("[V24-REDIS] failed to retrieve value for key={}: {}", key, e.getMessage());
                }
            }
            log.info("[V24-REDIS] findByCareerId returning {} results for careerId={}", results.size(), careerId);
            return results;
        } catch (Exception e) {
            log.error("[V24-REDIS] findByCareerId FAILED for careerId={}, pattern={}: {}",
                    careerId, pattern, e.getMessage());
            // Do NOT return empty list silently — caller should handle error case
            throw new RuntimeException("[V24-REDIS] findByCareerId failed for careerId=" + careerId, e);
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
            // ignore
        }
    }
}
