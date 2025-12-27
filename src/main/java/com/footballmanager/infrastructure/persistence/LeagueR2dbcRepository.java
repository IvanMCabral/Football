package com.footballmanager.infrastructure.persistence;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface LeagueR2dbcRepository extends R2dbcRepository<LeagueEntity, UUID> {
    
    Flux<LeagueEntity> findByCountry(String country);
}
