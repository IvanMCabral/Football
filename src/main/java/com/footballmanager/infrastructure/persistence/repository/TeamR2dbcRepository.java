package com.footballmanager.infrastructure.persistence.repository;

import com.footballmanager.infrastructure.persistence.entity.*;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface TeamR2dbcRepository extends R2dbcRepository<TeamEntity, UUID> {
    // Logging will be handled in the controller/service since this is an interface.
    Flux<TeamEntity> findByManagerId(UUID managerId);
    Mono<TeamEntity> findByName(String name);
    Mono<Boolean> existsByName(String name);
}

