package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.infrastructure.persistence.entity.PlayerEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Repositorio Redis para jugadores con scope de usuario.
 * Keys: user:{userId}:player:{playerId}
 */
@Repository
public class PlayerRedisRepository {
    private final ReactiveRedisTemplate<String, PlayerEntity> redisTemplate;

    public PlayerRedisRepository(
            @Qualifier("playerEntityRedisTemplate") ReactiveRedisTemplate<String, PlayerEntity> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Genera la clave Redis para un jugador de un usuario específico
     */
    private String getKey(UUID userId, String playerId) {
        return "user:" + userId.toString() + ":player:" + playerId;
    }

    /**
     * Genera el patrón de búsqueda para todos los jugadores de un usuario
     */
    private String getKeyPattern(UUID userId) {
        return "user:" + userId.toString() + ":player:*";
    }

    /**
     * Guarda un jugador en Redis para un usuario específico
     */
    public Mono<Boolean> save(UUID userId, PlayerEntity player) {
        String key = getKey(userId, player.getId().toString());
        return redisTemplate.opsForValue().set(key, player);
    }

    /**
     * Busca un jugador por ID para un usuario específico
     */
    public Mono<PlayerEntity> findById(UUID userId, String playerId) {
        String key = getKey(userId, playerId);
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Busca todos los jugadores de un usuario específico
     */
    public Flux<PlayerEntity> findAllByUserId(UUID userId) {
        String pattern = getKeyPattern(userId);
        return redisTemplate.keys(pattern)
                .collectList()
                .flatMapMany(keys -> {
                    if (keys.isEmpty()) {
                        return Flux.empty();
                    }
                    return redisTemplate.opsForValue().multiGet(keys)
                            .map(list -> {
                                if (list == null) {
                                    return new ArrayList<PlayerEntity>();
                                }
                                ArrayList<PlayerEntity> filtered = new ArrayList<>();
                                for (Object item : list) {
                                    if (item instanceof PlayerEntity) {
                                        filtered.add((PlayerEntity) item);
                                    }
                                }
                                return filtered;
                            })
                            .flatMapMany(values -> Flux.fromIterable(values));
                });
    }

    /**
     * Elimina un jugador de Redis para un usuario específico
     */
    public Mono<Boolean> deleteById(UUID userId, String playerId) {
        String key = getKey(userId, playerId);
        return redisTemplate.delete(key)
                .map(count -> count > 0);
    }
}
