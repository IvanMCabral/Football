package com.footballmanager.infrastructure.world.legacyseed;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Mono;

import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacySeedPrincipalDatabaseGuardTest {

    @Test
    void blocksLegacySeedWritesOnPrincipalDatabaseByDefault() {
        LegacySeedPrincipalDatabaseGuard guard = new LegacySeedPrincipalDatabaseGuard(
                databaseClientReturning("football_manager"),
                false);

        assertThatThrownBy(() -> guard.assertLegacySeedCanWrite("legacy player seed"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Legacy seed write blocked on principal database");
    }

    @Test
    void allowsLegacySeedWritesOutsidePrincipalDatabase() {
        LegacySeedPrincipalDatabaseGuard guard = new LegacySeedPrincipalDatabaseGuard(
                databaseClientReturning("football_manager_test"),
                false);

        assertThatCode(() -> guard.assertLegacySeedCanWrite("legacy player seed"))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsPrincipalDatabaseOnlyWithExplicitOverride() {
        LegacySeedPrincipalDatabaseGuard guard = new LegacySeedPrincipalDatabaseGuard(
                databaseClientReturning("football_manager"),
                true);

        assertThatCode(() -> guard.assertLegacySeedCanWrite("legacy player seed"))
                .doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private static DatabaseClient databaseClientReturning(String databaseName) {
        DatabaseClient databaseClient = mock(DatabaseClient.class);
        DatabaseClient.GenericExecuteSpec executeSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        RowsFetchSpec<String> rowsFetchSpec = mock(RowsFetchSpec.class);

        when(databaseClient.sql(eq("SELECT current_database()"))).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenAnswer(invocation -> {
            BiFunction<Row, RowMetadata, String> mapper = invocation.getArgument(0);
            Row row = mock(Row.class);
            when(row.get(eq(0), eq(String.class))).thenReturn(databaseName);
            mapper.apply(row, mock(RowMetadata.class));
            return rowsFetchSpec;
        });
        when(rowsFetchSpec.one()).thenReturn(Mono.just(databaseName));
        return databaseClient;
    }
}
