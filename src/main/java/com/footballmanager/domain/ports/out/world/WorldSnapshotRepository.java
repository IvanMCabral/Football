package com.footballmanager.domain.ports.out.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface WorldSnapshotRepository {

    Mono<WorldSnapshot> save(WorldSnapshot snapshot);

    Mono<WorldSnapshot> findByUserId(UUID userId);

    Mono<Boolean> existsByUserId(UUID userId);

    Mono<Boolean> deleteByUserId(UUID userId);
}
