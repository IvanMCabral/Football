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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V25D78-C55.6 — Regression test for the {@code division} field exposure on
 * {@code WorldTeam} domain entity via {@code GET /api/v1/world/teams}.
 *
 * <p>Background: Bug MEDIUM found by REVISOR smoke C55.5 (runtime report
 * {@code reporte-C55.5-runtime-smoke.md} lines 168-181, 349-359). The
 * {@code teams.division} Postgres column was populated by V25D80 migration
 * (20 PRIMERA + 20 SEGUNDA + 20 TERCERA per league), but the
 * {@link com.footballmanager.domain.model.entity.WorldTeam} domain entity
 * had no {@code division} field — so the division tier was invisible to
 * the frontend.
 *
 * <p>This test exercises end-to-end:
 * <ol>
 *   <li>Seed LaLiga via {@code POST /world/seed-la-liga} (creates
 *       {@code WorldTeam} instances with {@code division =
 *       Division.defaultDivision()} = PRIMERA).</li>
 *   <li>GET {@code /api/v1/world/teams?userId=...} (the bug surface).</li>
 *   <li>Asserts that every team in the response carries a {@code division}
 *       field with a value in the canonical set
 *       {PRIMERA, SEGUNDA, TERCERA}.</li>
 * </ol>
 *
 * <p>The distribution 20/20/20 is enforced by V25D80 migration in dev
 * (Flyway enabled), but Flyway is disabled in tests, so this test only
 * verifies the field is present and well-formed — distribution is
 * exercised by C55.2 phase-4 tests once that UI lands.
 *
 * <p><b>Pre-req for:</b> C55.2 phase 4 (division preview dropdown,
 * standings por división, promotion/relegation views).
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
@DisplayName("WorldTeam — V25D78-C55.6 division field exposure (regression)")
class WorldTeamDivisionFieldE2ETest extends AbstractIntegrationTest {

    private static final UUID SEED_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seedLaLiga() {
        // V25D78-C55.5: ensure per-test isolation (AbstractIntegrationTest.@BeforeEach
        // already cleans Redis + Postgres world tables). Seed creates fresh
        // WorldTeam instances with division = Division.defaultDivision()
        // (PRIMERA, set by C55.6 createTeamFromDto).
        seedLaLigaForUser(SEED_USER_ID);
    }

    @Test
    @DisplayName("GET /world/teams?userId=... — every team has division ∈ {PRIMERA, SEGUNDA, TERCERA}")
    void getAllTeams_exposesDivisionFieldOnEveryTeam() {
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
        assertThat(teams.size()).as("seeded LaLiga should expose > 0 teams").isGreaterThan(0);

        int withDivision = 0;
        int validDivision = 0;
        for (JsonNode team : teams) {
            JsonNode divisionNode = team.get("division");
            assertThat(divisionNode).as(
                "team %s must expose `division` field (BUG_WORLDTEAM_NO_DIVISION_FIELD)",
                team.get("name").asText())
                .isNotNull();
            assertThat(divisionNode.isNull())
                .as("`division` field for team %s must NOT be null", team.get("name").asText())
                .isFalse();
            withDivision++;
            String div = divisionNode.asText();
            if ("PRIMERA".equals(div) || "SEGUNDA".equals(div) || "TERCERA".equals(div)) {
                validDivision++;
            } else {
                // Soft warning: log unexpected values but don't fail so we can
                // catch regression on unknown enum extensions
                System.err.printf(
                    "Unexpected division value on team %s: '%s'%n",
                    team.get("name").asText(), div);
            }
        }

        assertThat(withDivision).as("every team must have division field").isEqualTo(teams.size());
        assertThat(validDivision).as(
            "every team's division must be in {PRIMERA, SEGUNDA, TERCERA} — got %d valid of %d",
            validDivision, teams.size())
            .isEqualTo(teams.size());
    }

    @Test
    @DisplayName("GET /world/leagues/{id}/teams?userId=... — same division-field contract per-league")
    void getTeamsByLeague_exposesDivisionFieldOnEveryTeam() {
        final UUID laligaId = UUID.fromString("4feeb9df-4133-4655-883e-e96894907e7b");

        JsonNode teams = webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/leagues/{leagueId}/teams")
                .queryParam("userId", SEED_USER_ID)
                .build(laligaId))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();

        assertThat(teams).as("league teams response must be a non-empty array").isNotNull();
        assertThat(teams.isArray()).as("league teams response must be a JSON array").isTrue();
        assertThat(teams.size()).as("LaLiga should have > 0 teams").isGreaterThan(0);

        for (JsonNode team : teams) {
            String div = team.get("division").asText();
            assertThat(div).as(
                "team %s in LaLiga must have non-empty division (BUG_WORLDTEAM_NO_DIVISION_FIELD)",
                team.get("name").asText())
                .isIn("PRIMERA", "SEGUNDA", "TERCERA");
        }
    }
}
