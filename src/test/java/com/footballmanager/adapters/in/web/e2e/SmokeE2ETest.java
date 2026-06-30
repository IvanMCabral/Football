package com.footballmanager.adapters.in.web.e2e;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * V24D7 FASE A — Smoke E2E test.
 *
 * <p>Purpose: prove the test infrastructure works end-to-end so FASE B/C
 * (which will write the real E2E coverage) can be built on a verified base.
 *
 * <p>This test exercises:
 * <ol>
 *   <li>Spring context boots against {@code football_manager_test} (no Flyway)</li>
 *   <li>{@link com.footballmanager.adapters.in.web.health.HealthController} responds 200 OK</li>
 *   <li>R2DBC connection to the test DB works (raw {@code SELECT 1})</li>
 *   <li>Redis connection on logical DB 15 works (set+get roundtrip)</li>
 *   <li>{@code GET /api/v1/world/teams?userId=...} returns 200 against the seeded DB</li>
 *   <li>{@code GET /api/v1/world/leagues?userId=...} returns 200</li>
 * </ol>
 *
 * <p>Note: {@code /actuator/health} is NOT exercised here because the project
 * does not include {@code spring-boot-starter-actuator}. The custom
 * {@code /api/v1/health} endpoint is the project's health probe.
 *
 * <p>Each test is hermetic: {@link AbstractIntegrationTest#cleanRedis()} flushes
 * Redis DB 15 before every test. The DB is left intact (seeded LaLiga data).
 */
@DisplayName("V24D7 FASE A — Smoke E2E (test infrastructure)")
class SmokeE2ETest extends AbstractIntegrationTest {

    @Autowired
    private com.footballmanager.infrastructure.security.JwtTokenProvider jwtTokenProvider;

    private static final UUID SEED_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    @DisplayName("GET /api/v1/health returns 200 OK")
    void healthEndpoint_returns200() {
        webTestClient.get().uri("/api/v1/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("OK");
    }

    @Test
    @DisplayName("R2DBC connection to football_manager_test works")
    void r2dbcConnection_works() {
        Mono<String> query = databaseClient.sql("SELECT 1")
            .map(row -> "ok")
            .one();

        StepVerifier.create(query)
            .expectNext("ok")
            .verifyComplete();
    }

    @Test
    @DisplayName("Redis connection on DB 15 works (set + get roundtrip)")
    void redisConnection_works() {
        // ReactiveServerCommands has no ping() in spring-data-redis 3.2.x,
        // so we do a set+get roundtrip on the test Redis DB 15.
        String key = "smoke:e2e:ping:" + UUID.randomUUID();
        String value = "pong-" + System.currentTimeMillis();

        Mono<String> roundtrip = reactiveRedisTemplate.opsForValue()
            .set(key, value)
            .then(reactiveRedisTemplate.opsForValue().get(key));

        StepVerifier.create(roundtrip)
            .expectNext(value)
            .verifyComplete();
    }

    @Test
    @DisplayName("GET /api/v1/world/teams?userId=... returns 200 against seeded DB "
        + "(V25D78-C48: now requires authenticated — uses real JWT)")
    void worldTeamsEndpoint_returns200() {
        // V25D78-C48: /world/** changed from permitAll to authenticated. Pre-C48 this
        // worked anonymously; post-C48 we send a real JWT generated via JwtTokenProvider.
        // (mockUser configurer is incompatible with the server-bound WebTestClient used
        // by this test — the auto-bound @SpringBootTest RANDOM_PORT client connects via
        // real HTTP, so we have to use real JWT, not mockUser.)
        String token = jwtTokenProvider.generateToken(SEED_USER_ID.toString(), "USER");
        webTestClient.get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/teams")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .header("Authorization", "Bearer " + token)
            .accept(APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(APPLICATION_JSON);
    }

    @Test
    @DisplayName("GET /api/v1/world/leagues?userId=... returns 200 "
        + "(V25D78-C48: now requires authenticated — uses real JWT)")
    void worldLeaguesEndpoint_returns200() {
        // V25D78-C48: /world/** changed from permitAll to authenticated.
        String token = jwtTokenProvider.generateToken(SEED_USER_ID.toString(), "USER");
        webTestClient.get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .header("Authorization", "Bearer " + token)
            .accept(APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(APPLICATION_JSON);
    }
}
