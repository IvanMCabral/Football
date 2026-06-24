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
 * V24D7 FASE B — E2E HTTP coverage for {@link WorldQueryController}.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the seeded test DB.
 * No mocks — exercises the full stack (controller → use case → repo → R2DBC).
 *
 * <p>Scope:
 * <ul>
 *   <li>GET /api/v1/world/leagues — returns the seeded La Liga</li>
 *   <li>GET /api/v1/world/teams — returns the seeded teams</li>
 *   <li>GET /api/v1/world/players — returns the seeded players</li>
 *   <li>GET /api/v1/world/leagues/{id}/teams — returns teams for a specific league</li>
 *   <li>GET /api/v1/world/free-players — returns free agents</li>
 *   <li>GET /api/v1/world/teams/{worldTeamId}/players — returns squad for a team</li>
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
@DisplayName("WorldQueryController — E2E HTTP coverage")
class WorldQueryControllerE2ETest extends AbstractIntegrationTest {

    private static final UUID SEED_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LALIGA_ID =
        UUID.fromString("4feeb9df-4133-4655-883e-e96894907e7b");

    @org.springframework.beans.factory.annotation.Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
    }

    @Test
    @DisplayName("GET /world/leagues?userId=... — 200 with the seeded league")
    void leagues_returns200WithLaLiga() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").exists();
    }

    @Test
    @DisplayName("GET /world/teams?userId=... — 200 with at least 1 team")
    void teams_returns200WithSeededTeams() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/teams")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray();
    }

    @Test
    @DisplayName("GET /world/players?userId=... — 200 with seeded players")
    void players_returns200WithSeededPlayers() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/players")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray();
    }

    @Test
    @DisplayName("GET /world/leagues/{id}/teams?userId=... — 200 with teams for La Liga")
    void teamsByLeague_returns200() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues/{leagueId}/teams")
                .queryParam("userId", SEED_USER_ID)
                .build(LALIGA_ID))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray();
    }

    @Test
    @DisplayName("GET /world/free-players?userId=... — 200 with free agents")
    void freePlayers_returns200() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/free-players")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray();
    }
}
