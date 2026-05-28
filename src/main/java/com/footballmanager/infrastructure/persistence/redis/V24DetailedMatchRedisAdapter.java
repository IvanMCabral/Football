package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

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
@Repository
public class V24DetailedMatchRedisAdapter implements V24DetailedMatchStoragePort {

    private static final String KEY_PREFIX = "career:";
    private static final String KEY_MATCH_DETAIL = ":match-detail:";

    private final ReactiveRedisTemplate<String, V24DetailedMatchData> redisTemplate;
    private final ExecutorService executor = Executors.newFixedThreadPool(
            2, r -> {
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
            throw new RuntimeException("Redis save failed", e);
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
     * <p>MVP note: uses KEYS scan. Acceptable for small-to-medium careers.
     * Each key is fetched individually after the scan.
     */
    @Override
    public List<V24DetailedMatchData> findByCareerId(String careerId) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        String pattern = buildPattern(careerId);
        try {
            @SuppressWarnings("unchecked")
            List<String> keys = (List<String>) CompletableFuture.supplyAsync(() -> {
                List<String> k = redisTemplate.keys(pattern).collectList().block();
                return k != null ? k : List.of();
            }, executor).get(10, TimeUnit.SECONDS);
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }
            List<V24DetailedMatchData> results = new ArrayList<>(keys.size());
            for (String key : keys) {
                V24DetailedMatchData detail = CompletableFuture.supplyAsync(() ->
                        redisTemplate.opsForValue().get(key).block(), executor)
                        .get(5, TimeUnit.SECONDS);
                if (detail != null) {
                    results.add(detail);
                }
            }
            return results;
        } catch (Exception e) {
            return List.of();
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