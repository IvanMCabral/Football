package com.footballmanager.infrastructure.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.ports.out.match.MatchStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Implementación reactiva del repositorio de estados de partido usando Redis.
 */
@Repository
@RequiredArgsConstructor
public class RedisMatchStateRepository implements MatchStateRepository {

    private static final String KEY_PREFIX = "match:state:";
    private static final Duration TTL = Duration.ofHours(24);

    private final @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

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
        String key = buildKey(userId, matchState.getMatchId());

        try {
            String json = objectMapper.writeValueAsString(matchState);

            return redisTemplate.opsForValue()
                    .set(key, json, TTL)
                    .thenReturn(matchState);
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
