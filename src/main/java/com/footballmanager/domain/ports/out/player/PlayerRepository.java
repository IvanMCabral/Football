package com.footballmanager.domain.ports.out.player;

import com.footballmanager.domain.model.entity.Player;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PlayerRepository {
    // Métodos con userId para Redis (scope de usuario) - ÚNICOS MÉTODOS DE ESCRITURA
    Mono<Player> save(UUID userId, Player player);
    Mono<Player> findById(UUID userId, UUID playerId);
    Flux<Player> findAvailablePlayersByUserId(UUID userId);
    Flux<Player> findAllByUserId(UUID userId);
    Mono<Boolean> existsById(UUID userId, UUID playerId);
    Mono<Void> deleteById(UUID userId, UUID playerId);

    // Métodos de solo lectura desde SQL (para inicialización del WorldSnapshot)
    Flux<Player> findByTeamId(UUID teamId);
    Mono<Void> addPlayerToTeamSquad(UUID teamId, UUID playerId);
    Mono<Void> removePlayerFromTeamSquad(UUID teamId, UUID playerId);
}

