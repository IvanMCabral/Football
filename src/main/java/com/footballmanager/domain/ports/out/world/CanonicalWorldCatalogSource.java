package com.footballmanager.domain.ports.out.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Rebuilds canonical world data from durable authorities without owner overlay state. */
public interface CanonicalWorldCatalogSource {
    Mono<WorldSnapshot> rebuild(UUID ownerId);
}
