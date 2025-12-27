package com.footballmanager.infrastructure.persistence;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

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
}
