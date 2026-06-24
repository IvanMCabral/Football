package com.footballmanager.adapters.in.web.career.simulation;

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
 * V24D7+2.2 — E2E HTTP coverage for {@link com.footballmanager.adapters.in.web.career.simulation.RoundController}.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test DB + Redis DB 15.
 * Creates a career via POST /api/v1/games, then exercises POST /api/v1/match-engine/rounds/start.
 *
 * <p>Helpers {@code seedTeamId} and {@code seedLeagueId} are copy-paste from
 * GameControllerE2ETest (V24D7+2.1, commit 9454092) and use the same field names:
 * {@code worldTeamId} (NOT {@code id}) for WorldTeam and {@code realLeagueId} (NOT {@code id})
 * for WorldLeague.
 *
 * <p><b>FIXED (V24D13-2):</b> {@code RoundController.startMatches} used to call
 * {@code careerSessionService.getCareerFromCache(userId).block()} on a reactor
 * thread, which throws {@code IllegalStateException("block() not supported in
 * thread parallel-N")}. The GlobalExceptionHandler mapped that to HTTP 422
 * LINEUP_STATE_ERROR, blocking the live smoke (posesión animándose, sustitución
 * en vivo). The fix loads CareerSave reactively via
 * {@code careerSessionService.getCareerFromCache(userId).switchIfEmpty(Mono.error(...))}
 * and dispatches per-match side effects inside {@code doOnNext}. The controller's
 * {@code onErrorResume} was also tightened to propagate IAE/ISE to
 * GlobalExceptionHandler (preserving the 4xx semantic codes) and only convert
 * genuinely unexpected errors to 500.
 *
 * <p><b>Error semantics:</b> {@code GlobalExceptionHandler} (a {@code @RestControllerAdvice})
 * intercepts BEFORE the controller's {@code onErrorResume} and returns 422 with
 * semantic {@code code} fields instead of generic 500. Tests assert both the
 * status and the {@code code}.
 *
 * <p>Scope (5 tests):
 * <ul>
 *   <li>POST /api/v1/match-engine/rounds/start without auth — 401 UNAUTHORIZED</li>
 *   <li>POST .../start with userId but no career in cache — 422 LINEUP_STATE_ERROR</li>
 *   <li>POST .../start with valid career + real fixture — 200 IN_PROGRESS (happy path)</li>
 *   <li>POST .../start with malformed roundId "not-a-uuid" — 422 LINEUP_VALIDATION_ERROR</li>
 *   <li>POST .../start with empty matches array — 200 IN_PROGRESS with empty list (workaround: seedFirstFixtureMatch first)</li>
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
@DisplayName("RoundController — E2E HTTP coverage")
class RoundControllerE2ETest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        // V24D7+2.2: clear career cache between tests. RoundController reads
        // CareerSave from Redis via careerSessionService.getCareerFromCache(userId).
        // Without flushDb, a previous test's career could leak into the next one.
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
    }

    // V24D7+2.1 hardcoded seed admin (copied from GameControllerE2ETest line 63-64).
    // /api/v1/world/* endpoints only respond to this user; random UUIDs return 500.
    private static final String SEED_USER_ID =
        "00000000-0000-0000-0000-000000000001";

    private String uniqueUserId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Fetches a real teamId from the LaLiga seed via the world/teams endpoint.
     * Copy-paste literal of GameControllerE2ETest.seedTeamId. Uses field "worldTeamId".
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

    /**
     * Fetches a real leagueId from the LaLiga seed via the world/leagues endpoint.
     * Copy-paste literal of GameControllerE2ETest.seedLeagueId. Uses field "realLeagueId".
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
     * V24D7+2.2 NEW helper. Creates a game (and thus a career in Redis) for the given userId.
     * Body matches GameControllerE2ETest.validCreateBody: teamId and leagueId are required
     * (controller returns 400 otherwise), and teamsPerDivision=2 is the safest minimum that
     * passes CreateCareerSnapshotUseCaseImpl validation (>= 2 && <= leagueTeams.size()).
     */
    private void seedCareerAndGame(String userId) {
        String teamId = seedTeamId(userId);
        String leagueId = seedLeagueId();
        String body = String.format(
            "{\"name\":\"%s\",\"teamId\":\"%s\",\"leagueId\":\"%s\",\"teamsPerDivision\":2}",
            "Career-" + UUID.randomUUID().toString().substring(0, 8),
            teamId, leagueId
        );

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/games")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isCreated();
    }

    /**
     * V24D7+2.2 NEW helper. Fetches the first fixture match for round=1 of the user's career.
     * Returns {@code String[3]} = [matchId, homeTeamId, awayTeamId].
     * Field names verified in FixtureQueryDtos.MatchInfo: all are plain String/Integer/Double,
     * NOT value object wrappers.
     *
     * <p>Throws if the array is empty — that means seedCareerAndGame did not actually
     * initialize the career with fixtures, which is exactly what the happy path is
     * supposed to guard against.
     */
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

        if (response == null || !response.isArray() || response.size() == 0) {
            throw new IllegalStateException(
                "Career fixtures not initialized for user " + userId
                    + " (CareerViewController returned empty array; seedCareerAndGame may have failed)"
            );
        }

        JsonNode firstMatch = response.get(0);
        return new String[] {
            firstMatch.get("matchId").asText(),
            firstMatch.get("homeTeamId").asText(),
            firstMatch.get("awayTeamId").asText()
        };
    }

    // ========== Tests ==========

    @Test
    @DisplayName("POST /api/v1/match-engine/rounds/start without auth — 401")
    void startRound_unauthenticated_returns401() {
        String body = String.format(
            "{\"roundId\":\"%s\",\"userId\":\"%s\",\"matches\":[]}",
            UUID.randomUUID(), UUID.randomUUID()
        );

        webTestClient.post().uri("/api/v1/match-engine/rounds/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("POST /api/v1/match-engine/rounds/start without career in cache — 422 LINEUP_STATE_ERROR (GlobalExceptionHandler)")
    void startRound_userWithoutCareer_returns422() {
        String userId = uniqueUserId();
        // No seedCareerAndGame call → no career in Redis → RoundController.startMatches
        // throws IllegalStateException("Career not found for user: ..."), but
        // GlobalExceptionHandler (@RestControllerAdvice) intercepts BEFORE the
        // controller's onErrorResume and maps to 422 LINEUP_STATE_ERROR.
        String body = String.format(
            "{\"roundId\":\"%s\",\"userId\":\"%s\",\"matches\":[]}",
            UUID.randomUUID(), userId
        );

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/rounds/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isEqualTo(422)
            .expectBody()
            .jsonPath("$.code").isEqualTo("LINEUP_STATE_ERROR");
    }

    @Test
    @DisplayName("POST /api/v1/match-engine/rounds/start with valid career and real fixture — 200 IN_PROGRESS")
    void startRound_validCareer_returns200() {
        String userId = uniqueUserId();

        // 1) Crear game (inicializa career en Redis para userId)
        seedCareerAndGame(userId);

        // 2) Tomar primer fixture real del career
        String[] fixture = seedFirstFixtureMatch(userId);
        String matchId = fixture[0];
        String homeTeamId = fixture[1];
        String awayTeamId = fixture[2];

        // 3) Start round con esos IDs
        String body = String.format(
            "{\"roundId\":\"%s\",\"userId\":\"%s\",\"matches\":[{\"matchId\":\"%s\",\"homeTeamId\":\"%s\",\"awayTeamId\":\"%s\"}]}",
            UUID.randomUUID(), userId, matchId, homeTeamId, awayTeamId
        );

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/rounds/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .value(json -> {
                org.junit.jupiter.api.Assertions.assertNotNull(json.get("roundId"));
                org.junit.jupiter.api.Assertions.assertNotNull(json.get("timestamp"));
                org.junit.jupiter.api.Assertions.assertNotNull(json.get("matches"));
                org.junit.jupiter.api.Assertions.assertNotNull(json.get("status"));
                org.junit.jupiter.api.Assertions.assertEquals("IN_PROGRESS", json.get("status").asText());
            });
    }

    @Test
    @DisplayName("POST /api/v1/match-engine/rounds/start with malformed roundId — 422 LINEUP_VALIDATION_ERROR (UUID.fromString throws IllegalArgumentException)")
    void startRound_invalidRoundIdFormat_returns422() {
        String userId = uniqueUserId();
        seedCareerAndGame(userId);

        String body = String.format(
            "{\"roundId\":\"not-a-uuid\",\"userId\":\"%s\",\"matches\":[]}",
            userId
        );

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/rounds/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isEqualTo(422)
            .expectBody()
            .jsonPath("$.code").isEqualTo("LINEUP_VALIDATION_ERROR");
    }

    @Test
    @DisplayName("POST /api/v1/match-engine/rounds/start with empty matches array — 200 with empty list")
    void startRound_emptyMatchesArray_returns200() {
        String userId = uniqueUserId();
        seedCareerAndGame(userId);

        String body = String.format(
            "{\"roundId\":\"%s\",\"userId\":\"%s\",\"matches\":[]}",
            UUID.randomUUID(), userId
        );

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/rounds/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .value(json -> {
                org.junit.jupiter.api.Assertions.assertNotNull(json.get("status"));
                org.junit.jupiter.api.Assertions.assertEquals("IN_PROGRESS", json.get("status").asText());
                org.junit.jupiter.api.Assertions.assertTrue(json.get("matches").isArray());
                org.junit.jupiter.api.Assertions.assertEquals(0, json.get("matches").size());
            });
    }
}
