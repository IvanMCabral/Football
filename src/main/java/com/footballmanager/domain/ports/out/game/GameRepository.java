package com.footballmanager.domain.ports.out.game;

import com.footballmanager.domain.model.aggregate.Game;
import com.footballmanager.domain.model.valueobject.GameId;
import com.footballmanager.domain.model.valueobject.UserId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GameRepository {
    Mono<Game> save(UUID userId, Game game);
    Mono<Game> findById(UUID userId, GameId id);
    Flux<Game> findByUserId(UUID userId, UserId userIdParam);
    Flux<Game> findAll(UUID userId);
    Mono<Void> deleteById(UUID userId, GameId id);
}
