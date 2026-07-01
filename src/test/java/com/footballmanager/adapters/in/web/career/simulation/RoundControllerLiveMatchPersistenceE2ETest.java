package com.footballmanager.adapters.in.web.career.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.footballmanager.AbstractIntegrationTest;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V25D76-C41: regression E2E for the C40-B3/C41 bug chain.
 *
 * <p><b>Root cause (C40-B3 + C41):</b> the live match path in
 * {@code RoundController.handleMatchFinished → persistFinishedMatch} tried to
 * derive the user namespace from {@code snap.userId()} /
 * {@code career.getUserId()}, but the V24 path's MatchSessionRegistry never
 * set the initial state's userId (RoundController line 52 — fixed in C41).
 * The C40 fallback to {@code UUID.randomUUID()} then persisted finished
 * matches under a junk key that {@code GET /api/v1/matches}
 * ({@code matchRepository.findAll(userId)}) could never find.
 *
 * <p><b>Why this test exists (V25D51/C13 pattern):</b> the C40 unit test
 * {@code CareerSquadPopulationE2ETest} mocked the persistence layer and
 * passed, but the production wire never routed correctly. This test
 * exercises the FULL path end-to-end (no mocks for persistence) — creating
 * a career, auto-selecting a lineup, starting a round via the live engine,
 * polling {@code GET /api/v1/matches} until the match appears, and asserting
 * it does. Catches any future regression where the persistence namespace
 * drifts from the query namespace.
 *
 * <p><b>Strategy:</b>
 * <ol>
 *   <li>Real {@code @SpringBootTest} against the isolated test DB + Redis DB 15.</li>
 *   <li>Create career + game via {@code POST /api/v1/games} (real LaLiga seed).</li>
 *   <li>Auto-select lineup ({@code POST /career/lineup/auto-select 4-4-2}).</li>
 *   <li>Start round via {@code POST /api/v1/match-engine/rounds/start}
 *       (V24 path: 90 ticks × ~500ms = ~45s per match; legacy path: 5 ticks
 *       × 500ms = ~2.5s; we wait up to 90s for safety).</li>
 *   <li>Poll {@code GET /api/v1/matches} every 1s with 90s budget — assert
 *       match is present (length >= 1, includes the played matchId).</li>
 * </ol>
 *
 * <p><b>Polling strategy:</b> the live engine runs in a separate scheduler
 * thread, so the HTTP POST returns immediately while the match continues
 * ticking. The handleMatchFinished callback fires asynchronously when the
 * match reaches minute 90. The test polls {@code GET /api/v1/matches} until
 * the match appears (or 90s timeout).
 *
 * <p><b>Scope (1 test):</b>
 * <ul>
 *   <li>{@code liveMatch_finishes_persistedToMatchRepository_findableByUserId}
 *       — full live match → persistence → query chain.</li>
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
@DisplayName("V25D76-C41 — live match persistence end-to-end regression")
class RoundControllerLiveMatchPersistenceE2ETest extends AbstractIntegrationTest {

    // V25D75-C40 / RoundControllerE2ETest: /api/v1/world/* only responds to this user.
    private static final String SEED_USER_ID =
        "00000000-0000-0000-0000-000000000001";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
        // V25D78-C55.5: seed LaLiga per-test so seedTeamId/seedCareer find data
        seedLaLigaForUser(UUID.fromString(SEED_USER_ID));
    }

    @Test
    @DisplayName("V25D76-C41: live match → /api/v1/matches returns the persisted match (no orphan key)")
    void liveMatch_finishes_persistedToMatchRepository_findableByUserId() throws Exception {
        // V25D78-C55.5: use SEED_USER_ID throughout. The @BeforeEach seeds
        // LaLiga for SEED_USER_ID, so the career-start endpoint must query
        // snapshots for the same user (BuildWorldViewUseCase.getOrCreateSnapshot
        // keys by userId; if the snapshot is empty for the test user, the
        // endpoint throws "La liga no tiene equipos"). The original random
        // userId was a C40-era collision workaround — no longer needed
        // because @BeforeEach re-seeds per-test.
        String userId = SEED_USER_ID;

        // 2. Seed La Liga already happened in @BeforeEach for SEED_USER_ID.

        // 3. Fetch a real La Liga teamId (Real Madrid fixed for determinism).
        //    The LALIGA_ID is a hardcoded UUID in the seed (deterministic).
        final String laligaId = "4feeb9df-4133-4655-883e-e96894907e7b";
        // V25D78-C55.5: filter for "Real Madrid" by name (C55.3 B1's 60-team
        // expansion means .get(0) is now "Vigo City 1", a synthetic B1 add,
        // not Real Madrid). Use the helper from AbstractIntegrationTest.
        String teamId = laligaTeamId(
            UUID.fromString(SEED_USER_ID),
            UUID.fromString(laligaId),
            "Real Madrid");

        // 4. Create career (POST /api/v1/career/start). The user is now bound to a career.
        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/career/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"leagueId\":\"%s\",\"teamId\":\"%s\",\"difficulty\":\"NORMAL\",\"gameSpeed\":\"NORMAL\",\"teamsPerDivision\":2}",
                laligaId, teamId))
            .exchange()
            .expectStatus().isCreated();

        // 5. Auto-select lineup (4-4-2). Without this, the V24 path's session
        //    factory throws on empty starting XI.
        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/career/lineup/auto-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-4-2\"}")
            .exchange()
            .expectStatus().isOk();

        // 6. Fetch the round-1 fixture (the only match we'll play).
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
        assertThat(fixtures).as("career fixtures must be initialized after auto-select").isNotNull().isNotEmpty();
        JsonNode firstMatch = fixtures.get(0);
        String matchId = firstMatch.get("matchId").asText();

        // 7. Start the round — kicks off the V24 live engine scheduler for this match.
        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/rounds/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"roundId\":\"%s\",\"userId\":\"%s\",\"matches\":["
                    + "{\"matchId\":\"%s\",\"homeTeamId\":\"%s\",\"awayTeamId\":\"%s\"}]}",
                UUID.randomUUID(), userId, matchId,
                firstMatch.get("homeTeamId").asText(),
                firstMatch.get("awayTeamId").asText()))
            .exchange()
            .expectStatus().isOk();

        // 8. Poll /api/v1/matches until the match appears (V24 engine: 90 ticks × 500ms
        //    ≈ 45s in the worst case; legacy path: 5 ticks ≈ 2.5s; we wait 90s for safety).
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        List<Map<String, Object>> matches = List.of();
        boolean found = false;
        while (System.nanoTime() < deadline) {
            matches = webTestClient.mutateWith(mockUser(userId))
                .get().uri("/api/v1/matches")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .returnResult()
                .getResponseBody();
            if (matches != null && !matches.isEmpty()) {
                // Assert the specific match is in the list (not just any match).
                found = matches.stream().anyMatch(m -> matchId.equals(m.get("id")));
                if (found) break;
            }
            Thread.sleep(1000);
        }

        // 9. Final assertions. C41 fix: the match MUST be findable via the user's
        //    namespace (no UUID.randomUUID() orphan key). C40 regression test would
        //    silently pass on broken persistence because the mocked repo didn't
        //    catch the namespace drift.
        assertThat(matches)
            .as("GET /api/v1/matches must return non-empty list after live match finishes "
                + "(C41 fix: persistFinishedMatch now uses the controller's auth userId "
                + "instead of falling back to UUID.randomUUID)")
            .isNotNull()
            .isNotEmpty();
        assertThat(found)
            .as("played matchId=%s must be present in /api/v1/matches response: %s",
                matchId, matches)
            .isTrue();
    }
}
