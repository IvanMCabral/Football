package com.footballmanager.infrastructure.persistence.repository;

import com.footballmanager.infrastructure.persistence.entity.*;


import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface PlayerR2dbcRepository extends R2dbcRepository<PlayerEntity, UUID> {
    @Query("""
            SELECT p.* FROM players p
            INNER JOIN team_squad ts ON p.id = ts.player_id
            WHERE ts.team_id = :teamId
            """)
    Flux<PlayerEntity> findByTeamId(UUID teamId);

    @Query("""
            SELECT p.* FROM players p
            WHERE p.id NOT IN (SELECT player_id FROM team_squad)
            """)
    Flux<PlayerEntity> findAvailable();

        @Query("""
                INSERT INTO players (id, name, age, position, attack, defense, technique, speed, stamina, mentality, market_value, energy, injured, created_at, updated_at)
                VALUES (:id, :name, :age, :position, :attack, :defense, :technique, :speed, :stamina, :mentality, :marketValue, :energy, :injured, :createdAt, :updatedAt)
                """)
        Mono<Void> insertPlayer(
                UUID id,
                String name,
                int age,
                String position,
                int attack,
                int defense,
                int technique,
                int speed,
                int stamina,
                int mentality,
                BigDecimal marketValue,
                int energy,
                boolean injured,
                Instant createdAt,
                Instant updatedAt
        );

    @Query("""
            INSERT INTO team_squad (team_id, player_id)
            VALUES (:teamId, :playerId)
            """)
    Mono<Void> addPlayerToTeamSquad(UUID teamId, UUID playerId);

    @Query("""
            DELETE FROM team_squad
            WHERE team_id = :teamId AND player_id = :playerId
            """)
    Mono<Void> removePlayerFromTeamSquad(UUID teamId, UUID playerId);
}

