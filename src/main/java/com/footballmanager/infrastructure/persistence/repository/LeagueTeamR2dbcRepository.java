package com.footballmanager.infrastructure.persistence.repository;

import com.footballmanager.infrastructure.persistence.entity.LeagueTeamEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Repositorio R2DBC para leer relaciones Liga-Equipo directamente desde PostgreSQL.
 * Solo lectura - las escrituras se hacen en Redis.
 */
@Repository
public interface LeagueTeamR2dbcRepository extends ReactiveCrudRepository<LeagueTeamEntity, Long> {

    Flux<LeagueTeamEntity> findByLeagueId(UUID leagueId);

    Flux<LeagueTeamEntity> findByTeamId(UUID teamId);

    @Query("SELECT * FROM league_teams WHERE league_id = :leagueId")
    Flux<LeagueTeamEntity> findAllByLeagueId(UUID leagueId);
}
