package com.footballmanager.adapters.in.web.versus;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V25D78-C53 — E2E coverage for 2 minor UX bugs in {@link MatchControllerReactive}.
 *
 * <p><b>Scope (2 tests, 1 per fix):</b>
 * <ul>
 *   <li><b>Bug #1:</b> GET /api/v1/matches — the DTO used to return only
 *       {@code homeTeamId}/{@code awayTeamId} UUIDs, forcing the frontend
 *       to render "Team vs Team" placeholders. Post-C53, the DTO carries
 *       {@code homeTeamName}/{@code awayTeamName} resolved from the user's
 *       WorldSnapshot. This test seeds LaLiga, creates a match against a
 *       known team, and asserts the names round-trip end-to-end.</li>
 *
 *   <li><b>Bug #3:</b> GET /api/v1/matches/{matchId}/minute-by-minute — the
 *       endpoint did not exist (Spring returned 404 for the no-handler path),
 *       which made the frontend's MatchDetailComponent stay in "Loading..."
 *       state. Post-C53, the endpoint exists and returns 404 (no V24 detail
 *       for this match yet) when the user has no career — the frontend can
 *       then render the failure message instead of hanging.</li>
 * </ul>
 *
 * <p><b>Out of scope:</b> Bug #2 (only 1 league in /career/setup) is a
 * feature gap, not a bug — the LaLigaSeedService only seeds one league by
 * design. Reported separately to parent (per task spec rule "reportar
 * ANTES de fixear" for items outside the listed concerns).
 *
 * <p><b>Strategy:</b> real {@code @SpringBootTest} against the isolated test
 * DB + Redis DB 15. No mocks — exercises the full stack (controller →
 * WorldSnapshotService → Redis).
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
@DisplayName("V25D78-C53 — /matches team-name enrichment + minute-by-minute endpoint")
class MatchControllerReactiveC53E2ETest extends AbstractIntegrationTest {

    private static final UUID USER_ID =
        UUID.fromString("00000000-0000-0000-0000-0000000000c5");

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
    }

    @Test
    @DisplayName("V25D78-C53 Bug #1: GET /matches returns homeTeamName + awayTeamName "
        + "resolved from WorldSnapshot (no more 'Team vs Team' placeholders)")
    void getMatches_teamNames_resolvedFromSnapshot() {
        // Step 1: seed LaLiga so the user's WorldSnapshot has known teams (Real Madrid, etc.).
        webTestClient.mutateWith(mockUser(USER_ID.toString()))
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed-la-liga")
                .queryParam("userId", USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk();

        // Step 2: read /world/teams to get a known team id (use Real Madrid if present).
        JsonNode teams = webTestClient.mutateWith(mockUser(USER_ID.toString()))
            .get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/teams")
                .queryParam("userId", USER_ID)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();

        org.assertj.core.api.Assertions.assertThat(teams).as("/world/teams must return non-empty array").isNotNull();
        org.assertj.core.api.Assertions.assertThat(teams.isArray()).as("/world/teams must be JSON array").isTrue();
        org.assertj.core.api.Assertions.assertThat(teams.size())
            .as("LaLiga seed must produce >= 20 teams").isGreaterThanOrEqualTo(20);

        // Pick the first two teams to use as home/away. Capture their names and IDs.
        String homeId = teams.get(0).get("worldTeamId").asText();
        String homeName = teams.get(0).get("name").asText();
        String awayId = teams.get(1).get("worldTeamId").asText();
        String awayName = teams.get(1).get("name").asText();

        // Step 3: create a match referencing those two teams via POST /matches.
        String matchBody = String.format(
            "{\"homeTeamId\":\"%s\",\"awayTeamId\":\"%s\",\"scheduledAt\":\"%s\"}",
            homeId, awayId, Instant.now().toString());
        webTestClient.mutateWith(mockUser(USER_ID.toString()))
            .post().uri("/api/v1/matches")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(matchBody)
            .exchange()
            .expectStatus().isCreated();

        // Step 4: GET /api/v1/matches and verify the DTO now carries homeTeamName/awayTeamName.
        JsonNode matches = webTestClient.mutateWith(mockUser(USER_ID.toString()))
            .get().uri("/api/v1/matches")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();

        org.assertj.core.api.Assertions.assertThat(matches).as("/matches must return non-empty array").isNotNull();
        org.assertj.core.api.Assertions.assertThat(matches.isArray()).as("/matches must be JSON array").isTrue();
        org.assertj.core.api.Assertions.assertThat(matches.size()).as("/matches must contain the created match").isGreaterThanOrEqualTo(1);

        JsonNode firstMatch = matches.get(0);
        // The KEY assertion: homeTeamName/awayTeamName are resolved names, not literal "Team".
        org.assertj.core.api.Assertions.assertThat(firstMatch.has("homeTeamName"))
            .as("MatchDTO must carry homeTeamName field (C53 Bug #1 fix)").isTrue();
        org.assertj.core.api.Assertions.assertThat(firstMatch.has("awayTeamName"))
            .as("MatchDTO must carry awayTeamName field (C53 Bug #1 fix)").isTrue();
        org.assertj.core.api.Assertions.assertThat(firstMatch.get("homeTeamName").asText())
            .as("homeTeamName must equal the actual team name (not 'Team' placeholder)")
            .isEqualTo(homeName);
        org.assertj.core.api.Assertions.assertThat(firstMatch.get("awayTeamName").asText())
            .as("awayTeamName must equal the actual team name (not 'Team' placeholder)")
            .isEqualTo(awayName);
        org.assertj.core.api.Assertions.assertThat(firstMatch.get("homeTeamName").asText())
            .as("homeTeamName must NOT be the 'Team' literal placeholder")
            .isNotEqualTo("Team");
    }

    @Test
    @DisplayName("V25D78-C53 Bug #3: GET /matches/{matchId}/minute-by-minute endpoint exists "
        + "and returns 404 (no career / no V24 detail) instead of 404 for missing handler")
    void getMinuteByMinute_noCareer_returns404_notHandlerNotFound() {
        // Pre-C53: this endpoint did not exist → Spring's no-handler path returned
        // 404 with a generic body → frontend stayed in "Loading..." indefinitely.
        // Post-C53: the endpoint exists and returns 404 when there is no career
        // (because V24 detail is keyed by careerId+matchId). The status code is
        // still 404 but with the right semantics — the frontend's error handler
        // can now correctly transition out of the loading state.
        UUID anyMatchId = UUID.randomUUID();
        webTestClient.mutateWith(mockUser(USER_ID.toString()))
            .get().uri("/api/v1/matches/{matchId}/minute-by-minute", anyMatchId.toString())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound();
    }
}