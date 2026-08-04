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

    /**
     * Reads the canonical world teams directly from PostgreSQL when creating a
     * per-user world snapshot. This avoids the per-user Redis lookup path,
     * which is intentionally empty for a newly registered manager.
     */
    Flux<Team> findAllFromDatabase();
    Mono<Boolean> existsById(UUID userId, UUID teamId);
    Mono<Void> deleteById(UUID userId, UUID teamId);
}
