package com.footballmanager.adapters.in.web.world;

import com.footballmanager.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V24D7 FASE C — E2E HTTP coverage for {@link TeamCommandController}.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test DB +
 * Redis DB 15. Exercises the {@code CreateCustomTeamService} and
 * {@code WorldTeamCommandService} real implementations.
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
@DisplayName("TeamCommandController — E2E HTTP coverage")
class TeamCommandControllerE2ETest extends AbstractIntegrationTest {

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
    @DisplayName("POST /world/create-custom-team — 200 with the new team")
    void createCustomTeam_returns200() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/world/create-custom-team")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"userId\":\"%s\",\"name\":\"Test Custom FC\",\"country\":\"Argentina\",\"budget\":1000000.00,\"formation\":\"4-4-2\"}",
                SEED_USER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Test Custom FC")
            .jsonPath("$.country").isEqualTo("Argentina");
    }

    @Test
    @DisplayName("POST /world/random-team?userId=... — 200 with a random team")
    void createRandomTeam_returns200() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/random-team")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.worldTeamId").exists()
            .jsonPath("$.name").exists();
    }

    @Test
    @DisplayName("POST /world/random-teams — 200 with success response")
    void createRandomTeams_returns200() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/world/random-teams")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"userId\":\"%s\",\"count\":3}", SEED_USER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.count").isEqualTo(3)
            .jsonPath("$.message").exists();
    }
}
