package com.footballmanager.infrastructure.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.ports.out.match.MatchStateRepository;
import com.footballmanager.infrastructure.persistence.redis.CareerOwnershipTouchService;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Implementación reactiva del repositorio de estados de partido usando Redis.
 */
@Repository
public class RedisMatchStateRepository implements MatchStateRepository {

    private static final String KEY_PREFIX = "match:state:";
    private static final Duration TTL = Duration.ofHours(24);

    private final @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CareerOwnershipTouchService ownershipTouchService;

    @Autowired
    public RedisMatchStateRepository(
            @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            CareerOwnershipTouchService ownershipTouchService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ownershipTouchService = ownershipTouchService;
    }

    public RedisMatchStateRepository(
            @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, null);
    }

    @Override
    public Mono<MatchState> findById(UUID userId, UUID matchId) {
        String key = buildKey(userId, matchId);

        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(json -> {
                    try {
                        MatchState state = objectMapper.readValue(json, MatchState.class);
                        return Mono.just(state);
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                });
    }

    @Override
    public Mono<MatchState> save(UUID userId, MatchState matchState) {
        return Mono.error(new IllegalStateException(
                "state writer requires explicit lifecycle context"));
    }

    @Override
    public Mono<MatchState> save(UUID userId, MatchState matchState, CareerWriteContext context) {
        if (context == null || matchState == null || !context.ownerId().equals(userId)
                || !context.ownerId().toString().equals(matchState.getUserId())
                || !context.careerId().equals(matchState.getCareerId())
                || !context.expectedGeneration().equals(matchState.getLifecycleGeneration())) {
            return Mono.error(new IllegalArgumentException("state lifecycle context does not match state"));
        }
        return saveInternal(userId, context, matchState);
    }

    private Mono<MatchState> saveInternal(UUID userId, CareerWriteContext context, MatchState matchState) {
        String key = buildKey(userId, matchState.getMatchId());

        try {
            String json = objectMapper.writeValueAsString(matchState);

            Mono<MatchState> persist = redisTemplate.opsForValue()
                    .set(key, json, TTL)
                    .thenReturn(matchState);
            return ownershipTouchService == null
                    ? Mono.error(new IllegalStateException("career ownership service is required"))
                    : ownershipTouchService.touchBeforeWrite(context, () -> persist);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> deleteById(UUID userId, UUID matchId) {
        String key = buildKey(userId, matchId);

        return redisTemplate.delete(key)
                .then()
                .onErrorResume(e -> Mono.empty());
    }

    private String buildKey(UUID userId, UUID matchId) {
        return KEY_PREFIX + userId.toString() + ":" + matchId.toString();
    }
}
