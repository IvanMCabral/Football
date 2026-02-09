package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.RuntimeMatch;
import com.footballmanager.domain.ports.out.match.MatchRuntimeRepository;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Implementación Redis del repository de RuntimeMatch.
 *
 * ESTRATEGIA:
 * - Key pattern: runtime:match:{userId}:{matchId}
 * - TTL: 2 horas (7200 segundos)
 * - Serialización: JSON (Jackson)
 * - Expiración automática si no se finaliza
 */
@Repository
public class RedisMatchRuntimeRepository implements MatchRuntimeRepository {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisMatchRuntimeRepository(
            @org.springframework.beans.factory.annotation.Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private static final String KEY_PREFIX = "runtime:match:";
    private static final Duration TTL = Duration.ofHours(2);

    @Override
    public Mono<RuntimeMatch> save(UUID userId, RuntimeMatch runtimeMatch) {
        String key = buildKey(userId, runtimeMatch.getMatchId());
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(runtimeMatch))
                .flatMap(json -> redisTemplate.opsForValue().set(key, json, TTL))
                .thenReturn(runtimeMatch);
    }

    @Override
    public Mono<RuntimeMatch> findByMatchId(UUID userId, String matchId) {
        String key = buildKey(userId, matchId);
        return redisTemplate.opsForValue().get(key)
            .flatMap(json -> Mono.fromCallable(() ->
                objectMapper.readValue(json, RuntimeMatch.class)))
                .switchIfEmpty(Mono.defer(() -> {
                    return Mono.empty();
                }));
    }

    @Override
    public Mono<Void> delete(UUID userId, String matchId) {
        String key = buildKey(userId, matchId);
        return redisTemplate.delete(key)
                .then();
    }

    private String buildKey(UUID userId, String matchId) {
        return KEY_PREFIX + userId.toString() + ":" + matchId;
    }
}
