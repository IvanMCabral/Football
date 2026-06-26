package com.footballmanager.adapters.in.web.testharness;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.port.in.testharness.TestHarnessUseCase;
import com.footballmanager.domain.port.in.testharness.TestHarnessUseCase.CustomFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V24D20-TESTHARNESS — E2E HTTP coverage for {@link TestHarnessController}.
 *
 * <p>Pattern mirrors {@code LineupControllerE2ETest}: {@code @SpringBootTest}
 * with the isolated test profile (DB {@code football_manager_test}, Redis DB 15,
 * Flyway off, RANDOM_PORT) + {@code @MockBean} for {@link TestHarnessUseCase}.
 * The full Spring context boots so {@code SecurityConfig} and the
 * profile-gated controller registration are exercised end-to-end.
 *
 * <p>The full integration flow (createCustom + replace-fixtures + simulate
 * 4 rounds + verify results differ) is what REVISOR runs as a smoke
 * (see Phase 8 runbook doc); unit tests cover the use case logic and
 * these tests cover HTTP wiring / auth / response shape.
 *
 * <p>Critical for regression guard: the BUG_FORMATION_PERSIST_IGNORED
 * (sprint 1.7) is asserted end-to-end at the use-case level in
 * {@code TestHarnessUseCaseImplTest.setFormation_persistsInBothSessionTeamAndFormationMap}.
 * This E2E verifies the controller hands the request through.
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
@DisplayName("TestHarnessController — E2E HTTP coverage")
class TestHarnessControllerE2ETest {

    private static final UUID TEST_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TestHarnessUseCase testHarnessUseCase;

    private WebTestClient authedClient() {
        return webTestClient.mutateWith(
            SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()));
    }

    // ========== POST /set-formation ==========

    @Test
    @DisplayName("POST /set-formation — 200 OK with the formation echoed in body")
    void setFormation_happyPath() {
        when(testHarnessUseCase.setFormation(eq(TEST_USER_ID), eq("3-5-2")))
            .thenReturn(Mono.empty());

        authedClient()
            .post().uri("/api/v1/test-harness/career/set-formation")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"3-5-2\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.formation").isEqualTo("3-5-2");
    }

    @Test
    @DisplayName("POST /set-formation — 401 when no auth (controller profile-gated to dev|local|test)")
    void setFormation_noAuth_returns401() {
        webTestClient
            .post().uri("/api/v1/test-harness/career/set-formation")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"3-5-2\"}")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    // ========== POST /replace-fixtures ==========

    @Test
    @DisplayName("POST /replace-fixtures — 200 OK with fixtureCount and currentRound=1 in body")
    void replaceFixtures_happyPath() {
        when(testHarnessUseCase.replaceFixtures(eq(TEST_USER_ID), any()))
            .thenReturn(Mono.empty());

        String body = """
            [
              {"homeTeamId":"team-A","awayTeamId":"team-B","round":1},
              {"homeTeamId":"team-A","awayTeamId":"team-C","round":2}
            ]
            """;

        authedClient()
            .post().uri("/api/v1/test-harness/career/replace-fixtures")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.fixtureCount").isEqualTo(2)
            .jsonPath("$.maxRound").isEqualTo(2)
            .jsonPath("$.currentRound").isEqualTo(1);

        // Verify the CustomFixture mapping produced the right domain objects.
        verify(testHarnessUseCase, times(1)).replaceFixtures(
            eq(TEST_USER_ID),
            org.mockito.ArgumentMatchers.argThat((List<CustomFixture> specs) ->
                specs != null && specs.size() == 2
                && "team-A".equals(specs.get(0).homeTeamId())
                && "team-B".equals(specs.get(0).awayTeamId())
                && specs.get(0).round() == 1
                && specs.get(0).matchId() == null  // omitted in body → null
            )
        );
    }

    @Test
    @DisplayName("POST /replace-fixtures — 401 when no auth")
    void replaceFixtures_noAuth_returns401() {
        webTestClient
            .post().uri("/api/v1/test-harness/career/replace-fixtures")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("[]")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    // ========== POST /reset-injuries ==========

    @Test
    @DisplayName("POST /reset-injuries — 200 OK with success body")
    void resetInjuries_happyPath() {
        when(testHarnessUseCase.resetInjuries(eq(TEST_USER_ID)))
            .thenReturn(Mono.empty());

        authedClient()
            .post().uri("/api/v1/test-harness/career/reset-injuries")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true);
    }

    // ========== GET /snapshot ==========

    @Test
    @DisplayName("GET /snapshot — 200 OK with squadHealthSummary computed from career")
    void snapshot_happyPath() {
        // Minimal career: 1 squad player, all flags cleared.
        CareerSave career = new CareerSave();
        career.setUserId(TEST_USER_ID);
        career.setUserSessionTeamId("user-team");
        // career.save() round-trip via Jackson is out-of-scope for this
        // E2E — we just need a non-null CareerSave for snapshot() to
        // return. The controller maps to CareerSnapshotResponse which
        // tolerates null fields.
        when(testHarnessUseCase.snapshot(eq(TEST_USER_ID)))
            .thenReturn(Mono.just(career));

        authedClient()
            .get().uri("/api/v1/test-harness/career/snapshot")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.userId").isEqualTo(TEST_USER_ID.toString())
            .jsonPath("$.userSessionTeamId").isEqualTo("user-team")
            .jsonPath("$.squadHealthSummary").exists();
    }

    // ========== POST /create-custom (validation) ==========

    @Test
    @DisplayName("POST /create-custom — 200 OK with careerId and totalRounds in body")
    void createCustom_happyPath() {
        CareerSave career = new CareerSave();
        career.setUserId(TEST_USER_ID);
        career.setUserSessionTeamId("user-team");
        career.getTournamentState().setTotalRounds(2);
        career.getTournamentState().setCurrentRound(1);

        when(testHarnessUseCase.createCustom(
                eq(TEST_USER_ID), anyString(), anyString(),
                anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(career));

        // V25D38-F2: use valid UUIDs for leagueId/teamId (production path
        // requires UUIDs — StartCareerUseCaseImpl.start() calls
        // UUID.fromString(worldLeagueId) which throws IllegalArgumentException
        // on malformed strings; the V25D38-F2 fix now pre-validates this in
        // the controller so the client gets a clean 400 instead of a 500 NPE
        // on null or 422 IAE on malformed strings).
        String body = """
            {
              "leagueId":"4feeb9df-4133-4655-883e-e96894907e7b",
              "teamId":"8e55b18e-051d-48bd-9763-a35ae3005ac0",
              "difficulty":"EASY",
              "gameSpeed":"NORMAL",
              "teamsPerDivision":2
            }
            """;

        authedClient()
            .post().uri("/api/v1/test-harness/career/create-custom")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.teamsPerDivision").isEqualTo(2)
            .jsonPath("$.totalRounds").isEqualTo(2)
            .jsonPath("$.currentRound").isEqualTo(1);
    }

    @Test
    @DisplayName("POST /create-custom — default teamsPerDivision=5 when null (backward compat)")
    void createCustom_nullTeamsPerDivision_defaultsTo5() {
        CareerSave career = new CareerSave();
        career.setUserId(TEST_USER_ID);
        career.setUserSessionTeamId("user-team");

        when(testHarnessUseCase.createCustom(
                eq(TEST_USER_ID), anyString(), anyString(),
                anyString(), anyString(), eq(5)))
            .thenReturn(Mono.just(career));

        // V25D38-F2: use valid UUIDs (see createCustom_happyPath comment).
        String body = """
            {
              "leagueId":"4feeb9df-4133-4655-883e-e96894907e7b",
              "teamId":"8e55b18e-051d-48bd-9763-a35ae3005ac0",
              "difficulty":"EASY",
              "gameSpeed":"NORMAL"
            }
            """;

        authedClient()
            .post().uri("/api/v1/test-harness/career/create-custom")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.teamsPerDivision").isEqualTo(5);

        verify(testHarnessUseCase, times(1)).createCustom(
            eq(TEST_USER_ID), anyString(), anyString(),
            anyString(), anyString(), eq(5));
    }
}
