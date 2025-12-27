package com.footballmanager.infrastructure.persistence;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface MatchR2dbcRepository extends R2dbcRepository<MatchEntity, UUID> {
    
    @Query("SELECT * FROM matches WHERE home_team_id = :teamId OR away_team_id = :teamId")
    Flux<MatchEntity> findByTeamId(UUID teamId);
    
    @Query("SELECT * FROM matches WHERE status = 'SCHEDULED' ORDER BY scheduled_time")
    Flux<MatchEntity> findScheduledMatches();
}
