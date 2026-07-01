package com.footballmanager.adapters.in.web.career.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * LIVE-MATCH-F5.3.4: E2E HTTP coverage for the new
 * {@code GET /api/v1/match-engine/matches/{matchId}/roundId} helper endpoint
 * in {@link MatchEngineController}.
 *
 * <p>This endpoint is the resolution chain used by the front-end
 * {@code MatchEngineService.pauseRoundForMatch} to translate a {@code matchId}
 * (which the round-live component has) into the {@code roundId} that the
 * {@code RoundEngineRegistry} keys on. The front caches the result for 5
 * minutes, so this endpoint is hit once per round.
 *
 * <p>Note: {@code /api/v1/match-engine/**} is {@code permitAll} in
 * {@code SecurityConfig} (V24D12-C-3), so the helper itself doesn't enforce
 * auth — the upstream controllers that consume the same path
 * ({@code streamRoundState}, {@code pauseMatch}, etc.) enforce auth
 * in-code where needed. The roundId is also not user-scoped (it's just a
 * lookup against the live engine registry), so per-user ownership checks
 * don't apply here.
 *
 * <p>Coverage (4 tests):
 * <ul>
 *   <li>GET with malformed matchId — 400 BAD_REQUEST (UUID validation).</li>
 *   <li>GET with valid matchId but no registered engine — 404 NOT_FOUND.</li>
 *   <li>GET with valid matchId in a live round — 200 OK with {matchId, roundId}.</li>
 *   <li>GET with auth — 200 OK (auth is optional; helper is permitAll).</li>
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
@DisplayName("MatchEngineController — F5.3 roundId lookup helper (BUG-015)")
class MatchEngineControllerRoundIdLookupTest extends AbstractIntegrationTest {

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private RoundEngineRegistry roundEngineRegistry;

    @AfterEach
    void tearDown() {
        if (roundEngineRegistry.getActiveRoundCount() > 0) {
            roundEngineRegistry.stopAllEngines();
        }
    }

    @BeforeEach
    void seedLaLiga() {
        // V25D78-C55.5: seed LaLiga for SEED_USER_ID so seedTeamId/seedCareer
        // helpers find data. AbstractIntegrationTest.@BeforeEach already
        // cleaned Redis (flushDb + DELETE world tables).
        seedLaLigaForUser(UUID.fromString(SEED_USER_ID));
    }

    private static final String SEED_USER_ID =
        "00000000-0000-0000-0000-000000000001";

    private String uniqueUserId() {
        return UUID.randomUUID().toString();
    }

    private String seedTeamId() {
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

    private String seedCareerAndGameId(String userId) {
        // V25D78-C55.5: seed LaLiga for the random userId used by the test
        // (the auth principal in POST /games below is userId, so the
        // controller's BuildWorldView queries that user — which has no data
        // unless we seed it).
        seedLaLigaForUser(UUID.fromString(userId));
        String teamId = seedTeamId();
        String leagueId = seedLeagueId();
        String body = String.format(
            "{\"name\":\"%s\",\"teamId\":\"%s\",\"leagueId\":\"%s\",\"teamsPerDivision\":2}",
            "Career-" + UUID.randomUUID().toString().substring(0, 8),
            teamId, leagueId
        );

        JsonNode response = webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/games")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();

        // GameId is a value object that serializes as {"value":"..."} (no @JsonValue),
        // so we descend into id.value to get the raw UUID string.
        return response.get("id").get("value").asText();
    }

    /**
     * Starts a round and returns a {@code [roundId, matchId]} pair.
     */
    private String[] seedLiveRoundAndMatch(String userId, String careerId) {
        JsonNode fixtures = webTestClient.mutateWith(mockUser(userId))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/career/fixtures")
                .queryParam("round", 1)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();

        JsonNode firstMatch = fixtures.get(0);
        String matchId = firstMatch.get("matchId").asText();
        String homeTeamId = firstMatch.get("homeTeamId").asText();
        String awayTeamId = firstMatch.get("awayTeamId").asText();

        String roundId = UUID.randomUUID().toString();
        String body = String.format(
            "{\"roundId\":\"%s\",\"userId\":\"%s\",\"matches\":[{\"matchId\":\"%s\",\"homeTeamId\":\"%s\",\"awayTeamId\":\"%s\"}]}",
            roundId, userId, matchId, homeTeamId, awayTeamId
        );

        JsonNode response = webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/rounds/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();

        return new String[] { response.get("roundId").asText(), matchId };
    }

    // ========== Tests ==========

    @Test
    @DisplayName("GET roundId with malformed matchId — 400 BAD_REQUEST")
    void getRoundId_invalidUuid_returns400() {
        webTestClient.get().uri("/api/v1/match-engine/matches/{id}/roundId", "not-a-uuid")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("matchId"));
    }

    @Test
    @DisplayName("GET roundId with valid UUID but no registered engine — 404 NOT_FOUND")
    void getRoundId_noRegisteredEngine_returns404() {
        String matchId = UUID.randomUUID().toString();

        webTestClient.get().uri("/api/v1/match-engine/matches/{id}/roundId", matchId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("not registered"));
    }

    @Test
    @DisplayName("GET roundId with valid matchId in a live round — 200 OK with {matchId, roundId}")
    void getRoundId_liveMatch_returns200WithRoundId() {
        String userId = uniqueUserId();
        String careerId = seedCareerAndGameId(userId);
        String[] live = seedLiveRoundAndMatch(userId, careerId);
        String roundId = live[0];
        String matchId = live[1];

        webTestClient.get().uri("/api/v1/match-engine/matches/{id}/roundId", matchId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.matchId").isEqualTo(matchId)
            .jsonPath("$.roundId").isEqualTo(roundId);
    }

    @Test
    @DisplayName("GET roundId with auth header — 200 OK (endpoint is permitAll, auth optional)")
    void getRoundId_withAuth_returns200() {
        String userId = uniqueUserId();
        String careerId = seedCareerAndGameId(userId);
        String[] live = seedLiveRoundAndMatch(userId, careerId);
        String matchId = live[1];

        webTestClient.mutateWith(mockUser(userId))
            .get().uri("/api/v1/match-engine/matches/{id}/roundId", matchId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.matchId").isEqualTo(matchId);
    }
}