package com.footballmanager.adapters.in.web.health;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
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
