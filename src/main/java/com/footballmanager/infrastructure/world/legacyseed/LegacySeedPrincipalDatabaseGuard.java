package com.footballmanager.infrastructure.world.legacyseed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class LegacySeedPrincipalDatabaseGuard {

    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(10);
    private static final String PRINCIPAL_DATABASE_NAME = "football_manager";

    private final DatabaseClient databaseClient;
    private final boolean allowPrincipalDatabaseWrites;

    public LegacySeedPrincipalDatabaseGuard(
            DatabaseClient databaseClient,
            @Value("${app.world.legacy-seed.allow-principal-database-write:false}")
            boolean allowPrincipalDatabaseWrites) {
        this.databaseClient = databaseClient;
        this.allowPrincipalDatabaseWrites = allowPrincipalDatabaseWrites;
    }

    public void assertLegacySeedCanWrite(String operationName) {
        String databaseName = databaseClient.sql("SELECT current_database()")
                .map((row, metadata) -> row.get(0, String.class))
                .one()
                .block(CHECK_TIMEOUT);

        if (PRINCIPAL_DATABASE_NAME.equals(databaseName) && !allowPrincipalDatabaseWrites) {
            throw new IllegalStateException(
                    "Legacy seed write blocked on principal database 'football_manager'. "
                            + "Use the explicit final dataset importer for MVP1 catalog data, "
                            + "or set app.world.legacy-seed.allow-principal-database-write=true "
                            + "only for an intentional demo/legacy seed run.");
        }

        if (PRINCIPAL_DATABASE_NAME.equals(databaseName)) {
            log.warn("Legacy seed write allowed on principal database by explicit override: {}",
                    operationName);
        }
    }
}
