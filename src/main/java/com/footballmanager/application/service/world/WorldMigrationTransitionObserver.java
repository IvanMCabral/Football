package com.footballmanager.application.service.world;

import reactor.core.publisher.Mono;

import java.util.UUID;

/** Operational checkpoint invoked after PREPARED is durable and before commit. */
@FunctionalInterface
public interface WorldMigrationTransitionObserver {
    Mono<Decision> afterPrepared(UUID ownerId);

    enum Decision { CONTINUE, PAUSE }

    static WorldMigrationTransitionObserver noOp() {
        return ownerId -> Mono.just(Decision.CONTINUE);
    }
}
