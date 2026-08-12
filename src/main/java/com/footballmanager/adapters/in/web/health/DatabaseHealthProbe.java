package com.footballmanager.adapters.in.web.health;

import com.footballmanager.application.service.world.DurableBoundaryClassification;
import com.footballmanager.application.service.world.DurablePersistenceBoundary;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@DurableBoundaryClassification(value = DurablePersistenceBoundary.Classification.OUT_OF_SCOPE_WITH_EXPLICIT_REASON,
        reason = "database health probe reads connectivity only; it does not persist a World V2 root")
public class DatabaseHealthProbe {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    private final R2dbcEntityTemplate r2dbc;

    public DatabaseHealthProbe(R2dbcEntityTemplate r2dbc) {
        this.r2dbc = r2dbc;
    }

    public Mono<Boolean> isAvailable() {
        return r2dbc.getDatabaseClient()
            .sql("SELECT 1")
            .fetch()
            .first()
            .map(row -> true)
            .timeout(PROBE_TIMEOUT)
            .onErrorReturn(false);
    }
}
