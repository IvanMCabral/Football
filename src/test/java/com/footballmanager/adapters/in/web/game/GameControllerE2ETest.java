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

    // V24D7+2.1: /api/v1/world/teams and /api/v1/world/leagues are global endpoints
    // that require the seeded admin user (per WorldQueryControllerE2ETest SEED_USER_ID).
    // Random userIds trigger 500. So we hardcode the seed user here.
    private static final String SEED_USER_ID =
        "00000000-0000-0000-0000-000000000001";

    private String uniqueUserId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Fetches a real leagueId from the LaLiga seed via the world/leagues endpoint.
     * Per AbstractIntegrationTest the DB is NOT truncated between tests, so seed leagues
     * are always available. Returns the realLeagueId of the first league in the response array.
     * Note: WorldLeague JSON field is "realLeagueId" (not "id") per the entity definition.
     */
    private String seedLeagueId() {
        return webTestClient.mutateWith(mockUser(SEED_USER_ID))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody()
            .get(0).get("realLeagueId").asText();
    }

    /**
     * Fetches a real teamId from the LaLiga seed via the world/teams endpoint.
     * Per AbstractIntegrationTest the DB is NOT truncated between tests, so seed teams
     * are always available. Returns the worldTeamId of the first team in the response array.
     * Note: the userId parameter is unused — world/teams requires SEED_USER_ID.
     * Note: WorldTeam JSON field is "worldTeamId" (not "id") per the entity definition.
     */
    private String seedTeamId(String userId) {
        return webTestClient.mutateWith(mockUser(SEED_USER_ID))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/teams")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody()
            .get(0).get("worldTeamId").asText();
    }

    private String validCreateBody(String name, String teamId, String leagueId) {
        // teamId and leagueId are REQUIRED (controller returns 400 if null/missing).
        // teamsPerDivision: must be >= 2 && <= leagueTeams.size() per CreateCareerSnapshotUseCaseImpl
        // validation. 2 is the safest minimum (works for any league with >= 2 seeded teams).
        return String.format(
            "{\"name\":\"%s\",\"teamId\":\"%s\",\"leagueId\":\"%s\",\"teamsPerDivision\":2}",
            name, teamId, leagueId
        );
    }

    @Test
    @DisplayName("POST /api/v1/games — 201 with body containing gameId and userId")
    void createGame_validRequest_returns201() {
        String userId = uniqueUserId();
        String teamId = seedTeamId(userId);
        String leagueId = seedLeagueId();
        String body = validCreateBody("My Career " + UUID.randomUUID().toString().substring(0, 8), teamId, leagueId);

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/games")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(JsonNode.class)
            .value(json -> {
                // GameId and UserId are value objects serialized as {"value": "uuid"}
                org.junit.jupiter.api.Assertions.assertNotNull(json.get("id"));
                org.junit.jupiter.api.Assertions.assertNotNull(json.get("userId"));
                String idStr = json.get("id").get("value").asText();
                String userIdStr = json.get("userId").get("value").asText();
                org.junit.jupiter.api.Assertions.assertFalse(idStr.isBlank());
                // userId should match the authenticated user
                org.junit.jupiter.api.Assertions.assertEquals(userId, userIdStr);
            });
    }

    @Test
    @DisplayName("GET /api/v1/games/{id} — 200 with body (regression for V24D12.2 deserialization fix)")
    void getGameById_existingGame_returns200() {
        String userId = uniqueUserId();
        String teamId = seedTeamId(userId);
        String leagueId = seedLeagueId();
        String createBody = validCreateBody("GetTest", teamId, leagueId);

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
            .get("id").get("value").asText();

        // Get
        webTestClient.mutateWith(mockUser(userId))
            .get().uri("/api/v1/games/{id}", gameId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .value(json -> {
                org.junit.jupiter.api.Assertions.assertEquals(gameId, json.get("id").get("value").asText());
                org.junit.jupiter.api.Assertions.assertEquals(userId, json.get("userId").get("value").asText());
            });
    }

    @Test
    @DisplayName("GET /api/v1/games — 200 with list (array of games)")
    void getAllGames_userHasGames_returns200List() {
        String userId = uniqueUserId();
        String teamId = seedTeamId(userId);
        String leagueId = seedLeagueId();
        String body1 = validCreateBody("List1", teamId, leagueId);
        String body2 = validCreateBody("List2", teamId, leagueId);

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
        String teamId = seedTeamId(userId);
        String leagueId = seedLeagueId();
        String createBody = validCreateBody("DeleteMe", teamId, leagueId);

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
            .get("id").get("value").asText();

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
        String teamId = seedTeamId(userId);
        String leagueId = seedLeagueId();
        String createBody = validCreateBody("DeleteAndGet", teamId, leagueId);

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
            .get("id").get("value").asText();

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
        // Hardcoded UUIDs — controller returns 401 before parsing the body
        // (line 41-43 of GameController), so the teamId/leagueId values are irrelevant.
        String body = validCreateBody("UnauthTest",
            "00000000-0000-0000-0000-000000000002",
            "00000000-0000-0000-0000-000000000003");

        // No mockUser → no JWT → SecurityConfig returns 401
        webTestClient.post().uri("/api/v1/games")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    // ========== V24D7+2.1: defensive guard regression ==========

    @Test
    @DisplayName("POST /api/v1/games without teamId — 400 (controller defensive guard)")
    void createGame_missingTeamId_returns400() {
        String userId = uniqueUserId();
        // Body has name + leagueId but NO teamId
        String body = String.format(
            "{\"name\":\"MissingTeam\",\"leagueId\":\"%s\"}",
            UUID.randomUUID()
        );

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/games")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest();
    }
}
