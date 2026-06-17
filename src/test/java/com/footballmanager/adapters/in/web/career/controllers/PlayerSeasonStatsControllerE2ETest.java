package com.footballmanager.adapters.in.web.career.controllers;

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
 * V24D7+2.3 — E2E HTTP coverage for {@link PlayerSeasonStatsController}.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test DB + Redis DB 15.
 * Three read-only GET endpoints under {@code /api/v1/careers}. No career-in-Redis required
 * (the controller queries V24DetailedMatchStoragePort by careerId directly).
 *
 * <p><b>Error shape contract (differs from V24D7+2.2):</b>
 * <ul>
 *   <li><b>400 from controller validation</b> (in-code, NOT GlobalExceptionHandler):
 *       {@code {"error": "..."}} — assert {@code $.error}, NOT {@code $.code}.</li>
 *   <li><b>401 from security filter</b>: {@code {"code": "UNAUTHORIZED", "message": "...", "status": 401}}
 *       — assert {@code $.code}.</li>
 *   <li><b>404 from feature gate</b> (when {@code app.simulation.v24.expose-detail-api=false})
 *       or from single-player endpoint when playerStats is empty.</li>
 * </ul>
 *
 * <p><b>Field names:</b> all String/int/double/boolean direct values, NOT value object wrappers
 * (unlike Game where {@code id}/{@code userId} are VOs). Use {@code .asText()}, {@code .asInt()},
 * {@code .asBoolean()} directly, without {@code .get("value")}.
 *
 * <p><b>Scope (6 tests):</b>
 * <ul>
 *   <li>GET player-stats without auth — 401 UNAUTHORIZED</li>
 *   <li>GET player-stats?limit=0 — 400 with {@code error} containing "limit"</li>
 *   <li>GET player-stats?sortBy=invalid — 400 with {@code error} containing "sortBy"</li>
 *   <li>GET player-stats (all-players, no V24 data) — 200 with empty array + message</li>
 *   <li>GET player-stats with pagination + sort — 200 with proper structure</li>
 *   <li>GET players/{id}/stats (single-player, no V24 data) — 404 (controller returns 404 on empty playerStats)</li>
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
@DisplayName("PlayerSeasonStatsController — E2E HTTP coverage")
class PlayerSeasonStatsControllerE2ETest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        // V24D7+2.3: clear Redis between tests. The controller doesn't require
        // a career in cache (queries V24DetailedMatchStoragePort by careerId directly),
        // but flushDb keeps the test environment deterministic.
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
    }

    /**
     * PlayerSeasonStatsController does not call /api/v1/world/* or require any
     * seeded admin user. The only auth required is a valid JWT (mockUser), which
     * can be any userId — hence just UUID.randomUUID() per test.
     */
    private String uniqueUserId() {
        return UUID.randomUUID().toString();
    }

    // ========== Tests ==========

    @Test
    @DisplayName("GET /api/v1/careers/.../player-stats without auth — 401 UNAUTHORIZED")
    void getAllPlayerStats_unauthenticated_returns401() {
        String careerId = UUID.randomUUID().toString();

        webTestClient.get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/careers/{careerId}/seasons/1/player-stats")
                .build(careerId))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("GET /api/v1/careers/.../player-stats?limit=0 — 400 (controller validation: limit must be > 0)")
    void getAllPlayerStats_invalidLimit_returns400() {
        String userId = uniqueUserId();
        String careerId = UUID.randomUUID().toString();

        webTestClient.mutateWith(mockUser(userId))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/careers/{careerId}/seasons/1/player-stats")
                .queryParam("limit", 0)
                .build(careerId))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("limit"));
    }

    @Test
    @DisplayName("GET /api/v1/careers/.../player-stats?sortBy=invalid — 400 (controller rejects unknown sort fields)")
    void getAllPlayerStats_invalidSortBy_returns400() {
        String userId = uniqueUserId();
        String careerId = UUID.randomUUID().toString();

        webTestClient.mutateWith(mockUser(userId))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/careers/{careerId}/seasons/1/player-stats")
                .queryParam("sortBy", "notAValidField")
                .build(careerId))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("sortBy"));
    }

    @Test
    @DisplayName("GET /api/v1/careers/{random}/.../player-stats — 200 with empty list and message (no V24 detail in Redis)")
    void getAllPlayerStats_noV24Data_returns200WithEmptyList() {
        String userId = uniqueUserId();
        String careerId = UUID.randomUUID().toString(); // careerId random — sin V24 data

        webTestClient.mutateWith(mockUser(userId))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/careers/{careerId}/seasons/1/player-stats")
                .build(careerId))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .value(json -> {
                org.junit.jupiter.api.Assertions.assertEquals(careerId, json.get("careerId").asText());
                org.junit.jupiter.api.Assertions.assertEquals(1, json.get("season").asInt());
                org.junit.jupiter.api.Assertions.assertTrue(json.get("playerStats").isArray());
                org.junit.jupiter.api.Assertions.assertEquals(0, json.get("playerStats").size());
                // The queryService sets a message when there is no V24 detail data.
                org.junit.jupiter.api.Assertions.assertNotNull(json.get("message"));
                org.junit.jupiter.api.Assertions.assertTrue(
                    json.get("message").asText().contains("No V24 detail data"));
            });
    }

    @Test
    @DisplayName("GET /api/v1/careers/.../player-stats?limit=10&offset=0&sortBy=goals&order=desc — 200 with proper structure")
    void getAllPlayerStats_withPaginationAndSort_returns200() {
        String userId = uniqueUserId();
        String careerId = UUID.randomUUID().toString();

        webTestClient.mutateWith(mockUser(userId))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/careers/{careerId}/seasons/1/player-stats")
                .queryParam("limit", 10)
                .queryParam("offset", 0)
                .queryParam("sortBy", "goals")
                .queryParam("order", "desc")
                .build(careerId))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .value(json -> {
                org.junit.jupiter.api.Assertions.assertNotNull(json.get("careerId"));
                org.junit.jupiter.api.Assertions.assertNotNull(json.get("season"));
                org.junit.jupiter.api.Assertions.assertTrue(json.get("playerStats").isArray());
                // Sin V24 data, playerStats está vacío.
                org.junit.jupiter.api.Assertions.assertEquals(0, json.get("playerStats").size());
                // totalGoals/Assists/Appearances deben ser 0 (no null) per builder.
                org.junit.jupiter.api.Assertions.assertEquals(0, json.get("totalGoals").asInt());
                org.junit.jupiter.api.Assertions.assertEquals(0, json.get("totalAssists").asInt());
                org.junit.jupiter.api.Assertions.assertEquals(0, json.get("totalAppearances").asInt());
            });
    }

    @Test
    @DisplayName("GET /api/v1/careers/.../players/{playerId}/stats — 404 when no V24 data (controller returns 404 on empty playerStats)")
    void getSinglePlayerStats_noV24Data_returns404() {
        String userId = uniqueUserId();
        String careerId = UUID.randomUUID().toString();
        String playerId = UUID.randomUUID().toString();

        webTestClient.mutateWith(mockUser(userId))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/careers/{careerId}/seasons/1/players/{playerId}/stats")
                .build(careerId, playerId))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound();
    }
}
