package com.footballmanager.adapters.in.web.career;

import com.footballmanager.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V24D7 FASE C — E2E HTTP coverage for {@code CareerViewController}.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test DB +
 * Redis DB 15. Exercises the read-only career endpoints. For users without
 * a career, the controllers return safe defaults (empty lists, empty
 * status), so the tests assert that the HTTP layer returns 200/2xx, not
 * 5xx, regardless of whether a career exists.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.flyway.enabled=false",
        "spring.data.redis.database=15"
    }
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("CareerViewController — E2E HTTP coverage")
class CareerViewControllerE2ETest extends AbstractIntegrationTest {

    private static final UUID SEED_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @org.springframework.beans.factory.annotation.Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
    }

    @Test
    @DisplayName("GET /career/status — 2xx (no career returns safe default)")
    void getStatus_returns2xx() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/status")
            .exchange()
            .expectStatus().is2xxSuccessful();
    }

    @Test
    @DisplayName("GET /career/fixtures?round=1 — 2xx (empty list when no career)")
    void getFixtures_returns2xx() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/career/fixtures")
                .queryParam("round", 1)
                .build())
            .exchange()
            .expectStatus().is2xxSuccessful();
    }

    @Test
    @DisplayName("GET /career/fixtures/all — 2xx (empty response when no career)")
    void getAllFixtures_returns2xx() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/fixtures/all")
            .exchange()
            .expectStatus().is2xxSuccessful();
    }

    @Test
    @DisplayName("GET /career/standings — 2xx (empty list when no career)")
    void getStandings_returns2xx() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/standings")
            .exchange()
            .expectStatus().is2xxSuccessful();
    }

    @Test
    @DisplayName("GET /career/standings/all — 2xx (empty response when no career)")
    void getAllStandings_returns2xx() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/standings/all")
            .exchange()
            .expectStatus().is2xxSuccessful();
    }

    @Test
    @DisplayName("GET /career/palmares — 2xx (empty list when no career)")
    void getPalmares_returns2xx() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/palmares")
            .exchange()
            .expectStatus().is2xxSuccessful();
    }

    @Test
    @DisplayName("GET /career/palmares/all — 2xx (empty list when no career)")
    void getAllPalmares_returns2xx() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/palmares/all")
            .exchange()
            .expectStatus().is2xxSuccessful();
    }

    @Test
    @DisplayName("GET /career/divisions — 2xx (empty list when no career)")
    void getDivisions_returns2xx() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/divisions")
            .exchange()
            .expectStatus().is2xxSuccessful();
    }
}
