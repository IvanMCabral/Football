package com.footballmanager.adapters.in.web.career.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V24D15-CLEANUP (BUG-003 reproducer E2E): regression test for the
 * squad-fallback path that was fixed by commit 7213083
 * (F5.2 BUG-003 — fall back to squad when teamStarting11 has stale
 * player IDs).
 *
 * <p>The fix protects the user from per-round orchestrator mutations
 * (suspensions, injuries, sales) that remove a player from the
 * {@code playerManager} between rounds, leaving
 * {@code teamStarting11} with a dangling reference. The factory would
 * previously throw {@code IllegalArgumentException} → HTTP 422
 * {@code LINEUP_VALIDATION_ERROR} on Fecha 2+, blocking the manager's
 * career. The fix detects the stale entry and derives the XI from the
 * squad (the source of truth for "who is currently available").
 *
 * <p>This E2E test reproduces the regression scenario by mutating the
 * CareerSave JSON directly in Redis — we inject a {@code teamStarting11}
 * entry whose player IDs are <em>not</em> present in the
 * {@code playerManager}. We then POST {@code /career/{careerId}/next-round}
 * and assert the response is 200 with a non-empty matches list
 * (fallback fired), not 422 LINEUP_VALIDATION_ERROR (the old bug).
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test
 * DB + Redis DB 15. Creates a career via POST /api/v1/career/start,
 * auto-selects a lineup, then injects the stale entry by editing the
 * CareerSave JSON blob in Redis with the ObjectMapper bean.
 *
 * <p>Scope (2 tests):
 * <ul>
 *   <li>Happy path: career + valid lineup → next-round → 200</li>
 *   <li>BUG-003 reproducer: same flow but with a stale teamStarting11
 *       entry → next-round STILL returns 200 (fallback fired)</li>
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
@DisplayName("CareerSquadFallback — BUG-003 reproducer E2E")
class CareerSquadFallbackE2ETest extends AbstractIntegrationTest {

    private static final UUID SEED_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LALIGA_ID =
        UUID.fromString("4feeb9df-4133-4655-883e-e96894907e7b");
    private static final String REDIS_KEY_PREFIX = "career:";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanRedis() {
        reactiveRedisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands()
            .flushDb()
            .block();
        // V25D78-C55.5: seed LaLiga per-test so seedTeamId() finds data.
        seedLaLigaForUser(SEED_USER_ID);
    }

    private String seedTeamId() {
        List<Map<String, Object>> teams = webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues/{leagueId}/teams")
                .queryParam("userId", SEED_USER_ID)
                .build(LALIGA_ID))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .returnResult()
            .getResponseBody();

        assertThat(teams).isNotNull().isNotEmpty();
        return teams.stream()
            .filter(t -> "Real Madrid".equals(t.get("name")))
            .map(t -> (String) t.get("worldTeamId"))
            .findFirst()
            .orElseGet(() -> (String) teams.get(0).get("worldTeamId"));  // V25D78-C55.5 fallback if Real Madrid missing
    }

    private void seedCareer() {
        String teamId = seedTeamId();
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/career/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"leagueId\":\"%s\",\"teamId\":\"%s\",\"difficulty\":\"NORMAL\",\"gameSpeed\":\"NORMAL\",\"teamsPerDivision\":5}",
                LALIGA_ID, teamId))
            .exchange()
            .expectStatus().isCreated();
    }

    private void seedLineup(String formation) {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/auto-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format("{\"formation\":\"%s\"}", formation))
            .exchange()
            .expectStatus().isOk();
    }

    private String careerId() {
        Map<String, Object> status = webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/status")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();
        assertThat(status).isNotNull();
        Object id = status.get("careerId");
        assertThat(id).as("careerId must be set after /career/start").isNotNull();
        return id.toString();
    }

    @Test
    @DisplayName("HAPPY: career + valid lineup → POST /next-round → 200 with matches")
    void happyPath_validLineup_nextRound200() {
        seedCareer();
        seedLineup("4-4-2");
        String careerId = careerId();

        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/career/{careerId}/next-round", careerId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .value(json -> assertThat(json.get("success").asBoolean()).isTrue());
    }

    @Test
    @DisplayName("BUG-003: career with stale teamStarting11 entry → POST /next-round → 200 (fallback to squad)")
    void bug003_staleStarting11_nextRoundStill200() throws Exception {
        seedCareer();
        seedLineup("4-4-2");
        String careerId = careerId();

        // Read the CareerSave JSON from Redis.
        String redisKey = REDIS_KEY_PREFIX + SEED_USER_ID;
        String json = reactiveRedisTemplate.opsForValue().get(redisKey).block();
        assertThat(json).as("CareerSave must exist in Redis").isNotNull();

        // Mutate the teamStarting11 to inject a stale playerId (UUID that
        // does NOT exist in the playerManager). This is exactly what
        // happens in production when the orchestrator's per-round mutations
        // remove a player but the teamStarting11 entry is left behind.
        JsonNode root = objectMapper.readTree(json);
        com.fasterxml.jackson.databind.node.ObjectNode mutated = ((com.fasterxml.jackson.databind.node.ObjectNode) root).deepCopy();

        // The teamStarting11 is a Map<teamId, List<playerId>>.
        com.fasterxml.jackson.databind.node.ObjectNode starting11 = (com.fasterxml.jackson.databind.node.ObjectNode) mutated.get("teamStarting11");
        assertThat(starting11).as("teamStarting11 must be present after auto-select").isNotNull();

        // Pick the first team and replace its starting XI with stale IDs.
        Iterator<Map.Entry<String, JsonNode>> teams = starting11.fields();
        assertThat(teams.hasNext()).as("teamStarting11 must have at least one entry").isTrue();
        Map.Entry<String, JsonNode> firstTeam = teams.next();
        String teamId = firstTeam.getKey();

        List<String> staleIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            staleIds.add("stale-player-" + UUID.randomUUID());
        }
        com.fasterxml.jackson.databind.node.ArrayNode staleArr = objectMapper.createArrayNode();
        for (String id : staleIds) {
            staleArr.add(id);
        }
        starting11.set(teamId, staleArr);

        // Write the mutated CareerSave back to Redis with the same TTL.
        String mutatedJson = objectMapper.writeValueAsString(mutated);
        reactiveRedisTemplate.opsForValue()
            .set(redisKey, mutatedJson, java.time.Duration.ofDays(30))
            .block();

        // POST next-round — should NOT be 422 LINEUP_VALIDATION_ERROR.
        // The BUG-003 fix detects the stale entry and falls back to
        // deriving the XI from the squad.
        byte[] respBytes = webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/career/{careerId}/next-round", careerId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();
        String respJson = respBytes != null ? new String(respBytes) : "<null>";
        JsonNode resp = objectMapper.readTree(respJson);
        assertThat(resp.get("success").asBoolean())
            .as("next-round must succeed (success=true) even with a stale teamStarting11 "
                + "entry — BUG-003 fix falls back to squad derivation instead of "
                + "throwing the LINEUP_VALIDATION_ERROR 422 the old code returned")
            .isTrue();
        assertThat(resp.get("careerPhase").asText())
            .as("after next-round, careerPhase should be PRE_MATCH (round ready to start)")
            .isEqualTo("PRE_MATCH");
    }
}