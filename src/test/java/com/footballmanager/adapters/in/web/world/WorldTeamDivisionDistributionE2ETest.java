package com.footballmanager.adapters.in.web.world;

import com.fasterxml.jackson.databind.JsonNode;
import com.footballmanager.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V25D78-C55.6.1 — Regression test for the per-league division
 * distribution in {@code WorldTeam}. The C55.6 fix added the
 * {@code division} field but did not enforce the canonical 20/20/20
 * split per league; the FE smoke confirmed 60/0/0 (all PRIMERA) for
 * LaLiga instead of 20 PRIMERA + 20 SEGUNDA + 20 TERCERA.
 *
 * <p>Root cause: both {@code LaLigaSeedService} and {@code WorldSeedService}
 * hardcoded {@link com.footballmanager.domain.model.valueobject.Division#defaultDivision()}
 * (=PRIMERA) at team create time. The C55.6 design deferred redistribution
 * to the V25D80 SQL migration, but the {@code BuildWorldViewUseCase} read
 * path returns the Redis {@code WorldSnapshot} verbatim — never queries
 * Postgres for division. Plus {@code LaLigaSeedService} never even calls
 * {@code persistTeamsInPostgres}, so V25D80 has no rows to redistribute.
 *
 * <p>This test exercises end-to-end:
 * <ol>
 *   <li>Seed LaLiga via {@code POST /world/seed-la-liga} (legacy path that
 *       always runs through {@code LaLigaSeedService.applySeed}).</li>
 *   <li>Seed Premier via {@code POST /world/seed/premier} (new path that
 *       runs through {@code WorldSeedService.applySeed}).</li>
 *   <li>{@code GET /world/teams?userId=...} (the bug surface) and
 *       assert the per-league distribution is exactly 20/20/20.</li>
 * </ol>
 *
 * <p>Distribution logic: V25D80 SQL ranks teams by name within each league
 * (alphabetical), assigns top N/3 = PRIMERA, mid N/3 = SEGUNDA, last N/3 =
 * TERCERA. Java equivalent lives in {@code DivisionRankDistributor}.
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
@DisplayName("WorldTeam — V25D78-C55.6.1 per-league division distribution (20/20/20)")
class WorldTeamDivisionDistributionE2ETest extends AbstractIntegrationTest {

    private static final UUID LALIGA_ID =
        UUID.fromString("4feeb9df-4133-4655-883e-e96894907e7b");

    private static final UUID SEED_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-00000000c561");

    /**
     * V25D78-C55.5: setup common to both tests — clean Redis + Postgres world
     * tables (handled by {@link AbstractIntegrationTest#cleanRedis()}) and
     * seed LaLiga (LALIGA_ID = 4feeb9df-... is the hardcoded constant from
     * C55.4).
     */
    @BeforeEach
    void setUp() {
        // AbstractIntegrationTest.@BeforeEach already cleans Redis + world tables.
        // Seed LaLiga via legacy endpoint (covers LaLigaSeedService.applySeed).
        seedLaLigaForUser(SEED_USER_ID);
    }

    @Test
    @DisplayName("LaLiga seeded via /world/seed-la-liga: GET /world/teams returns "
        + "exactly {PRIMERA: 20, SEGUNDA: 20, TERCERA: 20}")
    void seededLaLiga_hasCorrectDivisionDistribution_20_20_20() {
        // Read all teams — runtime response, no DB inspection.
        JsonNode teams = webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/teams")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();

        assertThat(teams).as("response must be a non-empty array").isNotNull();
        assertThat(teams.isArray()).as("response must be a JSON array").isTrue();
        assertThat(teams.size())
            .as("LaLiga seed produces 60 teams (V25D78-C55.3 B1 contract)")
            .isEqualTo(60);

        Map<String, Integer> distribution = countByDivision(teams);
        assertThat(distribution)
            .as("LaLiga must have canonical 20/20/20 division distribution. "
                + "Pre-fix was 60 PRIMERA (C55.6 deferred to V25D80 migration "
                + "that never applied to LaLiga — see C55.6.1 bug report).")
            .containsEntry("PRIMERA", 20)
            .containsEntry("SEGUNDA", 20)
            .containsEntry("TERCERA", 20);
    }

    @Test
    @DisplayName("Premier seeded via /world/seed/premier: GET /world/teams returns "
        + "exactly {PRIMERA: 20, SEGUNDA: 20, TERCERA: 20}")
    void seededPremier_hasCorrectDivisionDistribution_20_20_20() {
        // Seed Premier via the multi-league endpoint (covers WorldSeedService.applySeed).
        // The /world/seed-la-liga seed from @BeforeEach already populated LaLiga;
        // adding Premier exercises the WorldSeedService code path which has its
        // own applySeed() invocation of DivisionRankDistributor.
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed/premier")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk();

        // Use the per-league endpoint so the assertion is scoped to Premier only
        // (LaLiga teams from the @BeforeEach setup must NOT count toward the
        // 20/20/20 expectation).
        UUID premierLeagueId = findLeagueIdByName(SEED_USER_ID, "Premier League 2024/25");

        assertThat(premierLeagueId)
            .as("/world/leagues must contain Premier League 2024/25 after seed/premier")
            .isNotNull();

        JsonNode premierTeams = webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues/{leagueId}/teams")
                .queryParam("userId", SEED_USER_ID)
                .build(premierLeagueId))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();

        assertThat(premierTeams).as("Premier teams response must be a non-empty array")
            .isNotNull();
        assertThat(premierTeams.isArray()).as("Premier response must be a JSON array")
            .isTrue();
        assertThat(premierTeams.size())
            .as("Premier seed produces 60 teams (V25D78-C55.1 contract)")
            .isEqualTo(60);

        Map<String, Integer> distribution = countByDivision(premierTeams);
        assertThat(distribution)
            .as("Premier must have canonical 20/20/20 division distribution. "
                + "Same fix path as LaLiga — WorldSeedService.applySeed now "
                + "invokes DivisionRankDistributor before saveSnapshot.")
            .containsEntry("PRIMERA", 20)
            .containsEntry("SEGUNDA", 20)
            .containsEntry("TERCERA", 20);
    }

    // ========== Helpers ==========

    /**
     * Count occurrences of each division value in a JSON array of team
     * objects. Skips teams with missing/null/invalid {@code division} field.
     */
    private Map<String, Integer> countByDivision(JsonNode teams) {
        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode team : teams) {
            JsonNode div = team.get("division");
            if (div == null || div.isNull() || !div.isTextual()) {
                continue;
            }
            counts.merge(div.asText(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Look up the league UUID by name via {@code GET /world/leagues}. Returns
     * null if not found. Uses the {@code realLeagueId} field (UUID) so the
     * test stays robust to enum-name changes upstream.
     */
    private UUID findLeagueIdByName(UUID userId, String leagueName) {
        JsonNode leagues = webTestClient.mutateWith(mockUser(userId.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues")
                .queryParam("userId", userId)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();

        if (leagues == null || !leagues.isArray()) {
            return null;
        }
        for (JsonNode league : leagues) {
            if (leagueName.equals(league.path("name").asText())) {
                String realLeagueId = league.path("realLeagueId").asText();
                if (realLeagueId != null && !realLeagueId.isEmpty()) {
                    return UUID.fromString(realLeagueId);
                }
            }
        }
        return null;
    }
}
