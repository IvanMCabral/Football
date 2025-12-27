package com.footballmanager.infrastructure.persistence;

import com.footballmanager.domain.model.Match;
import com.footballmanager.domain.model.MatchId;
import com.footballmanager.domain.model.TeamId;
import com.footballmanager.domain.ports.out.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MatchRepositoryAdapter implements MatchRepository {
    
    private final MatchR2dbcRepository r2dbcRepository;
    
    @Override
    public Mono<Void> save(Match match) {
        MatchEntity entity = MatchEntity.fromDomain(match);
        return r2dbcRepository.save(entity)
            .then();
    }
    
    @Override
    public Mono<Match> findById(MatchId id) {
        return r2dbcRepository.findById(id.getValue())
            .map(MatchEntity::toDomain);
    }
    
    @Override
    public Flux<Match> findByTeamId(TeamId teamId) {
        return r2dbcRepository.findByTeamId(teamId.getValue())
            .map(MatchEntity::toDomain);
    }
    
    @Override
    public Flux<Match> findScheduledMatches() {
        return r2dbcRepository.findScheduledMatches()
            .map(MatchEntity::toDomain);
    }
    
    @Override
    public Flux<Match> findAll() {
        return r2dbcRepository.findAll()
            .map(MatchEntity::toDomain);
    }
    
    @Override
    public Mono<Boolean> existsById(MatchId id) {
        return r2dbcRepository.existsById(id.getValue());
    }
    
    @Override
    public Mono<Void> deleteById(MatchId id) {
        return r2dbcRepository.deleteById(id.getValue());
    }
}
