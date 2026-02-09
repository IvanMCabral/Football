package com.footballmanager.domain.ports.out.team;

import com.footballmanager.domain.model.aggregate.Team;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TeamRepository {
    Mono<Team> save(UUID userId, Team team);
    Mono<Team> findById(UUID userId, UUID teamId);
    Flux<Team> findByManagerId(UUID userId, UUID managerId);
    Flux<Team> findAllByUserId(UUID userId);
    Mono<Boolean> existsById(UUID userId, UUID teamId);
    Mono<Void> deleteById(UUID userId, UUID teamId);
}
