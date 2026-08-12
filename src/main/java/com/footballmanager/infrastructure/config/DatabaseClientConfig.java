package com.footballmanager.infrastructure.config;

import com.footballmanager.application.service.world.DurableBoundaryClassification;
import com.footballmanager.application.service.world.DurablePersistenceBoundary;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;

@Configuration
@DurableBoundaryClassification(value = DurablePersistenceBoundary.Classification.OUT_OF_SCOPE_WITH_EXPLICIT_REASON,
        reason = "DatabaseClient bean configuration creates an infrastructure client; it is not a persistence writer")
public class DatabaseClientConfig {
    @Bean
    public DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }
}
