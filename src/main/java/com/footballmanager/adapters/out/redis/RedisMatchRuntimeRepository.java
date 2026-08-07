package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.RuntimeMatch;
import com.footballmanager.domain.ports.out.match.MatchRuntimeRepository;
import com.footballmanager.infrastructure.persistence.redis.CareerOwnershipTouchService;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CareerOwnershipTouchService ownershipTouchService;

    @Autowired
    public RedisMatchRuntimeRepository(
            @org.springframework.beans.factory.annotation.Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            CareerOwnershipTouchService ownershipTouchService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ownershipTouchService = ownershipTouchService;
    }

    public RedisMatchRuntimeRepository(
            @org.springframework.beans.factory.annotation.Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, null);
    }

    private static final String KEY_PREFIX = "runtime:match:";
    private static final Duration TTL = Duration.ofHours(2);

    @Override
    public Mono<RuntimeMatch> save(UUID userId, RuntimeMatch runtimeMatch) {
        if (ownershipTouchService != null && runtimeMatch.getCareerId() != null
                && runtimeMatch.getLifecycleGeneration() == null) {
            return Mono.error(new IllegalStateException("runtime writer requires lifecycle context"));
        }
        CareerWriteContext context = ownershipTouchService == null || runtimeMatch.getCareerId() == null ? null
                : new CareerWriteContext(userId, runtimeMatch.getCareerId(), runtimeMatch.getLifecycleGeneration());
        return saveInternal(userId, context, runtimeMatch);
    }

    @Override
    public Mono<RuntimeMatch> save(UUID userId, RuntimeMatch runtimeMatch, CareerWriteContext context) {
        if (context == null || runtimeMatch == null || !context.ownerId().equals(userId)
                || !context.careerId().equals(runtimeMatch.getCareerId())) {
            return Mono.error(new IllegalArgumentException("runtime lifecycle context does not match runtime"));
        }
        return saveInternal(userId, context, runtimeMatch);
    }

    private Mono<RuntimeMatch> saveInternal(UUID userId, CareerWriteContext context, RuntimeMatch runtimeMatch) {
        String key = buildKey(userId, runtimeMatch.getMatchId());
        Mono<RuntimeMatch> persist = Mono.fromCallable(() -> objectMapper.writeValueAsString(runtimeMatch))
                .flatMap(json -> redisTemplate.opsForValue().set(key, json, TTL))
                .thenReturn(runtimeMatch);
        return ownershipTouchService == null || runtimeMatch.getCareerId() == null
                ? persist
                : ownershipTouchService.touchBeforeWrite(context, () -> persist);
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
