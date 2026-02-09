package com.footballmanager.adapters.out.sql;

import com.footballmanager.infrastructure.persistence.entity.*;
import com.footballmanager.infrastructure.persistence.repository.*;
import com.footballmanager.infrastructure.persistence.redis.PlayerRedisRepository;

import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Primary
@RequiredArgsConstructor
public class PlayerRepositoryAdapter implements PlayerRepository {
    private final PlayerR2dbcRepository r2dbcRepository;
    private final TeamSquadR2dbcRepository squadRepository;
    private final PlayerRedisRepository redisRepository;

    // ========== Métodos con userId (Redis - scope de usuario) ==========

    @Override
    public Mono<Player> save(java.util.UUID userId, Player player) {
        PlayerEntity entity = PlayerEntity.fromDomainForInsert(player);
        return redisRepository.save(userId, entity)
            .then(Mono.just(player));
    }

    @Override
    public Mono<Player> findById(java.util.UUID userId, java.util.UUID playerId) {
        return redisRepository.findById(userId, playerId.toString())
            .map(PlayerEntity::toDomain);
    }

    @Override
    public Flux<Player> findAvailablePlayersByUserId(java.util.UUID userId) {
        return redisRepository.findAllByUserId(userId)
            .filter(p -> p != null)
            .map(PlayerEntity::toDomain);
    }

    @Override
    public Flux<Player> findAllByUserId(java.util.UUID userId) {
        return redisRepository.findAllByUserId(userId)
            .map(PlayerEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(java.util.UUID userId, java.util.UUID playerId) {
        return redisRepository.findById(userId, playerId.toString())
            .hasElement();
    }

    @Override
    public Mono<Void> deleteById(java.util.UUID userId, java.util.UUID playerId) {
        return redisRepository.deleteById(userId, playerId.toString())
            .then();
    }

    // ========== Eliminados métodos legacy de SQL - ahora solo Redis ==========

    // ========== Métodos específicos para equipos (DB) ==========

    @Override
    public Flux<Player> findByTeamId(java.util.UUID teamId) {
        return r2dbcRepository.findByTeamId(teamId)
                .map(PlayerEntity::toDomain);
    }

    @Override
    public Mono<Void> addPlayerToTeamSquad(java.util.UUID teamId, java.util.UUID playerId) {
        return squadRepository.addPlayerToTeam(teamId, playerId);
    }

    @Override
    public Mono<Void> removePlayerFromTeamSquad(java.util.UUID teamId, java.util.UUID playerId) {
        return squadRepository.removePlayerFromTeam(teamId, playerId);
    }
}

