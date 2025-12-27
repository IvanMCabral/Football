package com.footballmanager.infrastructure.persistence;

import com.footballmanager.domain.model.League;
import com.footballmanager.domain.model.LeagueId;
import com.footballmanager.domain.ports.out.LeagueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class LeagueRepositoryAdapter implements LeagueRepository {
    
    private final LeagueR2dbcRepository r2dbcRepository;
    
    @Override
    public Mono<Void> save(League league) {
        LeagueEntity entity = LeagueEntity.fromDomain(league);
        return r2dbcRepository.save(entity)
            .then();
    }
    
    @Override
    public Mono<League> findById(LeagueId id) {
        return r2dbcRepository.findById(id.getValue())
            .map(LeagueEntity::toDomain);
    }
    
    @Override
    public Flux<League> findByCountry(String country) {
        return r2dbcRepository.findByCountry(country)
            .map(LeagueEntity::toDomain);
    }
    
    @Override
    public Flux<League> findAll() {
        return r2dbcRepository.findAll()
            .map(LeagueEntity::toDomain);
    }
    
    @Override
    public Mono<Boolean> existsById(LeagueId id) {
        return r2dbcRepository.existsById(id.getValue());
    }
    
    @Override
    public Mono<Void> deleteById(LeagueId id) {
        return r2dbcRepository.deleteById(id.getValue());
    }
}
