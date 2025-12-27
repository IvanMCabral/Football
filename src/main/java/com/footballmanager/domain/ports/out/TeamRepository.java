package com.footballmanager.domain.ports.out;

import com.footballmanager.domain.model.Team;
import com.footballmanager.domain.model.TeamId;
import com.footballmanager.domain.model.UserId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TeamRepository {
    Mono<Void> save(Team team);
    
    Mono<Team> findById(TeamId id);
    
    Flux<Team> findByManagerId(UserId managerId);
    
    Flux<Team> findAll();
    
    Mono<Boolean> existsById(TeamId id);
    
    Mono<Void> deleteById(TeamId id);
}
