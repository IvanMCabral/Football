package com.footballmanager.adapters.out.sql;

import com.footballmanager.domain.model.aggregate.Game;
import com.footballmanager.domain.model.valueobject.GameId;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.model.valueobject.UserId;
import com.footballmanager.domain.ports.out.game.GameRepository;
import com.footballmanager.infrastructure.persistence.entity.GameEntity;
import com.footballmanager.infrastructure.persistence.redis.GameRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Primary
@RequiredArgsConstructor
public class GameRepositoryAdapter implements GameRepository {
    private final GameRedisRepository redisRepository;

    @Override
    public Mono<Game> save(UUID userId, Game game) {
        GameEntity entity = new GameEntity(
            game.getId() != null ? game.getId().getValue() : null,
            game.getUserId() != null ? game.getUserId().getValue() : null,
            game.getTeamId() != null ? game.getTeamId().getValue() : null,
            game.getName(),
            game.getCreatedAt()
        );
        return redisRepository.save(userId, entity)
            .thenReturn(game);
    }

    @Override
    public Mono<Game> findById(UUID userId, GameId id) {
        return redisRepository.findById(userId, id.getValue())
            .map(GameEntity::toDomain);
    }

    @Override
    public Flux<Game> findByUserId(UUID userId, UserId userIdParam) {
        return redisRepository.findAllByUserId(userId)
            .map(GameEntity::toDomain);
    }

    @Override
    public Flux<Game> findAll(UUID userId) {
        return redisRepository.findAllByUserId(userId)
            .map(GameEntity::toDomain);
    }

    @Override
    public Mono<Void> deleteById(UUID userId, GameId id) {
        return redisRepository.deleteById(userId, id.getValue()).then();
    }
}
