package com.footballmanager.infrastructure.persistence.repository;

import com.footballmanager.infrastructure.persistence.entity.*;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import com.footballmanager.application.service.world.DurableBoundaryClassification;
import com.footballmanager.application.service.world.DurablePersistenceBoundary;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@DurableBoundaryClassification(value = DurablePersistenceBoundary.Classification.OUT_OF_SCOPE_CANONICAL_SOURCE,
        reason = "R2DBC player repository is the canonical PostgreSQL catalog source")
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

