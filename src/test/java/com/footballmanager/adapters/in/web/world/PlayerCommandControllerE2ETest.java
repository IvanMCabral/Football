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
 * V24D7 FASE C — E2E HTTP coverage for {@link PlayerCommandController}.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test DB +
 * Redis DB 15. Exercises the {@code WorldPlayerCommandService},
 * {@code AssignPlayerUseCase}, and {@code RemovePlayerUseCase}.
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
@DisplayName("PlayerCommandController — E2E HTTP coverage")
class PlayerCommandControllerE2ETest extends AbstractIntegrationTest {

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
    @DisplayName("POST /world/create-custom-player — 200 with the new player")
    void createCustomPlayer_returns200() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/world/create-custom-player")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format("""
                {
                  "userId":"%s",
                  "name":"Test Player",
                  "age":25,
                  "position":"CM",
                  "attack":75,"defense":75,"technique":80,"speed":78,"stamina":85,"mentality":82
                }
                """, SEED_USER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.worldPlayerId").exists()
            .jsonPath("$.name").exists();
    }

    @Test
    @DisplayName("POST /world/create-random-player — 200 with a random player")
    void createRandomPlayer_returns200() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/world/create-random-player")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format("{\"userId\":\"%s\"}", SEED_USER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.worldPlayerId").exists()
            .jsonPath("$.name").exists();
    }

    @Test
    @DisplayName("POST /world/create-random-players — 200 with success response")
    void createRandomPlayers_returns200() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/world/create-random-players")
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
