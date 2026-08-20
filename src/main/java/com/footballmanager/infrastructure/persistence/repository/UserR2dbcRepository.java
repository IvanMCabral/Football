package com.footballmanager.infrastructure.persistence.repository;

import com.footballmanager.infrastructure.persistence.entity.*;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import com.footballmanager.application.service.world.DurableBoundaryClassification;
import com.footballmanager.application.service.world.DurablePersistenceBoundary;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.time.Instant;

@Repository
@DurableBoundaryClassification(value = DurablePersistenceBoundary.Classification.OUT_OF_SCOPE_NON_WORLD_IDENTITY,
        reason = "R2DBC user repository is the canonical identity source, not World V2 state")
public interface UserR2dbcRepository extends R2dbcRepository<UserEntity, UUID> {
    @org.springframework.data.r2dbc.repository.Query("""
        INSERT INTO users (id, email, username, password_hash, role, created_at, updated_at)
        VALUES (:id, :email, :username, :passwordHash, :role, :createdAt, :updatedAt)
        RETURNING id, email, username, password_hash, role, created_at, updated_at, team_id
        """)
    Mono<UserEntity> insertNew(UUID id, String email, String username, String passwordHash,
                               String role, Instant createdAt, Instant updatedAt);

    Mono<UserEntity> findByEmail(String email);
    Mono<UserEntity> findByUsername(String username);
    Mono<UserEntity> findByTeamId(UUID teamId);
}

