package com.footballmanager.domain.ports.out;

import com.footballmanager.domain.model.Match;
import com.footballmanager.domain.model.MatchId;
import com.footballmanager.domain.model.TeamId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MatchRepository {
    Mono<Void> save(Match match);
    
    Mono<Match> findById(MatchId id);
    
    Flux<Match> findByTeamId(TeamId teamId);
    
    Flux<Match> findScheduledMatches();
    
    Flux<Match> findAll();
    
    Mono<Boolean> existsById(MatchId id);
    
    Mono<Void> deleteById(MatchId id);
}
