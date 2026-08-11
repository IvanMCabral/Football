package com.footballmanager.domain.ports.out.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import reactor.core.publisher.Mono;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;

import java.util.UUID;

public interface WorldSnapshotRepository {

    String CANONICAL_BOOTSTRAP_CONTEXT_KEY = WorldSnapshotRepository.class.getName() + ".canonicalBootstrap";

    Mono<WorldSnapshot> save(WorldSnapshot snapshot);

    default Mono<WorldSnapshot> saveInitial(WorldSnapshot snapshot) {
        return save(snapshot);
    }

    default Mono<WorldSnapshot> saveWithContext(CareerWriteContext context, WorldSnapshot snapshot) {
        return save(snapshot);
    }

    Mono<WorldSnapshot> findByUserId(UUID userId);

    Mono<Boolean> existsByUserId(UUID userId);

    Mono<Boolean> deleteByUserId(UUID userId);
}
