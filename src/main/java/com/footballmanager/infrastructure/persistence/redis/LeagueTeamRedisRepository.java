package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.infrastructure.persistence.entity.LeagueTeamEntity;
import com.footballmanager.application.service.world.WorldPersistedWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.Collection;
import java.util.Map;

/**
 * Repositorio Redis para relación Liga-Equipo con scope de usuario.
 * Keys: user:{userId}:league:{leagueId}:teams -> Set de teamIds
 *       user:{userId}:team:{teamId}:leagues -> Set de leagueIds
 */
@Repository
@RequiredArgsConstructor
@WorldPersistedWriter(root = LeagueTeamEntity.class, writeMethod = "add/sync",
        storageFamily = "user:*:league/team relations",
        role = WorldPersistedWriter.DurabilityRole.EXPLICITLY_NON_WORLD_REFERENCE)
public class LeagueTeamRedisRepository {
    private static final org.springframework.data.redis.core.script.RedisScript<Long> SYNC_RELATIONS =
            org.springframework.data.redis.core.script.RedisScript.of("""
                    local user = ARGV[1]
                    for i = 2, #ARGV, 2 do
                      local league = ARGV[i]
                      local team = ARGV[i + 1]
                      redis.call('SADD', 'user:' .. user .. ':league:' .. league .. ':teams', team)
                      redis.call('SADD', 'user:' .. user .. ':team:' .. team .. ':leagues', league)
                    end
                    return (#ARGV - 1) / 2
                    """, Long.class);
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

    public Mono<Void> addTeamsToLeague(UUID userId, UUID leagueId, Collection<UUID> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) return Mono.empty();
        String teamsKey = getTeamsKey(userId, leagueId);
        String[] values = teamIds.stream().map(UUID::toString).toArray(String[]::new);
        return redisTemplate.opsForSet().add(teamsKey, values).then();
    }

    public Mono<Void> addLeaguesToTeam(UUID userId, UUID teamId, Collection<UUID> leagueIds) {
        if (leagueIds == null || leagueIds.isEmpty()) return Mono.empty();
        String leaguesKey = getLeaguesKey(userId, teamId);
        String[] values = leagueIds.stream().map(UUID::toString).toArray(String[]::new);
        return redisTemplate.opsForSet().add(leaguesKey, values).then();
    }

    public Mono<Void> removeTeamFromLeague(UUID userId, UUID leagueId, UUID teamId) {
        String teamsKey = getTeamsKey(userId, leagueId);
        String leaguesKey = getLeaguesKey(userId, teamId);
        return redisTemplate.opsForSet().remove(teamsKey, teamId.toString())
                .then(redisTemplate.opsForSet().remove(leaguesKey, leagueId.toString()))
                .then();
    }

    public Mono<Void> syncRelations(UUID userId, Map<UUID, UUID> teamToLeague) {
        if (teamToLeague == null || teamToLeague.isEmpty()) return Mono.empty();
        java.util.List<String> args = new java.util.ArrayList<>(1 + teamToLeague.size() * 2);
        args.add(userId.toString());
        teamToLeague.forEach((team, league) -> {
            args.add(league.toString());
            args.add(team.toString());
        });
        return redisTemplate.execute(SYNC_RELATIONS, java.util.List.of(), args).then();
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
