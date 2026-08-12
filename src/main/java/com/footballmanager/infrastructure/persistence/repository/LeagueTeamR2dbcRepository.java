package com.footballmanager.infrastructure.persistence.repository;

import com.footballmanager.infrastructure.persistence.entity.LeagueTeamEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import com.footballmanager.application.service.world.DurableBoundaryClassification;
import com.footballmanager.application.service.world.DurablePersistenceBoundary;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Repositorio R2DBC para leer relaciones Liga-Equipo directamente desde PostgreSQL.
 * Solo lectura - las escrituras se hacen en Redis.
 */
@Repository
@DurableBoundaryClassification(value = DurablePersistenceBoundary.Classification.OUT_OF_SCOPE_CANONICAL_SOURCE,
        reason = "R2DBC league-team repository reads canonical PostgreSQL relations")
public interface LeagueTeamR2dbcRepository extends ReactiveCrudRepository<LeagueTeamEntity, Long> {

    Flux<LeagueTeamEntity> findByLeagueId(UUID leagueId);

    Flux<LeagueTeamEntity> findByTeamId(UUID teamId);

    @Query("SELECT * FROM league_teams WHERE league_id = :leagueId")
    Flux<LeagueTeamEntity> findAllByLeagueId(UUID leagueId);

    @Query("SELECT league_id, team_id FROM league_teams")
    Flux<LeagueTeamEntity> findAllRelations();
}
