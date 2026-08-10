package com.footballmanager.domain.ports.out.league;

import com.footballmanager.domain.model.valueobject.TeamId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Collection;
import java.util.UUID;

public interface LeagueTeamRepository {
    Mono<UUID> findCommonLeagueId(UUID userId, TeamId homeId, TeamId awayId);
    Mono<Void> validateTeamsInSameLeague(UUID userId, TeamId homeId, TeamId awayId);

    Flux<LeagueTeamLink> findByTeamId(UUID userId, UUID teamId);
    Flux<LeagueTeamLink> findByLeagueId(UUID userId, UUID leagueId);

    Mono<Void> addTeamToLeague(UUID userId, UUID leagueId, UUID teamId);
    default Mono<Void> addTeamsToLeague(UUID userId, UUID leagueId, Collection<UUID> teamIds) {
        return Flux.fromIterable(teamIds)
                .flatMap(teamId -> addTeamToLeague(userId, leagueId, teamId), 16)
                .then();
    }
    Mono<Void> removeTeamFromLeague(UUID userId, UUID leagueId, UUID teamId);
}
