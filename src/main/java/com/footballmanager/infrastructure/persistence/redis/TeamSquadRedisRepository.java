package com.footballmanager.infrastructure.persistence.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repositorio Redis para squad de equipos con scope de usuario.
 * Keys: user:{userId}:team:{teamId}:squad -> Set de playerIds
 */
@Repository
@RequiredArgsConstructor
public class TeamSquadRedisRepository {
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private String getKey(UUID userId, UUID teamId) {
        return "user:" + userId + ":team:" + teamId + ":squad";
    }

    public Mono<Boolean> addPlayerToTeam(UUID userId, UUID teamId, UUID playerId) {
        String key = getKey(userId, teamId);
        return redisTemplate.opsForSet().add(key, playerId.toString())
                .map(count -> count > 0);
    }

    public Mono<Boolean> removePlayerFromTeam(UUID userId, UUID teamId, UUID playerId) {
        String key = getKey(userId, teamId);
        return redisTemplate.opsForSet().remove(key, playerId.toString())
                .map(count -> count > 0);
    }

    public Mono<Long> removeAllPlayersFromTeam(UUID userId, UUID teamId) {
        String key = getKey(userId, teamId);
        return redisTemplate.delete(key);
    }

    public Flux<UUID> findPlayerIdsByTeamId(UUID userId, UUID teamId) {
        String key = getKey(userId, teamId);
        return redisTemplate.opsForSet().members(key)
                .map(UUID::fromString);
    }

    public Mono<Boolean> isPlayerInTeam(UUID userId, UUID teamId, UUID playerId) {
        String key = getKey(userId, teamId);
        return redisTemplate.opsForSet().isMember(key, playerId.toString());
    }

    public Mono<Long> countPlayersInTeam(UUID userId, UUID teamId) {
        String key = getKey(userId, teamId);
        return redisTemplate.opsForSet().size(key);
    }
}
