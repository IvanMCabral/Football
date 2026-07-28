package com.footballmanager.domain.ports.out.league;

import reactor.core.publisher.Flux;

import java.util.UUID;

public interface LeagueTeamSourceRepository {
    Flux<LeagueTeamLink> findByLeagueId(UUID leagueId);
}
