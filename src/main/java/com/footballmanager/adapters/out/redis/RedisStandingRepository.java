package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.Standing;
import com.footballmanager.domain.ports.out.standing.StandingRepository;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Repository para guardar y cargar Standings en Redis.
 * Keys: user:{userId}:standing:{seasonKey}:{teamId}
 */
@Repository
public class RedisStandingRepository implements StandingRepository {

    private static final String KEY_PREFIX = "user:";
    private static final Duration CACHE_TTL = Duration.ofDays(30);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisStandingRepository(@Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                   ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private String getKey(UUID userId, String seasonKey, UUID teamId) {
        return KEY_PREFIX + userId + ":standing:" + seasonKey + ":" + teamId.toString();
    }

    private String getSeasonPattern(UUID userId, String seasonKey) {
        return KEY_PREFIX + userId + ":standing:" + seasonKey + ":*";
    }

    @Override
    public Mono<Void> save(UUID userId, Standing standing) {
        String key = getKey(userId, standing.getSeasonKey(), UUID.fromString(standing.getTeamId()));
        try {
            String json = objectMapper.writeValueAsString(standing);
            return redisTemplate.opsForValue()
                    .set(key, json, CACHE_TTL)
                    .then();
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Standing> findBySeasonKeyAndTeamId(UUID userId, String seasonKey, UUID teamId) {
        String key = getKey(userId, seasonKey, teamId);
        return redisTemplate.opsForValue()
                .get(key)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, Standing.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(standing -> standing != null)
                .switchIfEmpty(Mono.defer(() -> {
                    return Mono.empty();
                }));
    }

    @Override
    public Flux<Standing> findBySeasonKey(UUID userId, String seasonKey) {
        String pattern = getSeasonPattern(userId, seasonKey);
        return redisTemplate.keys(pattern)
                .flatMap(key -> redisTemplate.opsForValue().get(key))
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, Standing.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(standing -> standing != null);
    }

    @Override
    public Mono<Boolean> exists(UUID userId, String seasonKey, UUID teamId) {
        String key = getKey(userId, seasonKey, teamId);
        return redisTemplate.hasKey(key);
    }

    @Override
    public Mono<Void> delete(UUID userId, String seasonKey, UUID teamId) {
        String key = getKey(userId, seasonKey, teamId);
        return redisTemplate.delete(key)
                .then();
    }

    @Override
    public Mono<Void> deleteBySeasonKey(UUID userId, String seasonKey) {
        String pattern = getSeasonPattern(userId, seasonKey);
        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete)
                .then();
    }
}
