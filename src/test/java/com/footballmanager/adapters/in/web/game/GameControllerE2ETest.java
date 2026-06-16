package com.footballmanager.adapters.in.web.game;

import com.fasterxml.jackson.databind.JsonNode;
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
 * V24D7+2.1 — E2E HTTP coverage for {@link GameController}.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test DB + Redis DB 15.
 * Covers the basic CRUD lifecycle: create, get, list, delete, plus auth + 404 paths.
 *
 * <p>Critical regression: Test 2 (getGameById_existingGame_returns200) guards the
 * V24D12.2 deserialization fix ({@code @NoArgsConstructor} + {@code @Setter} on
 * {@code GameEntity}). If those annotations are removed, the GET returns 404 silently.
 *
 * <p>Scope (7 tests):
 * <ul>
 *   <li>POST /api/v1/games 201 + body (id, userId)</li>
 *   <li>GET /api/v1/games/{id} 200 (V24D12.2 regression)</li>
 *   <li>GET /api/v1/games list 200 + array (>= 2)</li>
 *   <li>GET /api/v1/games/{nonexistent} 404</li>
 *   <li>DELETE /api/v1/games/{id} 204</li>
 *   <li>GET /api/v1/games/{id} after DELETE 404 (idempotency)</li>
 *   <li>POST /api/v1/games without auth 401 (SecurityConfig V24D12-1)</li>
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
@DisplayName("GameController — E2E HTTP coverage")
class GameControllerE2ETest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
    }

    private String uniqueUserId() {
        return UUID.randomUUID().toString();
    }

    private String validCreateBody(String name) {
        // leagueId is REQUIRED (controller returns 400 if null, line 46-48)
        // other fields optional with defaults
        return String.format(
            "{\"name\":\"%s\",\"leagueId\":\"%s\"}",
            name, UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("POST /api/v1/games — 201 with body containing gameId and userId")
    void createGame_validRequest_returns201() {
        String userId = uniqueUserId();
        String body = validCreateBody("My Career " + UUID.randomUUID().toString().substring(0, 8));

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/games")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(JsonNode.class)
            .value(json -> {
                // GameId value object serializes as String
                org.junit.jupiter.api.Assertions.assertNotNull(json.get("id"));
                org.junit.jupiter.api.Assertions.assertNotNull(json.get("userId"));
                org.junit.jupiter.api.Assertions.assertFalse(json.get("id").asText().isBlank());
                // userId should match the authenticated user
                org.junit.jupiter.api.Assertions.assertEquals(userId, json.get("userId").asText());
            });
    }

    @Test
    @DisplayName("GET /api/v1/games/{id} — 200 with body (regression for V24D12.2 deserialization fix)")
    void getGameById_existingGame_returns200() {
        String userId = uniqueUserId();
        String createBody = validCreateBody("GetTest");

        // Create
        String gameId = webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/games")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody()
            .get("id").asText();

        // Get
        webTestClient.mutateWith(mockUser(userId))
            .get().uri("/api/v1/games/{id}", gameId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .value(json -> {
                org.junit.jupiter.api.Assertions.assertEquals(gameId, json.get("id").asText());
                org.junit.jupiter.api.Assertions.assertEquals(userId, json.get("userId").asText());
            });
    }

    @Test
    @DisplayName("GET /api/v1/games — 200 with list (array of games)")
    void getAllGames_userHasGames_returns200List() {
        String userId = uniqueUserId();
        String body1 = validCreateBody("List1");
        String body2 = validCreateBody("List2");

        // Create 2 games
        webTestClient.mutateWith(mockUser(userId)).post().uri("/api/v1/games")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(body1).exchange().expectStatus().isCreated();
        webTestClient.mutateWith(mockUser(userId)).post().uri("/api/v1/games")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(body2).exchange().expectStatus().isCreated();

        // List
        webTestClient.mutateWith(mockUser(userId))
            .get().uri("/api/v1/games")
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .value(json -> {
                org.junit.jupiter.api.Assertions.assertTrue(json.isArray());
                org.junit.jupiter.api.Assertions.assertTrue(json.size() >= 2);
            });
    }

    @Test
    @DisplayName("GET /api/v1/games/{nonexistent} — 404 Not Found")
    void getGameById_nonExistent_returns404() {
        String userId = uniqueUserId();
        String fakeGameId = UUID.randomUUID().toString();

        webTestClient.mutateWith(mockUser(userId))
            .get().uri("/api/v1/games/{id}", fakeGameId)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("DELETE /api/v1/games/{id} — 204 No Content")
    void deleteGame_existingGame_returns204() {
        String userId = uniqueUserId();
        String createBody = validCreateBody("DeleteMe");

        // Create
        String gameId = webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/games")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody()
            .get("id").asText();

        // Delete
        webTestClient.mutateWith(mockUser(userId))
            .delete().uri("/api/v1/games/{id}", gameId)
            .exchange()
            .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("GET /api/v1/games/{id} after DELETE — 404 (idempotency: deleted game is gone)")
    void getGameById_afterDelete_returns404() {
        String userId = uniqueUserId();
        String createBody = validCreateBody("DeleteAndGet");

        // Create
        String gameId = webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/games")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody()
            .get("id").asText();

        // Delete
        webTestClient.mutateWith(mockUser(userId))
            .delete().uri("/api/v1/games/{id}", gameId)
            .exchange()
            .expectStatus().isNoContent();

        // Get should now return 404
        webTestClient.mutateWith(mockUser(userId))
            .get().uri("/api/v1/games/{id}", gameId)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("POST /api/v1/games without auth — 401 (SecurityConfig rule V24D12-1)")
    void createGame_unauthenticated_returns401() {
        String body = validCreateBody("UnauthTest");

        // No mockUser → no JWT → SecurityConfig returns 401
        webTestClient.post().uri("/api/v1/games")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isUnauthorized();
    }
}
