package com.footballmanager.domain.port.in.league;

import com.footballmanager.domain.model.aggregate.League;
import com.footballmanager.domain.model.aggregate.Team;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface LeagueManagementUseCase {
    Mono<League> createLeague(UUID userId, League league);
    Mono<League> getLeague(UUID userId, UUID leagueId);
    Flux<League> getAllLeagues(UUID userId);
    Mono<League> updateLeague(UUID userId, UUID leagueId, League league);
    Mono<Void> deleteLeague(UUID userId, UUID leagueId);
    Mono<League> startLeague(UUID userId, UUID leagueId);
    Mono<League> finishLeague(UUID userId, UUID leagueId);
    Flux<Team> getTeamsInLeague(UUID userId, UUID leagueId);
    Mono<Void> addTeamToLeague(UUID userId, UUID leagueId, UUID teamId);
    Mono<Void> removeTeamFromLeague(UUID userId, UUID leagueId, UUID teamId);
}
