package com.footballmanager.adapters.in.web.career.e2e;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.application.service.career.CareerSessionService;
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
 * V24D8-BUG-001 — E2E test for squad population after career creation.
 *
 * <p>Verifies that:
 * <ol>
 *   <li>Creating a career with La Liga + a team populates the squad with players</li>
 *   <li>GET /career/teams/me/squad returns &gt; 0 players (not empty)</li>
 *   <li>POST /career/lineup/auto-select returns 200 (not 500 NPE)</li>
 * </ol>
 *
 * <p>Bug: when career was created with the deprecated empty career path,
 * userSessionTeamId was null, causing ConcurrentHashMap NPE on squad/lineup queries.
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
@DisplayName("CareerSquadPopulation — E2E coverage for V24D8-BUG-001")
class CareerSquadPopulationE2ETest extends AbstractIntegrationTest {

    private static final UUID SEED_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LALIGA_ID =
        UUID.fromString("4feeb9df-4133-4655-883e-e96894907e7b");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private CareerSessionService careerSessionService;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
        // V25D78-C55.5: seed LaLiga per-test so seedTeamId/seedCareer find data
        seedLaLigaForUser(SEED_USER_ID);
        // V25D75-C40 A2: also clear the in-memory CareerSessionService cache.
        // Without this, CareerSquadFallbackE2ETest (which runs before us in
        // the full mvn test suite) leaves a cached career for SEED_USER_ID
        // that survives the Redis flushDb — getCareerFromCache returns the
        // stale entry and the noCareer test sees 200 instead of 422.
        careerSessionService.clearCache();
    }

    @Test
    @DisplayName("POST /career/start → GET /career/teams/me/squad returns ≥11 players")
    void careerCreation_populatesSquadWithPlayers() {
        // 1. Get first team from La Liga to use in career creation
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
        String teamId = teams.stream()
            .filter(t -> "Real Madrid".equals(t.get("name")))
            .map(t -> (String) t.get("worldTeamId"))
            .findFirst()
            .orElseGet(() -> (String) teams.get(0).get("worldTeamId"));  // V25D78-C55.5 fallback
        assertThat(teamId).isNotNull();

        // 2. Create career with La Liga + first team
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/career/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"leagueId\":\"%s\",\"teamId\":\"%s\",\"difficulty\":\"NORMAL\",\"gameSpeed\":\"NORMAL\",\"teamsPerDivision\":5}",
                LALIGA_ID, teamId))
            .exchange()
            .expectStatus().isCreated();

        // 3. GET squad — must return players (not empty list)
        List<Map<String, Object>> squad = webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/teams/me/squad")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .returnResult()
            .getResponseBody();

        assertThat(squad)
            .as("Squad must not be empty after career creation")
            .isNotNull()
            .hasSizeGreaterThanOrEqualTo(11);
    }

    @Test
    @DisplayName("POST /career/start → POST /career/lineup/auto-select returns 200 (no 500 NPE)")
    void careerCreation_autoSelectLineup_returns200() {
        // 1. Get first team from La Liga
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
        String teamId = teams.stream()
            .filter(t -> "Real Madrid".equals(t.get("name")))
            .map(t -> (String) t.get("worldTeamId"))
            .findFirst()
            .orElseGet(() -> (String) teams.get(0).get("worldTeamId"));  // V25D78-C55.5 fallback

        // 2. Create career
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/career/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"leagueId\":\"%s\",\"teamId\":\"%s\",\"difficulty\":\"NORMAL\",\"gameSpeed\":\"NORMAL\",\"teamsPerDivision\":5}",
                LALIGA_ID, teamId))
            .exchange()
            .expectStatus().isCreated();

        // 3. Auto-select lineup — must return 200, not 500
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/auto-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-4-2\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody(LineupDTO.class)
            .value(lineup -> assertThat(lineup.players()).hasSizeGreaterThanOrEqualTo(7));
    }

    @Test
    @DisplayName("GET /career/teams/me returns 200 even when no career exists (no Mono.just null NPE)")
    void noCareer_getMyTeam_returns200not500() {
        // No career created — should return 200 with null/empty body, not 500 NPE
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/teams/me")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("GET /career/players/squad returns 422 when no career (not 500 NPE)")
    void noCareer_getPlayersSquad_returns422not500() {
        // No career created — should return 422 (no career), not 500 NPE
        // The fix prevents ConcurrentHashMap.get(null) NPE
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/players/squad")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isEqualTo(422);
    }

    @Test
    @DisplayName("V24D8-BUG-003: POST /career/start → GET /career/status has non-null userSessionTeamId and squadSize > 0")
    void careerCreation_withFreshUser_hasValidUserSessionTeamId() {
        // 1. Get first team from La Liga
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
        String teamId = teams.stream()
            .filter(t -> "Real Madrid".equals(t.get("name")))
            .map(t -> (String) t.get("worldTeamId"))
            .findFirst()
            .orElseGet(() -> (String) teams.get(0).get("worldTeamId"));  // V25D78-C55.5 fallback

        // 2. Create career
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/career/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"leagueId\":\"%s\",\"teamId\":\"%s\",\"difficulty\":\"NORMAL\",\"gameSpeed\":\"NORMAL\",\"teamsPerDivision\":5}",
                LALIGA_ID, teamId))
            .exchange()
            .expectStatus().isCreated();

        // 3. GET career status — userSessionTeamId and userTeamId must NOT be null
        Map<String, Object> status = webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/status")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
            .returnResult()
            .getResponseBody();

        assertThat(status)
            .as("Career status must not be null")
            .isNotNull();
        assertThat(status.get("userSessionTeamId"))
            .as("userSessionTeamId must NOT be null after career creation")
            .isNotNull();
        assertThat(status.get("userTeamId"))
            .as("userTeamId must NOT be null after career creation")
            .isNotNull();
        assertThat(status.get("squadSize"))
            .as("squadSize must be > 0 after career creation")
            .isInstanceOf(Number.class);
        assertThat(((Number) status.get("squadSize")).intValue())
            .as("squadSize must be > 0")
            .isGreaterThan(0);
        assertThat(status.get("totalRounds"))
            .as("totalRounds must be > 0 after career creation")
            .isInstanceOf(Number.class);
        assertThat(((Number) status.get("totalRounds")).intValue())
            .as("totalRounds must be > 0")
            .isGreaterThan(0);
    }

    /**
     * V24D8-BUG-004: Squad shows "Player N MAD" placeholders instead of real La Liga player names.
     *
     * Fix: LaLigaSeedService.persistPlayerNamesInPostgres() now inserts players + team_squad entries
     * into PostgreSQL using DatabaseClient. BuildWorldViewUseCase rebuilds WorldView from Postgres
     * and loads real player names (Vinicius Jr., Bellingham, Mbappe, etc.).
     */
    @Test
    @DisplayName("V24D8-BUG-004: POST /world/seed-la-liga + POST /career/start → squad has REAL player names (not placeholders)")
    void seedLaLiga_careerStart_squadHasRealPlayerNames() {
        // V25D78-C55.4: replaced the hardcoded UUID with a dynamic lookup.
        // The previous UUID was tied to a specific seed-data version and
        // stopped matching Real Madrid's teamId after C55.1's UUID
        // generation algorithm. We look up Real Madrid by name via the
        // HTTP /world/leagues/{id}/teams endpoint (deterministic — the
        // seed JSON places Real Madrid first alphabetically).

        // 1. Seed La Liga
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed-la-liga")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk();

        // 2. Resolve Real Madrid's actual teamId (name -> uuid) by querying
        // the league teams endpoint and filtering for the name.
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
        String teamId = teams.stream()
            .filter(t -> "Real Madrid".equals(t.get("name")))
            .map(t -> String.valueOf(t.get("worldTeamId")))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "Real Madrid not found in LaLiga teams after seed (got "
                + teams.size() + " teams)"));

        // 3. Create career with Real Madrid
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri("/api/v1/career/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"leagueId\":\"%s\",\"teamId\":\"%s\",\"difficulty\":\"NORMAL\",\"gameSpeed\":\"NORMAL\",\"teamsPerDivision\":5}",
                LALIGA_ID, teamId))
            .exchange()
            .expectStatus().isCreated();

        // 4. GET squad — names must NOT match "Player N XXX" pattern
        List<Map<String, Object>> squad = webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri("/api/v1/career/teams/me/squad")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .returnResult()
            .getResponseBody();

        assertThat(squad)
            .as("Squad must not be empty")
            .isNotNull()
            .hasSizeGreaterThanOrEqualTo(11);

        // All squad players must have real names (not "Player N XXX" placeholders)
        for (Map<String, Object> player : squad) {
            String name = (String) player.get("name");
            assertThat(name)
                .as("Player name must NOT be a placeholder like 'Player N MAD'")
                .isNotNull()
                .doesNotMatch("^Player \\d+ [A-Z]{3}$");
        }

        // Verify Real Madrid key players are present (at least some of these)
        List<String> squadNames = squad.stream()
            .map(p -> (String) p.get("name"))
            .toList();

        // Real Madrid has these players in the seed data
        assertThat(squadNames)
            .as("Squad should contain real Real Madrid players (e.g. Bellingham, Mbappe, Vinicius)")
            .anyMatch(name -> name.contains("Bellingham") || name.contains("Mbappe") ||
                             name.contains("Vinicius") || name.contains("Modric") ||
                             name.contains("Courtois") || name.contains("Carvajal"));
    }
}
