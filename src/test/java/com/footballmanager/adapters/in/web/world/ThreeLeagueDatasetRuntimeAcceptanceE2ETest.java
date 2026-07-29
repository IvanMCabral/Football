package com.footballmanager.adapters.in.web.world;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.world.importer.ThreeLeagueDatasetImporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.flyway.enabled=false",
        "spring.data.redis.database=15"
    }
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("MVP 1 three-league dataset runtime acceptance")
class ThreeLeagueDatasetRuntimeAcceptanceE2ETest extends AbstractIntegrationTest {

    private static final List<LeagueCase> LEAGUES = List.of(
        new LeagueCase("Spanish Primera Division", "ESP", 20),
        new LeagueCase("Argentine Primera Division", "ARG", 30),
        new LeagueCase("Brazilian Serie A", "BRA", 20)
    );

    @Autowired
    private ThreeLeagueDatasetImporter importer;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private CareerSessionService careerSessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetRuntimeState() {
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands()
            .flushDb()
            .block();
        careerSessionService.clearCache();
    }

    @Test
    @DisplayName("imports, exposes and starts playable careers for Spain, Argentina and Brazil")
    void importedThreeLeagueDatasetSupportsPlayableCareerSetup() {
        importer.importDataset();

        for (LeagueCase league : LEAGUES) {
            UUID userId = UUID.nameUUIDFromBytes(("runtime-acceptance|" + league.country()).getBytes());
            Map<String, Object> worldLeague = findLeague(userId, league);
            String leagueId = String.valueOf(worldLeague.get("realLeagueId"));

            List<Map<String, Object>> teams = teamsForLeague(userId, leagueId);
            assertThat(teams)
                .as(league.name() + " team coverage")
                .hasSize(league.expectedTeams());

            String teamId = String.valueOf(teams.get(0).get("worldTeamId"));
            List<Map<String, Object>> players = playersForTeam(userId, teamId);
            assertThat(players)
                .as(league.name() + " first team squad")
                .hasSizeGreaterThanOrEqualTo(24);
            assertThat(players)
                .allSatisfy(player -> {
                    assertThat(player.get("baseAttack")).isNotNull();
                    assertThat(player.get("baseDefense")).isNotNull();
                    assertThat(player.get("baseTechnique")).isNotNull();
                    assertThat(player.get("baseSpeed")).isNotNull();
                    assertThat(player.get("baseStamina")).isNotNull();
                    assertThat(player.get("baseMentality")).isNotNull();
                });

            startCareer(userId, leagueId, teamId);
            List<Map<String, Object>> squad = squad(userId);
            assertThat(squad)
                .as(league.name() + " career squad")
                .hasSizeGreaterThanOrEqualTo(24);
            autoSelect(userId);
        }
        assertGeneratedTraitCoverage();
    }

    @Test
    @DisplayName("rolls back generated data when the transactional import fails")
    void importerRollsBackWhenWriteFails() {
        deleteGeneratedDatasetRows();
        jdbcTemplate.update("DELETE FROM players WHERE source_system = 'conflict-fixture'");
        UUID conflictingPlayerId = deterministicUuid("player:ESP:athletic-club:1");
        jdbcTemplate.update("""
            INSERT INTO players (
                id, source_system, source_id, name, display_name, age, birth_date, position,
                dominant_foot, shirt_number, attack, defense, technique, speed, stamina,
                mentality, market_value, weekly_salary, height_cm, skill_levels_json
            ) VALUES (?, 'conflict-fixture', 'conflict-player', 'Conflict Player', 'Conflict',
                25, DATE '2001-01-01', 'CM', 'RIGHT', 99, 50, 50, 50, 50, 50, 50,
                1, 1, 180, '{"PASSER":50}')
            """, conflictingPlayerId);

        assertThatThrownBy(() -> importer.importDataset())
            .isInstanceOf(RuntimeException.class);

        assertThat(countGenerated("clubs")).isZero();
        assertThat(countGeneratedTeams()).isZero();
        assertThat(countGenerated("players")).isZero();
    }

    private Map<String, Object> findLeague(UUID userId, LeagueCase league) {
        List<Map<String, Object>> leagues = webTestClient.mutateWith(mockUser(userId.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues")
                .queryParam("userId", userId)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .returnResult()
            .getResponseBody();

        assertThat(leagues).isNotNull();
        return leagues.stream()
            .filter(row -> league.name().equals(row.get("name")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing imported league: " + league.name()));
    }

    private List<Map<String, Object>> teamsForLeague(UUID userId, String leagueId) {
        List<Map<String, Object>> teams = webTestClient.mutateWith(mockUser(userId.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues/{leagueId}/teams")
                .queryParam("userId", userId)
                .build(leagueId))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .returnResult()
            .getResponseBody();
        assertThat(teams).isNotNull();
        return teams;
    }

    private List<Map<String, Object>> playersForTeam(UUID userId, String teamId) {
        List<Map<String, Object>> players = webTestClient.mutateWith(mockUser(userId.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/teams/{teamId}/players")
                .queryParam("userId", userId)
                .build(teamId))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .returnResult()
            .getResponseBody();
        assertThat(players).isNotNull();
        return players;
    }

    private void startCareer(UUID userId, String leagueId, String teamId) {
        webTestClient.mutateWith(mockUser(userId.toString()))
            .post().uri("/api/v1/career/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"leagueId":"%s","teamId":"%s","difficulty":"NORMAL","gameSpeed":"NORMAL","teamsPerDivision":5}
                """.formatted(leagueId, teamId))
            .exchange()
            .expectStatus().isCreated();
    }

    private List<Map<String, Object>> squad(UUID userId) {
        List<Map<String, Object>> squad = webTestClient.mutateWith(mockUser(userId.toString()))
            .get().uri("/api/v1/career/teams/me/squad")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .returnResult()
            .getResponseBody();
        assertThat(squad).isNotNull();
        return squad;
    }

    private void autoSelect(UUID userId) {
        webTestClient.mutateWith(mockUser(userId.toString()))
            .post().uri("/api/v1/career/lineup/auto-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-4-2\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody(LineupDTO.class)
            .value(lineup -> assertThat(lineup.players()).hasSizeGreaterThanOrEqualTo(11));
    }

    private void assertGeneratedTraitCoverage() {
        Long invalid = databaseClient.sql("""
                SELECT COUNT(*) AS c FROM (
                    SELECT p.id
                    FROM players p
                    LEFT JOIN player_special_attributes psa ON psa.player_id = p.id
                    WHERE p.source_system = 'manager-mvp1-generated'
                    GROUP BY p.id
                    HAVING COUNT(psa.id) <> 2
                ) invalid
                """)
            .map(row -> row.get("c", Long.class))
            .one()
            .block();
        assertThat(invalid).isZero();
    }

    private void deleteGeneratedDatasetRows() {
        jdbcTemplate.update("""
            DELETE FROM player_special_attributes
            WHERE player_id IN (SELECT id FROM players WHERE source_system = 'manager-mvp1-generated')
            """);
        jdbcTemplate.update("""
            DELETE FROM player_secondary_positions
            WHERE player_id IN (SELECT id FROM players WHERE source_system = 'manager-mvp1-generated')
            """);
        jdbcTemplate.update("""
            DELETE FROM team_squad
            WHERE team_id IN (
                SELECT t.id FROM teams t
                JOIN clubs c ON c.id = t.club_id
                WHERE c.source_system = 'manager-mvp1-generated'
            )
               OR player_id IN (SELECT id FROM players WHERE source_system = 'manager-mvp1-generated')
            """);
        jdbcTemplate.update("""
            DELETE FROM league_teams
            WHERE team_id IN (
                SELECT t.id FROM teams t
                JOIN clubs c ON c.id = t.club_id
                WHERE c.source_system = 'manager-mvp1-generated'
            )
            """);
        jdbcTemplate.update("""
            DELETE FROM club_division_memberships
            WHERE club_id IN (SELECT id FROM clubs WHERE source_system = 'manager-mvp1-generated')
            """);
        jdbcTemplate.update("""
            DELETE FROM season_competitions
            WHERE league_id IN (SELECT id FROM leagues WHERE code IN ('ESP-PRIMERA', 'ARG-PRIMERA', 'BRA-SERIE-A'))
            """);
        jdbcTemplate.update("""
            UPDATE leagues SET season_id = NULL
            WHERE code IN ('ESP-PRIMERA', 'ARG-PRIMERA', 'BRA-SERIE-A')
            """);
        jdbcTemplate.update("""
            DELETE FROM seasons
            WHERE league_id IN (SELECT id FROM leagues WHERE code IN ('ESP-PRIMERA', 'ARG-PRIMERA', 'BRA-SERIE-A'))
            """);
        jdbcTemplate.update("""
            DELETE FROM teams
            WHERE club_id IN (SELECT id FROM clubs WHERE source_system = 'manager-mvp1-generated')
            """);
        jdbcTemplate.update("DELETE FROM players WHERE source_system = 'manager-mvp1-generated'");
        jdbcTemplate.update("DELETE FROM clubs WHERE source_system = 'manager-mvp1-generated'");
        jdbcTemplate.update("""
            DELETE FROM divisions
            WHERE league_id IN (SELECT id FROM leagues WHERE code IN ('ESP-PRIMERA', 'ARG-PRIMERA', 'BRA-SERIE-A'))
            """);
        jdbcTemplate.update("DELETE FROM leagues WHERE code IN ('ESP-PRIMERA', 'ARG-PRIMERA', 'BRA-SERIE-A')");
    }

    private long countGenerated(String table) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE source_system = 'manager-mvp1-generated'",
            Long.class);
        return count == null ? 0 : count;
    }

    private long countGeneratedTeams() {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM teams t
            JOIN clubs c ON c.id = t.club_id
            WHERE c.source_system = 'manager-mvp1-generated'
            """, Long.class);
        return count == null ? 0 : count;
    }

    private static UUID deterministicUuid(String key) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            bytes[6] &= 0x0f;
            bytes[6] |= 0x40;
            bytes[8] &= 0x3f;
            bytes[8] |= (byte) 0x80;
            return UUID.nameUUIDFromBytes(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build deterministic UUID", e);
        }
    }

    private record LeagueCase(String name, String country, int expectedTeams) {}
}
