package com.footballmanager.adapters.in.web.health;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerReadinessMatrixTest {

    @Test
    void readinessReturns200WhenDatabaseAndRedisAreUp() {
        assertReadiness(true, true, HttpStatus.OK, "UP", "UP", "UP");
    }

    @Test
    void readinessReturns503WhenDatabaseIsDownAndRedisIsUp() {
        assertReadiness(false, true, HttpStatus.SERVICE_UNAVAILABLE, "DOWN", "DOWN", "UP");
    }

    @Test
    void readinessReturns503WhenDatabaseIsUpAndRedisIsDown() {
        assertReadiness(true, false, HttpStatus.SERVICE_UNAVAILABLE, "DOWN", "UP", "DOWN");
    }

    @Test
    void readinessReturns503WhenDatabaseAndRedisAreDown() {
        assertReadiness(false, false, HttpStatus.SERVICE_UNAVAILABLE, "DOWN", "DOWN", "DOWN");
    }

    @Test
    void livenessDoesNotDependOnExternalServices() {
        DatabaseHealthProbe database = mock(DatabaseHealthProbe.class);
        RedisHealthProbe redis = mock(RedisHealthProbe.class);
        HealthController controller = new HealthController(database, redis);

        StepVerifier.create(controller.liveness())
            .assertNext(response -> {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).containsEntry("status", "UP");
            })
            .verifyComplete();
    }

    private void assertReadiness(
        boolean databaseUp,
        boolean redisUp,
        HttpStatus expectedStatus,
        String expectedOverall,
        String expectedDatabase,
        String expectedRedis
    ) {
        DatabaseHealthProbe database = mock(DatabaseHealthProbe.class);
        RedisHealthProbe redis = mock(RedisHealthProbe.class);
        when(database.isAvailable()).thenReturn(Mono.just(databaseUp));
        when(redis.isAvailable()).thenReturn(Mono.just(redisUp));
        HealthController controller = new HealthController(database, redis);

        StepVerifier.create(controller.readiness())
            .assertNext(response -> {
                assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
                assertThat(response.getBody())
                    .containsEntry("status", expectedOverall)
                    .containsEntry("database", expectedDatabase)
                    .containsEntry("redis", expectedRedis);
            })
            .verifyComplete();
    }
}
