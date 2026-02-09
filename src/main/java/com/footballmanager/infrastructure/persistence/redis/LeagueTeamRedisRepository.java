package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.infrastructure.persistence.entity.LeagueTeamEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repositorio Redis para relación Liga-Equipo con scope de usuario.
 * Keys: user:{userId}:league:{leagueId}:teams -> Set de teamIds
 *       user:{userId}:team:{teamId}:leagues -> Set de leagueIds
 */
@Repository
@RequiredArgsConstructor
public class LeagueTeamRedisRepository {
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private String getTeamsKey(UUID userId, UUID leagueId) {
        return "user:" + userId + ":league:" + leagueId + ":teams";
    }

    private String getLeaguesKey(UUID userId, UUID teamId) {
        return "user:" + userId + ":team:" + teamId + ":leagues";
    }

    public Mono<Void> addTeamToLeague(UUID userId, UUID leagueId, UUID teamId) {
        String teamsKey = getTeamsKey(userId, leagueId);
        String leaguesKey = getLeaguesKey(userId, teamId);
        return redisTemplate.opsForSet().add(teamsKey, teamId.toString())
                .then(redisTemplate.opsForSet().add(leaguesKey, leagueId.toString()))
                .then();
    }

    public Mono<Void> removeTeamFromLeague(UUID userId, UUID leagueId, UUID teamId) {
        String teamsKey = getTeamsKey(userId, leagueId);
        String leaguesKey = getLeaguesKey(userId, teamId);
        return redisTemplate.opsForSet().remove(teamsKey, teamId.toString())
                .then(redisTemplate.opsForSet().remove(leaguesKey, leagueId.toString()))
                .then();
    }

    public Flux<UUID> findTeamIdsByLeagueId(UUID userId, UUID leagueId) {
        String key = getTeamsKey(userId, leagueId);
        return redisTemplate.opsForSet().members(key)
                .map(UUID::fromString);
    }

    public Flux<UUID> findLeagueIdsByTeamId(UUID userId, UUID teamId) {
        String key = getLeaguesKey(userId, teamId);
        return redisTemplate.opsForSet().members(key)
                .map(UUID::fromString);
    }

    public Flux<LeagueTeamEntity> findByTeamId(UUID userId, UUID teamId) {
        return findLeagueIdsByTeamId(userId, teamId)
                .map(leagueId -> {
                    LeagueTeamEntity entity = new LeagueTeamEntity();
                    entity.setLeagueId(leagueId);
                    entity.setTeamId(teamId);
                    return entity;
                });
    }

    public Flux<LeagueTeamEntity> findByLeagueId(UUID userId, UUID leagueId) {
        return findTeamIdsByLeagueId(userId, leagueId)
                .map(teamId -> {
                    LeagueTeamEntity entity = new LeagueTeamEntity();
                    entity.setLeagueId(leagueId);
                    entity.setTeamId(teamId);
                    return entity;
                });
    }

    public Mono<Long> countTeamsInLeague(UUID userId, UUID leagueId) {
        String key = getTeamsKey(userId, leagueId);
        return redisTemplate.opsForSet().size(key);
    }
}
