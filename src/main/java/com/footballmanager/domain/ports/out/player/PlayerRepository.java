package com.footballmanager.domain.ports.out.player;

import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.view.WorldPlayerOvrProjection;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PlayerRepository {
    Mono<Player> save(UUID userId, Player player);
    Mono<Player> findById(UUID userId, UUID playerId);
    Flux<Player> findAvailablePlayersByUserId(UUID userId);
    Flux<Player> findAllByUserId(UUID userId);
    Mono<Boolean> existsById(UUID userId, UUID playerId);
    Mono<Void> deleteById(UUID userId, UUID playerId);

    // Métodos de solo lectura desde SQL (para inicialización del WorldSnapshot)
    Flux<Player> findByTeamId(UUID teamId);
    /** Reads canonical players grouped by team in one database query. */
    Mono<java.util.Map<UUID, java.util.List<Player>>> findAllByTeamFromDatabase();
    /** Reads only the raw fields required by the canonical WorldPlayer OVR. */
    Mono<List<WorldPlayerOvrProjection>> findPlayersForOvrFromDatabase();
    Flux<PlayerSpecialTrait> findSpecialTraitsByPlayerIds(Collection<UUID> playerIds);
    Mono<Void> addPlayerToTeamSquad(UUID teamId, UUID playerId);
    Mono<Void> removePlayerFromTeamSquad(UUID teamId, UUID playerId);

}

