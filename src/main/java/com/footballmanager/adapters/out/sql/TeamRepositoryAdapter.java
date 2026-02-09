package com.footballmanager.adapters.out.sql;

import com.footballmanager.infrastructure.persistence.entity.*;
import com.footballmanager.infrastructure.persistence.redis.TeamRedisRepository;
import com.footballmanager.infrastructure.persistence.redis.TeamSquadRedisRepository;
import com.footballmanager.infrastructure.persistence.repository.TeamR2dbcRepository;

import com.footballmanager.domain.model.aggregate.*;
import com.footballmanager.domain.model.valueobject.*;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@Primary
@RequiredArgsConstructor
public class TeamRepositoryAdapter implements TeamRepository {

    private final TeamRedisRepository teamRedisRepository;
    private final TeamSquadRedisRepository squadRedisRepository;
    private final TeamR2dbcRepository teamR2dbcRepository;

    @Override
    public Mono<Team> save(UUID userId, Team team) {
        TeamEntity entity = TeamEntity.fromDomainForInsert(team);
        return teamRedisRepository.save(userId, entity)
            .thenReturn(team);
    }

    @Override
    public Mono<Team> findById(UUID userId, UUID teamId) {
        return teamRedisRepository.findById(userId, teamId)
            .flatMap(entity -> {
                Set<PlayerId> playerIds = new HashSet<>();
                return squadRedisRepository.findPlayerIdsByTeamId(userId, teamId)
                    .map(PlayerId::of)
                    .collectList()
                    .map(list -> {
                        playerIds.addAll(list);
                        return entity.toDomain(playerIds);
                    });
            });
    }

    @Override
    public Flux<Team> findByManagerId(UUID userId, UUID managerId) {
        return teamRedisRepository.findByManagerId(userId, managerId)
            .flatMap(entity -> {
                Set<PlayerId> playerIds = new HashSet<>();
                return squadRedisRepository.findPlayerIdsByTeamId(userId, entity.getId())
                    .map(PlayerId::of)
                    .collectList()
                    .map(list -> {
                        playerIds.addAll(list);
                        return entity.toDomain(playerIds);
                    });
            });
    }

    @Override
    public Flux<Team> findAllByUserId(UUID userId) {
        // Primero buscar en Redis
        return teamRedisRepository.findAllByUserId(userId)
            .flatMap(entity -> {
                Set<PlayerId> playerIds = new HashSet<>();
                return squadRedisRepository.findPlayerIdsByTeamId(userId, entity.getId())
                    .map(PlayerId::of)
                    .collectList()
                    .map(list -> {
                        playerIds.addAll(list);
                        return entity.toDomain(playerIds);
                    });
            })
            .collectList()
            .flatMapMany(redisTeams -> {
                if (!redisTeams.isEmpty()) {
                    return Flux.fromIterable(redisTeams);
                }

                // Fallback: buscar en PostgreSQL
                return teamR2dbcRepository.findAll()
                    .flatMap(entity -> {
                        Set<PlayerId> playerIds = new HashSet<>();
                        return squadRedisRepository.findPlayerIdsByTeamId(userId, entity.getId())
                            .map(PlayerId::of)
                            .collectList()
                            .map(list -> {
                                playerIds.addAll(list);
                                return entity.toDomain(playerIds);
                            });
                    });
            });
    }

    @Override
    public Mono<Boolean> existsById(UUID userId, UUID teamId) {
        return teamRedisRepository.existsById(userId, teamId);
    }

    @Override
    public Mono<Void> deleteById(UUID userId, UUID teamId) {
        return teamRedisRepository.deleteById(userId, teamId)
            .then(squadRedisRepository.removeAllPlayersFromTeam(userId, teamId).then());
    }
}
