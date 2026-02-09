package com.footballmanager.infrastructure.persistence.repository;

import com.footballmanager.infrastructure.persistence.entity.*;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface UserR2dbcRepository extends R2dbcRepository<UserEntity, UUID> {
    Mono<UserEntity> findByEmail(String email);
    Mono<UserEntity> findByUsername(String username);
}

