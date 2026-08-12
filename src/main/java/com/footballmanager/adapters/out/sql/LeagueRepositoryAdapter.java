package com.footballmanager.adapters.out.sql;

import com.footballmanager.domain.model.aggregate.League;
import com.footballmanager.domain.model.valueobject.LeagueId;
import com.footballmanager.domain.ports.out.league.LeagueRepository;
import com.footballmanager.infrastructure.persistence.entity.LeagueEntity;
import com.footballmanager.infrastructure.persistence.redis.LeagueRedisRepository;
import com.footballmanager.infrastructure.persistence.repository.LeagueR2dbcRepository;
import com.footballmanager.application.service.world.DurableBoundaryClassification;
import com.footballmanager.application.service.world.DurablePersistenceBoundary;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Primary
@RequiredArgsConstructor
@DurableBoundaryClassification(value = DurablePersistenceBoundary.Classification.OUT_OF_SCOPE_CANONICAL_SOURCE,
        reason = "League adapter persists canonical PostgreSQL catalog data, not owner-scoped World V2 state")
public class LeagueRepositoryAdapter implements LeagueRepository {

    private final LeagueRedisRepository redisRepository;
    private final LeagueR2dbcRepository leagueR2dbcRepository;

    @Override
    public Mono<League> save(UUID userId, League league) {
        LeagueEntity entity = LeagueEntity.fromDomain(league);
        return redisRepository.save(userId, entity)
            .thenReturn(league);
    }

    @Override
    public Mono<League> findById(UUID userId, LeagueId id) {
        return redisRepository.findById(userId, id.getValue())
            .map(LeagueEntity::toDomain);
    }

    @Override
    public Flux<League> findByCountry(UUID userId, String country) {
        return redisRepository.findByCountry(userId, country)
            .map(LeagueEntity::toDomain);
    }

    @Override
    public Flux<League> findAll(UUID userId) {
        return redisRepository.findAllByUserId(userId)
            .map(LeagueEntity::toDomain)
            .collectList()
            .flatMapMany(redisLeagues -> {
                if (!redisLeagues.isEmpty()) {
                    return Flux.fromIterable(redisLeagues);
                }

                return leagueR2dbcRepository.findAll()
                    .map(LeagueEntity::toDomain)
                    .flatMap(league -> redisRepository.save(userId, LeagueEntity.fromDomain(league))
                        .thenReturn(league));
            });
    }

    @Override
    public Flux<League> findAllCanonical() {
        return leagueR2dbcRepository.findAll().map(LeagueEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(UUID userId, LeagueId id) {
        return redisRepository.findById(userId, id.getValue())
            .hasElement();
    }

    @Override
    public Mono<Void> deleteById(UUID userId, LeagueId id) {
        return redisRepository.deleteById(userId, id.getValue()).then();
    }
}
