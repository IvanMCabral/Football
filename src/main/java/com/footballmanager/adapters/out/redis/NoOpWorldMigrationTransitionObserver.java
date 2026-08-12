package com.footballmanager.adapters.out.redis;

import com.footballmanager.application.service.world.WorldMigrationTransitionObserver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Default production checkpoint observer; tests may inject deterministic interruption. */
@Component
public final class NoOpWorldMigrationTransitionObserver implements WorldMigrationTransitionObserver {
    @Override
    public Mono<Decision> afterPrepared(UUID ownerId) {
        return Mono.just(Decision.CONTINUE);
    }
}
