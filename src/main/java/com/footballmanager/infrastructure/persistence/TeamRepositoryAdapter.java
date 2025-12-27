package com.footballmanager.infrastructure.persistence;

import com.footballmanager.domain.model.*;
import com.footballmanager.domain.ports.out.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;

@Component
@RequiredArgsConstructor
public class TeamRepositoryAdapter implements TeamRepository {
    
    private final TeamR2dbcRepository r2dbcRepository;
    
    @Override
    public Mono<Void> save(Team team) {
        TeamEntity entity = TeamEntity.fromDomain(team);
        return r2dbcRepository.save(entity)
            .then();
    }
    
    @Override
    public Mono<Team> findById(TeamId id) {
        return r2dbcRepository.findById(id.getValue())
            .map(entity -> entity.toDomain(new HashSet<>()));
    }
    
    @Override
    public Flux<Team> findByManagerId(UserId managerId) {
        return r2dbcRepository.findByManagerId(managerId.getValue())
            .map(entity -> entity.toDomain(new HashSet<>()));
    }
    
    @Override
    public Flux<Team> findAll() {
        return r2dbcRepository.findAll()
            .map(entity -> entity.toDomain(new HashSet<>()));
    }
    
    @Override
    public Mono<Boolean> existsById(TeamId id) {
        return r2dbcRepository.existsById(id.getValue());
    }
    
    @Override
    public Mono<Void> deleteById(TeamId id) {
        return r2dbcRepository.deleteById(id.getValue());
    }
}
