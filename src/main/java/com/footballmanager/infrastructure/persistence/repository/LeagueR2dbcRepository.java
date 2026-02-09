package com.footballmanager.infrastructure.persistence.repository;

import com.footballmanager.infrastructure.persistence.entity.*;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.Modifying;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Repository
public interface LeagueR2dbcRepository extends R2dbcRepository<LeagueEntity, UUID> {
    Flux<LeagueEntity> findByCountry(String country);

    @org.springframework.data.r2dbc.repository.Query("INSERT INTO leagues (id, name, country, created_at, updated_at) VALUES ($1, $2, $3, $4, $5) RETURNING id, name, country, created_at, updated_at")
    Mono<LeagueEntity> insertWithId(UUID id, String name, String country, java.time.Instant createdAt, java.time.Instant updatedAt);
}

