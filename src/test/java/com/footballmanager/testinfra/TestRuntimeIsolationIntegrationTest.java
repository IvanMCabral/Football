package com.footballmanager.testinfra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TestRuntimeIsolationIntegrationTest {

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisDb15Template;

    @Autowired
    private RedisProperties redisProperties;

    @Test
    void jdbcFlywayAndR2dbcUseEphemeralPostgresDatabase() {
        StepVerifier.create(databaseClient.sql("SELECT COUNT(*) AS count FROM flyway_schema_history")
                .map(row -> row.get("count", Long.class))
                .one())
            .assertNext(count -> assertThat(count).isPositive())
            .verifyComplete();

        StepVerifier.create(databaseClient.sql("SELECT current_database() AS database")
                .map(row -> row.get("database", String.class))
                .one())
            .assertNext(database -> assertThat(database).isEqualTo("postgres"))
            .verifyComplete();
    }

    @Test
    void redisDatabaseZeroAndFifteenAreIsolatedAndCleanable() {
        assertThat(redisProperties.getDatabase()).isEqualTo(15);

        String db0Key = "db0:sentinel:" + UUID.randomUUID();
        String db15Key = "db15:sentinel:" + UUID.randomUUID();
        String db0Value = "zero-" + UUID.randomUUID();
        String db15Value = "fifteen-" + UUID.randomUUID();

        ReactiveRedisTemplate<String, String> db0Template = createTemplateForDatabase(0);
        try {
            StepVerifier.create(db0Template.opsForValue().set(db0Key, db0Value)
                    .then(redisDb15Template.opsForValue().set(db15Key, db15Value))
                    .then(db0Template.opsForValue().get(db0Key)))
                .expectNext(db0Value)
                .verifyComplete();

            StepVerifier.create(redisDb15Template.opsForValue().get(db15Key))
                .expectNext(db15Value)
                .verifyComplete();

            StepVerifier.create(redisDb15Template.opsForValue().get(db0Key))
                .verifyComplete();

            StepVerifier.create(db0Template.opsForValue().get(db15Key))
                .verifyComplete();

            StepVerifier.create(db0Template.delete(db0Key)
                    .then(redisDb15Template.delete(db15Key)))
                .expectNextCount(1)
                .verifyComplete();

            StepVerifier.create(db0Template.opsForValue().get(db0Key))
                .verifyComplete();

            StepVerifier.create(redisDb15Template.opsForValue().get(db15Key))
                .verifyComplete();
        } finally {
            ReactiveRedisConnectionFactory factory = db0Template.getConnectionFactory();
            if (factory instanceof LettuceConnectionFactory lettuce) {
                lettuce.destroy();
            }
        }
    }

    private ReactiveRedisTemplate<String, String> createTemplateForDatabase(int database) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
            redisProperties.getHost(),
            redisProperties.getPort());
        configuration.setDatabase(database);
        configuration.setPassword(redisProperties.getPassword());
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        return new ReactiveRedisTemplate<>(factory, redisDb15Template.getSerializationContext());
    }
}
