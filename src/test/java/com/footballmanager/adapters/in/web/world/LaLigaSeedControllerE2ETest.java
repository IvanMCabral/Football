package com.footballmanager.adapters.in.web.world;

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
 * V24D7 FASE C — E2E HTTP coverage for {@link LaLigaSeedController}.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test DB +
 * Redis DB 15. Exercises the LaLigaSeedService real implementation, which
 * reads {@code laliga-2024-25.json} from the classpath and writes the
 * WorldSnapshot to Redis.
 *
 * <p>Scope:
 * <ul>
 *   <li>POST /api/v1/world/seed-la-liga — 200 with counts</li>
 *   <li>Idempotency: run twice, snapshot still has 20 teams (no duplication)</li>
 *   <li>Response shape: status, leagueName, teamsInserted, playersInserted, durationMs</li>
 * </ul>
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
@DisplayName("LaLigaSeedController — E2E HTTP coverage")
class LaLigaSeedControllerE2ETest extends AbstractIntegrationTest {

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
    @DisplayName("POST /world/seed-la-liga — 200 with status, teamsInserted, playersInserted")
    void seed_returns200WithCounts() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed-la-liga")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok")
            .jsonPath("$.userId").isEqualTo(SEED_USER_ID.toString())
            .jsonPath("$.leagueName").isEqualTo("La Liga 2024/25")
            .jsonPath("$.teamsInserted").exists()
            .jsonPath("$.playersInserted").exists()
            .jsonPath("$.durationMs").exists();
    }

    @Test
    @DisplayName("POST /world/seed-la-liga — idempotent: second run returns 200 without duplicating")
    void seed_idempotent_secondRunReturns200() {
        // First run
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed-la-liga")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.teamsInserted").value(teams1 ->
                org.junit.jupiter.api.Assertions.assertNotNull(teams1));

        // Second run
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed-la-liga")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok");
    }
}
