package com.footballmanager.infrastructure.persistence;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface TeamR2dbcRepository extends R2dbcRepository<TeamEntity, UUID> {
    
    Flux<TeamEntity> findByManagerId(UUID managerId);
    
    Mono<TeamEntity> findByName(String name);
    
    Mono<Boolean> existsByName(String name);
}
