package com.footballmanager.adapters.in.web.career.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.CareerSave;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V25D78-C43 P0 (Bug #1 reproducer) — E2E test for the
 * "formation doesn't persist between seasons / after orchestrator save" bug.
 *
 * <p>Pre-fix root cause: {@code LineupCommandUseCaseImpl} wrote to Redis via
 * {@code careerRepository.save(career)} but did NOT update the in-memory
 * {@code careerCache}. Later, when {@code MatchSimulationOrchestrator}
 * (or any other code path) called {@code getCareerFromCache} + {@code saveCareer},
 * the cache returned a stale pre-lineup {@code CareerSave} and {@code saveCareer}
 * overwrote Redis with that stale object — wiping the lineup. The next
 * {@code GET /career/lineup/current} returned {@code formation:null, players:[]}.
 *
 * <p>This test reproduces the bug at the E2E level: after auto-select with
 * formation 4-3-3, we re-save the same {@code CareerSave} via
 * {@code CareerSessionService.saveCareer} (the path the orchestrator uses) —
 * pre-fix, this would wipe the lineup because the cache was stale; post-fix,
 * the cache is updated atomically with the auto-select save so the
 * "re-save same career" is a no-op and the lineup survives.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test
 * environment (DB {@code football_manager_test}, Redis DB 15, Flyway off,
 * RANDOM_PORT). Creates a career, auto-selects with 4-3-3, then re-saves
 * the SAME career and asserts the formation + 11-player lineup survive.
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
@DisplayName("V25D78-C43 P0 — Formation persistence after orchestrator save (Bug #1 reproducer E2E)")
class LineupFormationPersistenceE2ETest extends AbstractIntegrationTest {

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

    @Autowired
    private CareerSessionService careerSessionService;

    @BeforeEach
    void cleanState() {
        reactiveRedisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
        // V25D75-C40 A2: clear the in-memory cache between tests so a previous
        // test's cached CareerSave doesn't leak across the @BeforeEach.
        careerSessionService.clearCache();
    }

    private String seedRealTeam() {
        List<Map<String, Object>> teams = webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues/{leagueId}/teams")
                .queryParam("userId", SEED_USER_ID)
                .build(LALIGA_ID))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .returnResult()
            .getResponseBody();

        assertThat(teams).isNotNull().isNotEmpty();
        return (String) teams.get(0).get("worldTeamId");
    }

    private void seedCareer(String teamId) {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/career/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"leagueId\":\"%s\",\"teamId\":\"%s\",\"difficulty\":\"NORMAL\",\"gameSpeed\":\"NORMAL\",\"teamsPerDivision\":5}",
                LALIGA_ID, teamId))
            .exchange()
            .expectStatus().isCreated();
    }

    private JsonNode getCurrentLineup() {
        byte[] body = webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/lineup/current")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();
        assertThat(body).as("GET /career/lineup/current body").isNotNull();
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Bug #1 reproducer: auto-select 4-3-3 → orchestrator's saveCareer (cache sync) "
        + "→ lineup PERSISTS (formation 4-3-3, 11 players, 11 slots)")
    void formation_persists_afterOrchestratorStaleSave() throws Exception {
        // 1. Setup: create career with first LaLiga team (Real Madrid by sort order
        // for the current seed).
        String teamId = seedRealTeam();
        seedCareer(teamId);

        // 2. Auto-select with formation 4-3-3. This is the only mutation the
        // smoke test cares about — post-fix this MUST update the in-memory
        // careerCache atomically with the Redis save.
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/auto-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-3-3\"}")
            .exchange()
            .expectStatus().isOk();

        // 3. Sanity: lineup is set, formation persisted, 11 players, 11 slots.
        JsonNode lineupAfterAutoSelect = getCurrentLineup();
        assertThat(lineupAfterAutoSelect.get("formation").asText())
            .as("formation 4-3-3 must be persisted after auto-select")
            .isEqualTo("4-3-3");
        assertThat(lineupAfterAutoSelect.get("players").size())
            .as("11 players after auto-select")
            .isEqualTo(11);
        assertThat(lineupAfterAutoSelect.get("slots").size())
            .as("11 subdivision slots after auto-select")
            .isEqualTo(11);

        // 4. Simulate the orchestrator path: get the CACHED career (post-fix this
        // is the freshest career with the lineup — pre-fix it was the stale
        // pre-lineup career), then save it back via CareerSessionService.saveCareer
        // (the same method the orchestrator uses). This is the "save after match
        // results" pattern.
        CareerSave cachedAfterAutoSelect = careerSessionService.getCareerFromCache(SEED_USER_ID).block();
        assertThat(cachedAfterAutoSelect)
            .as("cache must be populated by getCareerFromCache after auto-select")
            .isNotNull();
        // The post-fix invariant: the cached career has the persisted lineup.
        assertThat(cachedAfterAutoSelect.getTeamStarting11())
            .as("post-fix: cache must have teamStarting11 entry (pre-fix it was empty)")
            .isNotEmpty();
        assertThat(cachedAfterAutoSelect.getTeamStarting11Formation())
            .as("post-fix: cache must have teamStarting11Formation entry (pre-fix it was empty)")
            .containsKey(cachedAfterAutoSelect.getUserSessionTeamId());

        // 5. Re-save the cached career (orchestrator's saveCareer pattern).
        // Post-fix: the cache already has the lineup, so this is a no-op.
        // Pre-fix: this would have wiped the lineup (because the cache was stale
        // pre-lineup and saveCareer would overwrite Redis with the stale object).
        careerSessionService.saveCareer(cachedAfterAutoSelect).block();

        // 6. Verify Redis state directly: the CareerSave JSON in Redis must still
        // contain the teamStarting11 + teamStarting11Formation entries. This is
        // the contract the orchestrator MUST NOT break.
        String redisJson = reactiveRedisTemplate.opsForValue()
            .get(REDIS_KEY_PREFIX + SEED_USER_ID)
            .block();
        assertThat(redisJson).as("CareerSave in Redis").isNotNull();
        JsonNode persisted = objectMapper.readTree(redisJson);
        JsonNode teamStarting11 = persisted.get("teamStarting11");
        assertThat(teamStarting11)
            .as("post-fix: teamStarting11 in Redis must have an entry (pre-fix the orchestrator's "
                + "saveCareer wiped this because the cache was stale)")
            .isNotNull();
        assertThat(teamStarting11.size())
            .as("post-fix: teamStarting11 must have ≥1 entry (the user's team)")
            .isGreaterThanOrEqualTo(1);
        JsonNode formationMap = persisted.get("teamStarting11Formation");
        assertThat(formationMap)
            .as("post-fix: teamStarting11Formation in Redis must have an entry")
            .isNotNull();
        assertThat(formationMap.size())
            .as("post-fix: teamStarting11Formation must have ≥1 entry (4-3-3)")
            .isGreaterThanOrEqualTo(1);

        // 7. Verify the API still returns the lineup end-to-end.
        JsonNode lineupAfterResave = getCurrentLineup();
        assertThat(lineupAfterResave.get("formation").asText())
            .as("formation 4-3-3 must SURVIVE the orchestrator's saveCareer (Bug #1 contract)")
            .isEqualTo("4-3-3");
        assertThat(lineupAfterResave.get("players").size())
            .as("11 players must SURVIVE the orchestrator's saveCareer (Bug #1 contract)")
            .isEqualTo(11);
        assertThat(lineupAfterResave.get("slots").size())
            .as("11 slots must SURVIVE the orchestrator's saveCareer (Bug #1 contract)")
            .isEqualTo(11);
    }

    @Test
    @DisplayName("Bug #1 contract: getCareerFromCache after auto-select returns career WITH the persisted lineup")
    void getCareerFromCache_afterAutoSelect_returnsCareerWithLineup() {
        // Direct test of the post-fix contract: after autoSelectLineup, the
        // CareerSessionService.getCareerFromCache must return a career whose
        // teamStarting11 and teamStarting11Formation maps are populated for the
        // user's team. Pre-fix, the cache was stale (the auto-select wrote to
        // Redis but didn't update the cache), so this assertion would fail
        // with an empty teamStarting11 — the orchestrator would then save that
        // empty career back to Redis, wiping the lineup.
        String teamId = seedRealTeam();
        seedCareer(teamId);

        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/auto-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-3-3\"}")
            .exchange()
            .expectStatus().isOk();

        CareerSave cached = careerSessionService.getCareerFromCache(SEED_USER_ID).block();
        assertThat(cached).isNotNull();
        String userTeamId = cached.getUserSessionTeamId();
        assertThat(userTeamId).isNotNull();

        assertThat(cached.getTeamStarting11().get(userTeamId))
            .as("post-fix: cached CareerSave must have teamStarting11 entry for user team "
                + "(this is the Bug #1 contract — pre-fix the cache was stale and the "
                + "orchestrator's saveCareer would have wiped the lineup)")
            .isNotNull()
            .hasSize(11);

        assertThat(cached.getTeamStarting11Formation().get(userTeamId))
            .as("post-fix: cached CareerSave must have teamStarting11Formation entry for user team")
            .isEqualTo("4-3-3");
    }
}
