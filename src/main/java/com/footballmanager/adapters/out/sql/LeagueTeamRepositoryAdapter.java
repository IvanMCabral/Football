package com.footballmanager.adapters.out.sql;

import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.ports.out.league.LeagueTeamRepository;
import com.footballmanager.infrastructure.persistence.entity.LeagueTeamEntity;
import com.footballmanager.infrastructure.persistence.redis.LeagueTeamRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LeagueTeamRepositoryAdapter implements LeagueTeamRepository {
    private final LeagueTeamRedisRepository redisRepository;

    @Override
    public Mono<UUID> findCommonLeagueId(UUID userId, TeamId homeId, TeamId awayId) {
        Mono<java.util.List<UUID>> homeLeagueIdsMono = redisRepository.findLeagueIdsByTeamId(userId, homeId.getValue())
                .collectList();
        Mono<java.util.List<UUID>> awayLeagueIdsMono = redisRepository.findLeagueIdsByTeamId(userId, awayId.getValue())
                .collectList();
        return Mono.zip(homeLeagueIdsMono, awayLeagueIdsMono)
                .flatMap(tuple -> {
                    java.util.List<UUID> homeLeagueIds = tuple.getT1();
                    java.util.List<UUID> awayLeagueIds = tuple.getT2();
                    return Flux.fromIterable(homeLeagueIds)
                            .filter(awayLeagueIds::contains)
                            .next();
                });
    }

    @Override
    public Mono<Void> validateTeamsInSameLeague(UUID userId, TeamId homeId, TeamId awayId) {
        return findCommonLeagueId(userId, homeId, awayId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Teams are not in the same league")))
                .then();
    }

    @Override
    public Flux<LeagueTeamEntity> findByTeamId(UUID userId, UUID teamId) {
        return redisRepository.findByTeamId(userId, teamId);
    }

    @Override
    public Flux<LeagueTeamEntity> findByLeagueId(UUID userId, UUID leagueId) {
        return redisRepository.findByLeagueId(userId, leagueId);
    }

    @Override
    public Mono<Void> addTeamToLeague(UUID userId, UUID leagueId, UUID teamId) {
        return redisRepository.addTeamToLeague(userId, leagueId, teamId)
                .then();
    }

    @Override
    public Mono<Void> removeTeamFromLeague(UUID userId, UUID leagueId, UUID teamId) {
        return redisRepository.removeTeamFromLeague(userId, leagueId, teamId)
                .then();
    }
}
