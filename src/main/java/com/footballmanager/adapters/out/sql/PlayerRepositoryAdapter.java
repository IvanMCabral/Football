package com.footballmanager.adapters.out.sql;

import com.footballmanager.infrastructure.persistence.entity.*;
import com.footballmanager.infrastructure.persistence.repository.*;
import com.footballmanager.infrastructure.persistence.redis.PlayerRedisRepository;

import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import com.footballmanager.application.service.world.DurableBoundaryClassification;
import com.footballmanager.application.service.world.DurablePersistenceBoundary;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

@Component
@Primary
@RequiredArgsConstructor
@DurableBoundaryClassification(value = DurablePersistenceBoundary.Classification.OUT_OF_SCOPE_CANONICAL_SOURCE,
        reason = "Player adapter persists canonical PostgreSQL catalog data")
public class PlayerRepositoryAdapter implements PlayerRepository {
    private final PlayerR2dbcRepository r2dbcRepository;
    private final TeamSquadR2dbcRepository squadRepository;
    private final PlayerRedisRepository redisRepository;
    private final DatabaseClient databaseClient;

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
    public Mono<java.util.Map<UUID, java.util.List<Player>>> findAllByTeamFromDatabase() {
        return databaseClient.sql("""
                        SELECT ts.team_id, p.*
                        FROM players p
                        INNER JOIN team_squad ts ON p.id = ts.player_id
                        ORDER BY ts.team_id, p.id
                        """)
                .map((row, metadata) -> new java.util.AbstractMap.SimpleEntry<>(
                        row.get("team_id", UUID.class),
                        PlayerEntity.fromRow(row).toDomain()))
                .all()
                .collectMultimap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue)
                .map(multimap -> multimap.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                java.util.Map.Entry::getKey,
                                entry -> java.util.List.copyOf(entry.getValue()))));
    }

    @Override
    public Flux<PlayerSpecialTrait> findSpecialTraitsByPlayerIds(Collection<UUID> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return Flux.empty();
        }

        return databaseClient.sql("""
                        SELECT psa.player_id, sa.code, sa.name, sa.description
                        FROM player_special_attributes psa
                        INNER JOIN special_attributes sa ON sa.id = psa.special_attribute_id
                        WHERE psa.player_id IN (:playerIds)
                        ORDER BY psa.player_id, psa.slot
                        """)
                .bind("playerIds", playerIds)
                .map((row, metadata) -> new PlayerSpecialTrait(
                        row.get("player_id", UUID.class),
                        row.get("code", String.class),
                        row.get("name", String.class),
                        row.get("description", String.class)))
                .all();
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

