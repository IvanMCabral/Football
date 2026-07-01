package com.footballmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V24D7 FASE A — Base class for HTTP/E2E integration tests.
 *
 * <p>Boots the full Spring context against the isolated test environment:
 * <ul>
 *   <li>Postgres database {@code football_manager_test} (separate from dev DB)</li>
 *   <li>Redis logical database 15 (dev backend uses database 0)</li>
 *   <li>RANDOM_PORT to avoid clashes with the dev backend on :8080</li>
 * </ul>
 *
 * <p>Subclasses can inject {@link #webTestClient} and write tests against real
 * HTTP request/response flows. JWT-bypassing slices should use {@code @WebFluxTest}
 * with {@code @MockBean} for the use case instead — see FASE B/C.
 *
 * <p>Test isolation strategy:
 * <ul>
 *   <li>{@link #cleanRedis()} flushes Redis DB 15 between tests (cheap, fast)</li>
 *   <li>The DB is NOT truncated between tests — tests must be idempotent and
 *       must not rely on row counts. The seeded LaLiga data is stable.</li>
 * </ul>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        // Belt-and-suspenders: the application-test.yml also sets these,
        // but pinning them here makes the contract obvious to subclasses.
        "spring.flyway.enabled=false",
        "spring.data.redis.database=15"
    }
)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected DatabaseClient databaseClient;

    @Autowired
    protected ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @BeforeEach
    void cleanRedis() {
        // Wipe test Redis DB so state from a previous test does not leak.
        // Cheap (in-memory flush), avoids test-order coupling.
        reactiveRedisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands()
            .flushDb()
            .block();

        // V25D78-C55.4: also clean Postgres world tables that persist across
        // test runs in the same JVM. Without this, the BuildWorldViewUseCase
        // reloads stale teams + leagues (from a previous test run with
        // older algorithm-generated leagueIds) into the snapshot, and the
        // seeded data with the new LALIGA_ID constant becomes invisible
        // because the old Postgres teams carry old leagueIds.
        databaseClient.sql("DELETE FROM team_squad").fetch().rowsUpdated().onErrorResume(e -> reactor.core.publisher.Mono.just(0L)).block();
        databaseClient.sql("DELETE FROM players").fetch().rowsUpdated().onErrorResume(e -> reactor.core.publisher.Mono.just(0L)).block();
        databaseClient.sql("DELETE FROM teams").fetch().rowsUpdated().onErrorResume(e -> reactor.core.publisher.Mono.just(0L)).block();
        databaseClient.sql("DELETE FROM leagues_teams").fetch().rowsUpdated().onErrorResume(e -> reactor.core.publisher.Mono.just(0L)).block();
        databaseClient.sql("DELETE FROM leagues").fetch().rowsUpdated().onErrorResume(e -> reactor.core.publisher.Mono.just(0L)).block();
    }

    /**
     * V25D78-C55.5: seed LaLiga for the given user via POST /world/seed-la-liga.
     * Use in @BeforeEach of test classes that depend on the seeded data.
     */
    protected void seedLaLigaForUser(UUID userId) {
        webTestClient.mutateWith(mockUser(userId.toString()))
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed-la-liga")
                .queryParam("userId", userId)
                .build())
            .exchange()
            .expectStatus().isOk();
    }

    /**
     * V25D78-C55.5: fetch the teamId for the named team by filtering
     * /world/leagues/{laligaId}/teams. Replaces the legacy "first team"
     * helper that broke when C55.3 B1 extended LaLiga to 60 teams (the first
     * team alphabetically is now "Vigo City 1", a synthetic B1 add).
     *
     * <p>Returns the teamId whose {@code name} field matches {@code teamName}
     * (exact case-sensitive match). Caller must have already seeded LaLiga
     * via {@link #seedLaLigaForUser(UUID)}.
     *
     * @param laligaId  the LaLiga league UUID (stable constant
     *                   4feeb9df-4133-4655-883e-e96894907e7b)
     * @param teamName  the team name to look up (e.g. "Real Madrid",
     *                   "Barcelona", "Atletico Madrid")
     */
    protected String laligaTeamId(UUID userId, UUID laligaId, String teamName) {
        List<Map<String, Object>> teams = webTestClient.mutateWith(mockUser(userId.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues/{leagueId}/teams")
                .queryParam("userId", userId)
                .build(laligaId))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .returnResult()
            .getResponseBody();
        return teams.stream()
            .filter(t -> teamName.equals(t.get("name")))
            .map(t -> String.valueOf(t.get("worldTeamId")))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                teamName + " not found in LaLiga teams (got " + teams.size() + " teams)"));
    }
}
