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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V24D24.2: E2E HTTP coverage for the {@code roundId} field that was added to
 * {@code FixtureQueryDtos.MatchInfo} (the inner fixture DTO returned by
 * {@code GET /api/v1/career/fixtures/round-with-bye}).
 *
 * <p>The front test-harness UI (Panel C → "Simulate round N" button) needs a
 * UUID {@code roundId} to POST against
 * {@code POST /api/v1/match-engine/rounds/start} without first registering a
 * live engine. The back now derives a deterministic UUID per
 * {@code (careerId, round)} so the front can pick the roundId directly from
 * the fixtures-with-bye response.
 *
 * <p>Coverage (4 tests):
 * <ul>
 *   <li>GET /fixtures/round-with-bye with auth — every match in every round
 *       carries a non-null {@code roundId} field.</li>
 *   <li>The {@code roundId} for a given (careerId, round) is stable across
 *       calls (deterministic — front can cache it).</li>
 *   <li>Different rounds yield different {@code roundId} values.</li>
 *   <li>Different careers yield different {@code roundId} values (careerId
 *       is part of the hash input, isolating the namespace).</li>
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
@DisplayName("CareerViewController — V24D24.2 roundId hydration in /fixtures/round-with-bye")
class CareerViewControllerRoundIdInByeResponseTest extends AbstractIntegrationTest {

    private static final String SEED_USER_ID =
        "00000000-0000-0000-0000-000000000001";

    @org.springframework.beans.factory.annotation.Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void seedLaLigaPerTest() {
        // V25D78-C55.5: this class had no @BeforeEach, so it relied on
        // test-order leakage to find LaLiga teams in Redis. With C55.4
        // flushDb per test, that broke. Seed LaLiga explicitly.
        seedLaLigaForUser(UUID.fromString(SEED_USER_ID));
    }

    @org.junit.jupiter.api.AfterEach
    void flushRedis() {
        if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
            redisTemplate.getConnectionFactory().getReactiveConnection()
                .serverCommands().flushDb().block();
        }
    }

    private String uniqueUserId() {
        return UUID.randomUUID().toString();
    }

    private String seedTeamId() {
        // V25D78-C55.5: filter for "Real Madrid" by name (C55.3 B1's 60-team
        // expansion means the alphabetically-first team is now a synthetic
        // B1 add like "Vigo City 1", not Real Madrid).
        java.util.List<java.util.Map<String, Object>> teams = webTestClient.mutateWith(mockUser(SEED_USER_ID))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/teams")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(new org.springframework.core.ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>() {})
            .returnResult()
            .getResponseBody();
        return teams.stream()
            .filter(t -> "Real Madrid".equals(t.get("name")))
            .map(t -> (String) t.get("worldTeamId"))
            .findFirst()
            .orElseGet(() -> (String) teams.get(0).get("worldTeamId"));  // fallback
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
     * Creates a fresh career via {@code POST /api/v1/games} and returns the
     * careerId string. The career is cached in Redis so subsequent
     * {@code /api/v1/career/**} calls can resolve it.
     */
    private String seedCareer(String userId) {
        // V25D78-C55.5: seed LaLiga for the random userId used by the test
        // (the auth principal in POST /games below is userId, so the
        // controller's BuildWorldView queries that user).
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

        // GameId serializes as {"value":"..."}; descend into id.value.
        return response.get("id").get("value").asText();
    }

    /**
     * Fetches the fixtures-with-bye response for the user's career.
     */
    private JsonNode fetchRoundsWithBye(String userId) {
        return webTestClient.mutateWith(mockUser(userId))
            .get().uri("/api/v1/career/fixtures/round-with-bye")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();
    }

    /**
     * Indexes the {@code roundId} per round number from the response so tests
     * can assert on the hydration contract.
     */
    private java.util.Map<Integer, String> extractRoundIdsPerRound(JsonNode response) {
        java.util.Map<Integer, String> roundIds = new java.util.HashMap<>();
        JsonNode rounds = response.get("rounds");
        if (rounds == null || !rounds.isArray()) {
            return roundIds;
        }
        for (JsonNode round : rounds) {
            int roundNumber = round.get("round").asInt();
            JsonNode matches = round.get("matches");
            if (matches == null || !matches.isArray() || matches.isEmpty()) {
                continue;
            }
            // All matches in the same round MUST share the same deterministic
            // roundId (roundId is keyed on (careerId, round), not on matchId).
            String firstRoundId = matches.get(0).get("roundId").asText();
            roundIds.put(roundNumber, firstRoundId);
        }
        return roundIds;
    }

    // ========== Tests ==========

    @Test
    @DisplayName("GET /fixtures/round-with-bye — every match carries a non-null roundId")
    void getRoundWithBye_everyMatchHasRoundId() {
        String userId = uniqueUserId();
        seedCareer(userId);

        JsonNode response = fetchRoundsWithBye(userId);
        JsonNode rounds = response.get("rounds");

        assertThat(rounds).isNotNull().isNotEmpty();
        int totalMatches = 0;
        int matchesWithRoundId = 0;
        for (JsonNode round : rounds) {
            JsonNode matches = round.get("matches");
            for (JsonNode match : matches) {
                totalMatches++;
                JsonNode roundIdNode = match.get("roundId");
                if (roundIdNode != null && !roundIdNode.isNull()) {
                    String roundId = roundIdNode.asText();
                    assertThat(roundId)
                        .as("match %s round %s must have a non-empty roundId",
                            match.get("matchId").asText(), round.get("round").asInt())
                        .isNotBlank();
                    // roundId must parse as a valid UUID (front uses UUID.fromString)
                    assertThatNoExceptionIsThrown(() -> UUID.fromString(roundId));
                    matchesWithRoundId++;
                }
            }
        }
        assertThat(matchesWithRoundId)
            .as("Every match in /fixtures/round-with-bye must carry a roundId")
            .isEqualTo(totalMatches);
    }

    @Test
    @DisplayName("roundId is deterministic: same (careerId, round) → same UUID across calls")
    void roundId_isDeterministicAcrossCalls() {
        String userId = uniqueUserId();
        seedCareer(userId);

        JsonNode firstCall = fetchRoundsWithBye(userId);
        JsonNode secondCall = fetchRoundsWithBye(userId);

        java.util.Map<Integer, String> first = extractRoundIdsPerRound(firstCall);
        java.util.Map<Integer, String> second = extractRoundIdsPerRound(secondCall);

        assertThat(first).isNotEmpty();
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("Different rounds yield different roundIds (round is part of the hash)")
    void roundId_differsByRound() {
        String userId = uniqueUserId();
        seedCareer(userId);

        JsonNode response = fetchRoundsWithBye(userId);
        java.util.Map<Integer, String> roundIds = extractRoundIdsPerRound(response);

        assertThat(roundIds).as("Need at least 2 rounds to compare").hasSizeGreaterThanOrEqualTo(2);

        Set<String> distinctRoundIds = new HashSet<>(roundIds.values());
        assertThat(distinctRoundIds)
            .as("Each round must have its own roundId — round number must be part of the hash")
            .hasSize(roundIds.size());
    }

    @Test
    @DisplayName("Different careers yield different roundIds (careerId is part of the hash)")
    void roundId_differsByCareer() {
        String userIdA = uniqueUserId();
        String userIdB = uniqueUserId();
        seedCareer(userIdA);
        seedCareer(userIdB);

        JsonNode careerA = fetchRoundsWithBye(userIdA);
        JsonNode careerB = fetchRoundsWithBye(userIdB);

        java.util.Map<Integer, String> roundIdsA = extractRoundIdsPerRound(careerA);
        java.util.Map<Integer, String> roundIdsB = extractRoundIdsPerRound(careerB);

        assertThat(roundIdsA).isNotEmpty();
        assertThat(roundIdsB).isNotEmpty();

        // Pick the smallest round present in both and compare.
        int commonRound = -1;
        Iterator<Integer> it = roundIdsA.keySet().iterator();
        while (it.hasNext()) {
            int r = it.next();
            if (roundIdsB.containsKey(r)) { commonRound = r; break; }
        }
        assertThat(commonRound).as("Both careers must have at least one common round").isNotEqualTo(-1);

        String roundIdA = roundIdsA.get(commonRound);
        String roundIdB = roundIdsB.get(commonRound);
        assertThat(roundIdA)
            .as("Round %s roundId in career A must differ from roundId in career B", commonRound)
            .isNotEqualTo(roundIdB);
    }

    private static void assertThatNoExceptionIsThrown(Runnable r) {
        try { r.run(); } catch (Throwable t) { throw new AssertionError("Expected no exception", t); }
    }
}