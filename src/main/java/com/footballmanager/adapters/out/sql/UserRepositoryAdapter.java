package com.footballmanager.adapters.out.sql;

import com.footballmanager.infrastructure.persistence.entity.*;
import com.footballmanager.infrastructure.persistence.repository.*;

import com.footballmanager.domain.model.aggregate.User;
import com.footballmanager.domain.ports.out.user.UserRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import com.footballmanager.application.observability.RuntimeOperationMetrics;

@Component
public class UserRepositoryAdapter implements UserRepository {
    private final UserR2dbcRepository r2dbcRepository;

    public UserRepositoryAdapter(UserR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    @Override
    public Mono<User> save(User user) {
        // Pasar el id para que haga UPDATE, no INSERT
        UserEntity entity = new UserEntity(
            user.getId() != null ? user.getId().getValue() : null,
            user.getEmail(),
            user.getUsername(),
            user.getPasswordHash(),
            user.getRole().name(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.getTeamId() // UUID directo, no .toString()
        );
        return RuntimeOperationMetrics.measure("postgres.user.save",
            r2dbcRepository.save(entity).map(UserEntity::toDomain));
    }

    @Override
    public Mono<User> createNew(String email, String username, String passwordHash) {
        UserEntity entity = new UserEntity(
            null,
            email,
            username,
            passwordHash,
            "USER",
            java.time.Instant.now(),
            java.time.Instant.now(),
            null
        );
        return RuntimeOperationMetrics.measure("postgres.user.create",
            r2dbcRepository.save(entity).map(UserEntity::toDomain));
    }

    @Override
    public Mono<User> findById(java.util.UUID id) {
        return RuntimeOperationMetrics.measure("postgres.user.findById",
            r2dbcRepository.findById(id).map(UserEntity::toDomain));
    }

    @Override
    public Mono<User> findByEmail(String email) {
        return RuntimeOperationMetrics.measure("postgres.user.findByEmail",
            r2dbcRepository.findByEmail(email).map(UserEntity::toDomain));
    }

    @Override
    public Mono<User> findByUsername(String username) {
        return RuntimeOperationMetrics.measure("postgres.user.findByUsername",
            r2dbcRepository.findByUsername(username).map(UserEntity::toDomain));
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return RuntimeOperationMetrics.measure("postgres.user.existsByEmail",
            r2dbcRepository.findByEmail(email).hasElement());
    }

    @Override
    public Mono<Boolean> existsByUsername(String username) {
        return RuntimeOperationMetrics.measure("postgres.user.existsByUsername",
            r2dbcRepository.findByUsername(username).hasElement());
    }

    @Override
    public Mono<Void> deleteById(java.util.UUID id) {
        return RuntimeOperationMetrics.measure("postgres.user.delete", r2dbcRepository.deleteById(id));
    }
}

