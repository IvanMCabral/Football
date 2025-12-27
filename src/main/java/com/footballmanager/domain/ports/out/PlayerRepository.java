package com.footballmanager.domain.ports.out;

import com.footballmanager.domain.model.Player;
import com.footballmanager.domain.model.PlayerId;
import com.footballmanager.domain.model.TeamId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlayerRepository {
    Mono<Void> save(Player player);
    
    Mono<Player> findById(PlayerId id);
    
    Flux<Player> findByTeamId(TeamId teamId);
    
    Flux<Player> findAvailablePlayers();
    
    Flux<Player> findAll();
    
    Mono<Boolean> existsById(PlayerId id);
    
    Mono<Void> deleteById(PlayerId id);
}
