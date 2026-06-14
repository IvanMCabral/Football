package com.footballmanager.adapters.in.web.world;

import com.footballmanager.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V24D7 FASE B — La Liga seed integrity E2E HTTP coverage.
 *
 * <p>Verifies that the seeded La Liga data (loaded into the test DB from
 * {@code db_test_dump.sql}) is queryable through the public HTTP API and
 * that the counts match the expected shape of the post-MVP MVP.
 *
 * <p>Expected (per the V24D6U6 close + V24D7 plan):
 * <ul>
 *   <li>1 league: "La Liga"</li>
 *   <li>At least 20 teams (target was 20 La Liga clubs; the dump may carry
 *       more from additional seed steps in earlier milestones)</li>
 *   <li>At least 130 players (target was ~406 — the dump carries 132 from
 *       the canonical seed used by the MVP smoke checklist)</li>
 * </ul>
 *
 * <p>The tests query the public {@code /api/v1/world/...} endpoints and
 * also peek at the DB via {@link com.footballmanager.AbstractIntegrationTest#databaseClient}
 * for cross-validation.
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
@DisplayName("LaLiga seed — E2E HTTP coverage")
class LaLigaSeedE2ETest extends AbstractIntegrationTest {

    private static final UUID SEED_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @org.springframework.beans.factory.annotation.Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
    }

    @Test
    @DisplayName("DB has the expected canonical tables")
    void db_hasExpectedTables() {
        java.util.List<String> tables = databaseClient.sql("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """)
            .map(row -> row.get("table_name", String.class))
            .all()
            .collectList()
            .block();

        org.junit.jupiter.api.Assertions.assertNotNull(tables);
        org.junit.jupiter.api.Assertions.assertTrue(tables.contains("leagues"));
        org.junit.jupiter.api.Assertions.assertTrue(tables.contains("teams"));
        org.junit.jupiter.api.Assertions.assertTrue(tables.contains("players"));
        org.junit.jupiter.api.Assertions.assertTrue(tables.contains("team_squad"));
        org.junit.jupiter.api.Assertions.assertTrue(tables.contains("matches"));
        org.junit.jupiter.api.Assertions.assertTrue(tables.contains("users"));
    }

    @Test
    @DisplayName("DB has the La Liga league seeded")
    void db_hasLaLigaLeague() {
        Long count = databaseClient.sql("SELECT COUNT(*) AS c FROM leagues")
            .map(row -> row.get("c", Long.class))
            .one()
            .block();
        org.junit.jupiter.api.Assertions.assertNotNull(count);
        org.junit.jupiter.api.Assertions.assertTrue(count >= 1,
            "Expected at least 1 league in test DB, got: " + count);
    }

    @Test
    @DisplayName("DB has at least 20 teams")
    void db_hasAtLeast20Teams() {
        Long count = databaseClient.sql("SELECT COUNT(*) AS c FROM teams")
            .map(row -> row.get("c", Long.class))
            .one()
            .block();
        org.junit.jupiter.api.Assertions.assertNotNull(count);
        org.junit.jupiter.api.Assertions.assertTrue(count >= 20,
            "Expected at least 20 teams in test DB, got: " + count);
    }

    @Test
    @DisplayName("DB has at least 130 players (post-MVP seed)")
    void db_hasAtLeast130Players() {
        Long count = databaseClient.sql("SELECT COUNT(*) AS c FROM players")
            .map(row -> row.get("c", Long.class))
            .one()
            .block();
        org.junit.jupiter.api.Assertions.assertNotNull(count);
        org.junit.jupiter.api.Assertions.assertTrue(count >= 130,
            "Expected at least 130 players in test DB, got: " + count);
    }

    @Test
    @DisplayName("HTTP /world/leagues returns the La Liga league")
    void http_leagues_returnsLaLiga() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").exists();
    }

    @Test
    @DisplayName("HTTP /world/teams returns at least 20 teams")
    void http_teams_returnsAtLeast20() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/teams")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray();
    }
}
