package com.footballmanager.domain.ports.out;

import com.footballmanager.domain.model.League;
import com.footballmanager.domain.model.LeagueId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LeagueRepository {
    Mono<Void> save(League league);
    
    Mono<League> findById(LeagueId id);
    
    Flux<League> findByCountry(String country);
    
    Flux<League> findAll();
    
    Mono<Boolean> existsById(LeagueId id);
    
    Mono<Void> deleteById(LeagueId id);
}
