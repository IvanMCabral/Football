package com.footballmanager.domain.port.in.player;

import com.footballmanager.domain.model.entity.Player;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PlayerManagementUseCase {
    Mono<Player> createPlayer(UUID userId, Player player);
    Mono<Player> getPlayer(UUID userId, UUID playerId);
    Flux<Player> getAllPlayersByUserId(UUID userId);
    Flux<Player> getAvailablePlayersByUserId(UUID userId);
    Mono<Player> updatePlayer(UUID userId, UUID playerId, Player player);
    Mono<Void> deletePlayer(UUID userId, UUID playerId);
    Mono<Player> updatePlayerAttributes(UUID userId, UUID playerId, int skillChange);
    Flux<Player> getPlayersByTeam(UUID teamId);
}
