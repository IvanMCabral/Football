package com.footballmanager.domain.ports.out.league;

import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.infrastructure.persistence.entity.LeagueTeamEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface LeagueTeamRepository {
    Mono<UUID> findCommonLeagueId(UUID userId, TeamId homeId, TeamId awayId);
    Mono<Void> validateTeamsInSameLeague(UUID userId, TeamId homeId, TeamId awayId);

    Flux<LeagueTeamEntity> findByTeamId(UUID userId, UUID teamId);
    Flux<LeagueTeamEntity> findByLeagueId(UUID userId, UUID leagueId);

    Mono<Void> addTeamToLeague(UUID userId, UUID leagueId, UUID teamId);
    Mono<Void> removeTeamFromLeague(UUID userId, UUID leagueId, UUID teamId);
}
