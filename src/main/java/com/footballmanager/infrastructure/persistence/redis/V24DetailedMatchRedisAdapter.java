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

/**
 * V24D4B: Redis adapter for V24DetailedMatchStoragePort.
 *
 * <p>Stores V24DetailedMatchData snapshots at keys:
 * {@code career:{careerId}:match-detail:{matchId}}
 *
 * <p>This adapter is behind a feature flag and is NOT wired into production simulation.
 * V24D4B is persistence-only — it does not activate V24 detail storage unless explicitly
 * wired in a later phase (V24D5).
 *
 * <p>Uses Jackson2Json serialization via dedicated ReactiveRedisTemplate.
 * The objectMapper is configured with {@code FAIL_ON_UNKNOWN_PROPERTIES = false}
 * for forward compatibility when newer schemaVersion data is stored.
 */
@Repository
public class V24DetailedMatchRedisAdapter implements V24DetailedMatchStoragePort {

    private static final String KEY_PREFIX = "career:";
    private static final String KEY_MATCH_DETAIL = ":match-detail:";

    private final ReactiveRedisTemplate<String, V24DetailedMatchData> redisTemplate;

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
        redisTemplate.opsForValue().set(key, detail).block();
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
        V24DetailedMatchData result = redisTemplate.opsForValue().get(key).block();
        return Optional.ofNullable(result);
    }

    /**
     * Retrieve all V24DetailedMatchData for a career.
     *
     * <p>MVP note: uses KEYS scan. Acceptable for small-to-medium careers.
     * Each key is fetched individually after the scan.
     *
     * @param careerId  the career to retrieve match details for
     * @return list of all match details for the career, never null
     */
    @Override
    public List<V24DetailedMatchData> findByCareerId(String careerId) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        String pattern = buildPattern(careerId);
        List<String> keys = redisTemplate.keys(pattern).collectList().block();
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<V24DetailedMatchData> results = new ArrayList<>(keys.size());
        for (String key : keys) {
            V24DetailedMatchData detail = redisTemplate.opsForValue().get(key).block();
            if (detail != null) {
                results.add(detail);
            }
        }
        return results;
    }

    @Override
    public void deleteByCareerId(String careerId) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        String pattern = buildPattern(careerId);
        redisTemplate.keys(pattern)
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.just(0L);
                    }
                    return redisTemplate.delete(keys.toArray(new String[0]));
                })
                .block();
    }
}
