package com.footballmanager.adapters.out.sql;

import com.footballmanager.domain.model.entity.Match;
import com.footballmanager.domain.model.valueobject.GameId;
import com.footballmanager.domain.model.valueobject.MatchId;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.ports.out.match.MatchRepository;
import com.footballmanager.infrastructure.persistence.entity.MatchEntity;
import com.footballmanager.infrastructure.persistence.redis.MatchRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Primary
@RequiredArgsConstructor
public class MatchRepositoryAdapter implements MatchRepository {
    private final MatchRedisRepository redisRepository;

    @Override
    public Mono<Void> save(UUID userId, Match match) {
        MatchEntity entity = MatchEntity.fromDomain(match);
        UUID gameId = match.getGameId() != null ? match.getGameId().getValue() : null;
        return redisRepository.save(userId, gameId, entity).then();
    }

    @Override
    public Mono<Match> findById(UUID userId, MatchId id) {
        return redisRepository.findById(userId, null, id.getValue())
            .map(MatchEntity::toDomain);
    }

    @Override
    public Flux<Match> findByTeamId(UUID userId, TeamId teamId) {
        return redisRepository.findByTeamId(userId, teamId.getValue())
            .map(MatchEntity::toDomain);
    }

    @Override
    public Flux<Match> findScheduledMatches(UUID userId) {
        return redisRepository.findScheduledMatches(userId)
            .map(MatchEntity::toDomain);
    }

    @Override
    public Flux<Match> findAll(UUID userId) {
        return redisRepository.findAllByUserId(userId)
            .map(MatchEntity::toDomain);
    }

    @Override
    public Flux<Match> findByGameId(UUID userId, GameId gameId) {
        return redisRepository.findByGameId(userId, gameId.getValue())
            .map(MatchEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(UUID userId, MatchId id) {
        return redisRepository.findById(userId, null, id.getValue())
            .hasElement();
    }

    @Override
    public Mono<Void> deleteById(UUID userId, MatchId id) {
        return redisRepository.deleteById(userId, null, id.getValue()).then();
    }
}
