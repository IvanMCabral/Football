package com.footballmanager.application.service.world.load;

import com.footballmanager.domain.ports.out.league.LeagueRepository;
import com.footballmanager.infrastructure.persistence.redis.LeagueTeamRedisRepository;
import com.footballmanager.infrastructure.persistence.repository.LeagueTeamR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sincroniza las relaciones league-team desde SQL a Redis.
 * Solo sincroniza si Redis está vacío.
 */
@Service
@RequiredArgsConstructor
public class LeagueTeamSyncService {

    private final LeagueRepository leagueRepository;
    private final LeagueTeamRedisRepository leagueTeamRedisRepository;
    private final LeagueTeamR2dbcRepository leagueTeamR2dbcRepository;

    /**
     * Carga el map de league-team, sincronizando desde SQL si es necesario.
     */
    public Mono<Map<UUID, UUID>> loadLeagueTeamsMap(UUID userId) {
        return loadFromRedis(userId)
                .flatMap(redisMap -> {
                    if (redisMap != null && !redisMap.isEmpty()) {
                        return Mono.just(redisMap);
                    }
                    return syncFromSql(userId);
                })
                .switchIfEmpty(syncFromSql(userId));
    }

    /**
     * Carga el map desde Redis.
     */
    private Mono<Map<UUID, UUID>> loadFromRedis(UUID userId) {
        return leagueRepository.findAll(userId)
                .flatMap(league -> leagueTeamRedisRepository.findByLeagueId(userId, league.getId().getValue())
                        .map(entity -> Map.entry(entity.getTeamId(), league.getId().getValue())))
                .collectList()
                .map(entries -> entries.stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * Carga el map desde SQL y sincroniza a Redis.
     */
    private Mono<Map<UUID, UUID>> syncFromSql(UUID userId) {
        return leagueRepository.findAll(userId)
                .flatMap(league -> leagueTeamR2dbcRepository.findByLeagueId(league.getId().getValue())
                        .map(entity -> Map.entry(entity.getTeamId(), league.getId().getValue())))
                .collectList()
                .flatMap(entries -> {
                    Map<UUID, UUID> map = entries.stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                    return syncToRedis(userId, map)
                            .thenReturn(map);
                });
    }

    /**
     * Sincroniza las asociaciones a Redis.
     */
    private Mono<Void> syncToRedis(UUID userId, Map<UUID, UUID> leagueTeamsMap) {
        List<Mono<Void>> operations = leagueTeamsMap.entrySet().stream()
                .map(entry -> leagueTeamRedisRepository.addTeamToLeague(userId, entry.getValue(), entry.getKey()))
                .toList();

        return Flux.concat(operations).then();
    }
}
