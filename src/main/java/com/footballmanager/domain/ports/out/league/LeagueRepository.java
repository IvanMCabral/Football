package com.footballmanager.domain.ports.out.league;

import com.footballmanager.domain.model.aggregate.League;
import com.footballmanager.domain.model.valueobject.LeagueId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface LeagueRepository {
    Mono<League> save(UUID userId, League league);
    Mono<League> findById(UUID userId, LeagueId id);
    Flux<League> findByCountry(UUID userId, String country);
    Flux<League> findAll(UUID userId);
    Mono<Boolean> existsById(UUID userId, LeagueId id);
    Mono<Void> deleteById(UUID userId, LeagueId id);
}
