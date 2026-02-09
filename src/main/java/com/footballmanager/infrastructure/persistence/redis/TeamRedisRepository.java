package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.infrastructure.persistence.entity.TeamEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Repositorio Redis para equipos con scope de usuario.
 * Keys: user:{userId}:team:{teamId}
 */
@Repository
public class TeamRedisRepository {
    private final ReactiveRedisTemplate<String, TeamEntity> redisTemplate;

    public TeamRedisRepository(
            @Qualifier("teamEntityRedisTemplate") ReactiveRedisTemplate<String, TeamEntity> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Genera la clave Redis para un equipo de un usuario específico
     */
    private String getKey(UUID userId, UUID teamId) {
        return "user:" + userId.toString() + ":team:" + teamId.toString();
    }

    /**
     * Genera el patrón de búsqueda para todos los equipos de un usuario
     */
    private String getKeyPattern(UUID userId) {
        return "user:" + userId.toString() + ":team:*";
    }

    /**
     * Guarda un equipo en Redis para un usuario específico
     */
    public Mono<Boolean> save(UUID userId, TeamEntity team) {
        String key = getKey(userId, team.getId());
        return redisTemplate.opsForValue().set(key, team);
    }

    /**
     * Busca un equipo por ID para un usuario específico
     */
    public Mono<TeamEntity> findById(UUID userId, UUID teamId) {
        String key = getKey(userId, teamId);
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Busca todos los equipos de un usuario específico
     */
    public Flux<TeamEntity> findAllByUserId(UUID userId) {
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
                                    return new ArrayList<TeamEntity>();
                                }
                                ArrayList<TeamEntity> filtered = new ArrayList<>();
                                for (Object item : list) {
                                    if (item instanceof TeamEntity) {
                                        filtered.add((TeamEntity) item);
                                    }
                                }
                                return filtered;
                            })
                            .flatMapMany(values -> Flux.fromIterable(values));
                });
    }

    /**
     * Busca equipos por managerId para un usuario específico
     */
    public Flux<TeamEntity> findByManagerId(UUID userId, UUID managerId) {
        return findAllByUserId(userId)
                .filter(team -> team.getManagerId() != null && team.getManagerId().equals(managerId));
    }

    /**
     * Verifica si existe un equipo
     */
    public Mono<Boolean> existsById(UUID userId, UUID teamId) {
        String key = getKey(userId, teamId);
        return redisTemplate.hasKey(key);
    }

    /**
     * Elimina un equipo de Redis para un usuario específico
     */
    public Mono<Boolean> deleteById(UUID userId, UUID teamId) {
        String key = getKey(userId, teamId);
        return redisTemplate.delete(key)
                .map(count -> count > 0);
    }

    /**
     * Elimina todos los equipos de un usuario
     */
    public Mono<Long> deleteAllByUserId(UUID userId) {
        String pattern = getKeyPattern(userId);
        return redisTemplate.keys(pattern)
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.just(0L);
                    }
                    return redisTemplate.delete(keys.toArray(new String[0]));
                });
    }
}
