package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repositorio para WorldSnapshot en Redis.
 * Key: world:{userId}
 *
 * WorldSnapshot se crea UNA VEZ y luego solo se actualiza.
 * NO se borra automáticamente (no tiene TTL).
 */
@Repository
public class RedisWorldRepository {

    private static final String KEY_PREFIX = "world:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisWorldRepository(@Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Genera la key de Redis para el WorldSnapshot de un usuario
     */
    private String generateKey(UUID userId) {
        return KEY_PREFIX + userId.toString();
    }

    /**
     * Guarda o actualiza el WorldSnapshot
     */
    public Mono<WorldSnapshot> save(WorldSnapshot snapshot) {
        String key = generateKey(snapshot.getUserId());

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(snapshot))
                .flatMap(json -> redisTemplate.opsForValue().set(key, json))
                .thenReturn(snapshot)
                .onErrorResume(e -> {
                    return Mono.error(e);
                });
    }

    /**
     * Busca el WorldSnapshot por userId
     */
    public Mono<WorldSnapshot> findByUserId(UUID userId) {
        String key = generateKey(userId);

        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> Mono.fromCallable(() -> objectMapper.readValue(json, WorldSnapshot.class)))
                .onErrorResume(e -> {
                    return Mono.empty();
                });
    }

    /**
     * Verifica si existe un WorldSnapshot para el userId
     */
    public Mono<Boolean> existsByUserId(UUID userId) {
        String key = generateKey(userId);
        return redisTemplate.hasKey(key);
    }

    /**
     * Elimina el WorldSnapshot (solo para testing o reset manual)
     */
    public Mono<Boolean> deleteByUserId(UUID userId) {
        String key = generateKey(userId);
        return redisTemplate.delete(key)
                .map(count -> count > 0);
    }
}
