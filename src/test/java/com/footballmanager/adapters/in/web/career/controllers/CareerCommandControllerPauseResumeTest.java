package com.footballmanager.adapters.in.web.career.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import org.junit.jupiter.api.AfterEach;
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
 * {@code POST /api/v1/career/{careerId}/round/{roundId}/pause} and
 * {@code /resume} endpoints in {@link CareerCommandController}.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test DB +
 * Redis DB 15 (same as {@code RoundControllerE2ETest}). Creates a career via
 * POST {@code /api/v1/games}, starts a round via {@code RoundController}'s
 * {@code POST /api/v1/match-engine/rounds/start} (so the {@link RoundEngineRegistry}
 * actually has a live engine for the roundId), then exercises pause/resume.
 *
 * <p>Coverage (7 tests):
 * <ul>
 *   <li>POST pause without auth — 401 UNAUTHORIZED (SecurityConfig rule on /api/v1/career/**).</li>
 *   <li>POST pause with malformed roundId — 400 BAD_REQUEST (controller-level UUID validation).</li>
 *   <li>POST pause with auth + valid roundId but no registered engine — 404 NOT_FOUND.</li>
 *   <li>POST pause with auth + live round — 200 OK with {@code success=true}, {@code alreadyPaused=false}.</li>
 *   <li>POST pause 2nd time (idempotency) — 200 OK with {@code alreadyPaused=true} (RoundEngine.pauseAll early-returns).</li>
 *   <li>POST resume with live round — 200 OK with {@code success=true}, {@code wasPaused=true}.</li>
 *   <li>POST resume 2nd time (idempotency) — 200 OK with {@code wasPaused=false} (RoundEngine.resumeAll early-returns).</li>
 * </ul>
 *
 * <p>The {@code SSE snapshot isPaused=true/false} E2E lives in a separate
 * test ({@code MatchEngineControllerPauseResumeSseE2ETest}) — it needs a
 * running SSE consumer which is heavy. The unit-level idempotency story is
 * covered here via the 2nd-call assertions.
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
@DisplayName("CareerCommandController — F5.3 pause/resume round (BUG-015)")
class CareerCommandControllerPauseResumeTest extends AbstractIntegrationTest {

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private RoundEngineRegistry roundEngineRegistry;

    @AfterEach
    void tearDown() {
        // Defensive cleanup so round engines from one test don't leak into
        // the next. CareerControllerE2E tests don't clean up either, so this
        // is good hygiene for the pause/resume tests.
        if (roundEngineRegistry.getActiveRoundCount() > 0) {
            roundEngineRegistry.stopAllEngines();
        }
        if (redisTemplate.getConnectionFactory() != null) {
            redisTemplate.getConnectionFactory().getReactiveConnection()
                .serverCommands().flushDb().block();
        }
    }

    // V24D7+2.1 hardcoded seed admin (copied from RoundControllerE2ETest).
    private static final String SEED_USER_ID =
        "00000000-0000-0000-0000-000000000001";

    private String uniqueUserId() {
        return UUID.randomUUID().toString();
    }

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
     * Creates a game and returns the {@code gameId} (== careerId in the
     * current model: the front treats {@code gameId} as the roundId in
     * {@code startRoundEngine}, so they share the same identifier space).
     *
     * <p>The {@code GameId} value object serializes as {@code {"value":"..."}}
     * (no {@code @JsonValue} on the VO), so we descend into {@code id.value}
     * to get the raw UUID string.
     */
    private String seedCareerAndGameId(String userId) {
        String teamId = seedTeamId(userId);
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

        return response.get("id").get("value").asText();
    }

    private String[] seedFirstFixtureMatch(String userId) {
        JsonNode response = webTestClient.mutateWith(mockUser(userId))
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

        JsonNode firstMatch = response.get(0);
        return new String[] {
            firstMatch.get("matchId").asText(),
            firstMatch.get("homeTeamId").asText(),
            firstMatch.get("awayTeamId").asText()
        };
    }

    /**
     * Starts a round (POST /api/v1/match-engine/rounds/start) so the
     * {@link RoundEngineRegistry} has a live {@code RoundEngine} for
     * the returned roundId. Returns the roundId.
     */
    private String seedLiveRound(String userId, String careerId) {
        String[] fixture = seedFirstFixtureMatch(userId);
        String matchId = fixture[0];
        String homeTeamId = fixture[1];
        String awayTeamId = fixture[2];

        // roundId is generated by the controller (RoundController) — we
        // cannot predict it. We pass an arbitrary UUID and read it back
        // from the response body (RoundController returns {roundId,...}).
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

        return response.get("roundId").asText();
    }

    // ========== Tests ==========

    @Test
    @DisplayName("POST pause without auth — 401 UNAUTHORIZED")
    void pauseRound_unauthenticated_returns401() {
        String careerId = UUID.randomUUID().toString();
        String roundId = UUID.randomUUID().toString();

        webTestClient.post().uri("/api/v1/career/{c}/round/{r}/pause", careerId, roundId)
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("POST pause with malformed roundId — 400 BAD_REQUEST (UUID validation)")
    void pauseRound_invalidRoundIdFormat_returns400() {
        String userId = uniqueUserId();
        String careerId = UUID.randomUUID().toString();

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/career/{c}/round/{r}/pause", careerId, "not-a-uuid")
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("roundId"));
    }

    @Test
    @DisplayName("POST pause with auth + valid roundId but no registered engine — 404 NOT_FOUND")
    void pauseRound_noRegisteredEngine_returns404() {
        String userId = uniqueUserId();
        String careerId = UUID.randomUUID().toString();
        // Random UUID that was never registered in the RoundEngineRegistry
        String roundId = UUID.randomUUID().toString();

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/career/{c}/round/{r}/pause", careerId, roundId)
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("round not found"));
    }

    @Test
    @DisplayName("POST pause with auth + live round — 200 OK with success=true, alreadyPaused=false")
    void pauseRound_liveRound_returns200WithSuccess() {
        String userId = uniqueUserId();
        String careerId = seedCareerAndGameId(userId);
        String roundId = seedLiveRound(userId, careerId);

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/career/{c}/round/{r}/pause", careerId, roundId)
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.careerId").isEqualTo(careerId)
            .jsonPath("$.roundId").isEqualTo(roundId)
            .jsonPath("$.alreadyPaused").isEqualTo(false)
            .jsonPath("$.alreadyFinished").isEqualTo(false);
    }

    @Test
    @DisplayName("POST pause 2nd time (idempotency) — 200 OK with alreadyPaused=true (RoundEngine.pauseAll early-returns)")
    void pauseRound_idempotent_returns200WithAlreadyPausedTrue() {
        String userId = uniqueUserId();
        String careerId = seedCareerAndGameId(userId);
        String roundId = seedLiveRound(userId, careerId);

        // First pause — flips isPaused from false to true.
        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/career/{c}/round/{r}/pause", careerId, roundId)
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.alreadyPaused").isEqualTo(false);

        // Second pause — engine is already paused, so pauseAll early-returns.
        // The controller reads isPaused() BEFORE calling pauseAll() so it can
        // surface alreadyPaused=true.
        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/career/{c}/round/{r}/pause", careerId, roundId)
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.alreadyPaused").isEqualTo(true);
    }

    @Test
    @DisplayName("POST resume after pause — 200 OK with wasPaused=true")
    void resumeRound_afterPause_returns200WithWasPausedTrue() {
        String userId = uniqueUserId();
        String careerId = seedCareerAndGameId(userId);
        String roundId = seedLiveRound(userId, careerId);

        // First pause the round so resume has something to do.
        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/career/{c}/round/{r}/pause", careerId, roundId)
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk();

        // Now resume.
        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/career/{c}/round/{r}/resume", careerId, roundId)
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.wasPaused").isEqualTo(true)
            .jsonPath("$.alreadyFinished").isEqualTo(false);
    }

    @Test
    @DisplayName("POST resume 2nd time (idempotency) — 200 OK with wasPaused=false")
    void resumeRound_idempotent_returns200WithWasPausedFalse() {
        String userId = uniqueUserId();
        String careerId = seedCareerAndGameId(userId);
        String roundId = seedLiveRound(userId, careerId);

        // Pause once.
        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/career/{c}/round/{r}/pause", careerId, roundId)
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk();

        // First resume — flips isPaused from true to false.
        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/career/{c}/round/{r}/resume", careerId, roundId)
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.wasPaused").isEqualTo(true);

        // Second resume — engine is already running, resumeAll early-returns.
        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/career/{c}/round/{r}/resume", careerId, roundId)
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.wasPaused").isEqualTo(false);
    }
}