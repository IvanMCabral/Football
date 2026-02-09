package com.footballmanager.infrastructure.persistence.repository;

import com.footballmanager.infrastructure.persistence.entity.*;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface TeamSquadR2dbcRepository extends ReactiveCrudRepository<TeamSquadEntity, Long> {

    @Query("INSERT INTO team_squad (team_id, player_id) VALUES (:teamId, :playerId) ON CONFLICT DO NOTHING")
    Mono<Void> addPlayerToTeam(UUID teamId, UUID playerId);

    @Query("DELETE FROM team_squad WHERE team_id = :teamId AND player_id = :playerId")
    Mono<Void> removePlayerFromTeam(UUID teamId, UUID playerId);

    @Query("DELETE FROM team_squad WHERE team_id = :teamId")
    Mono<Void> removeAllPlayersFromTeam(UUID teamId);

    @Query("SELECT player_id FROM team_squad WHERE team_id = :teamId")
    Flux<UUID> findPlayerIdsByTeamId(UUID teamId);
}

