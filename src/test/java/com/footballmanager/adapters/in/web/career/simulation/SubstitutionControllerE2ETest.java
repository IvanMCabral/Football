package com.footballmanager.adapters.in.web.career.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * LIVE-MATCH-F1-POC: E2E coverage for {@link SubstitutionController}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>POST without auth → 401 UNAUTHORIZED (SecurityConfig rule).</li>
 *   <li>POST with invalid UUID in path → 400 BAD_REQUEST (request-level validation).</li>
 *   <li>POST with auth + valid matchId but no live session → 409 CONFLICT (IllegalStateException
 *       mapped by the controller).</li>
 * </ul>
 *
 * <p>Happy-path tests (valid session + valid substitution → 200 OK) require
 * integration setup of a {@code MatchSession} with a {@code V24LiveSession}
 * which is beyond the scope of this initial E2E. They are covered by:
 * <ol>
 *   <li>Unit tests in {@link com.footballmanager.application.service.simulation.v24.V24SubstitutionEngineTest}
 *       (manualSubstitute happy + fail cases).</li>
 *   <li>The D1=B regression test in {@link com.footballmanager.application.service.simulation.v24.V24LiveSessionTest}
 *       which proves substitutions do NOT alter the match result.</li>
 *   <li>End-to-end manual verification (criterion of done in the LIVE-MATCH-F1-POC prompt).</li>
 * </ol>
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
@DisplayName("SubstitutionController — E2E HTTP coverage (LIVE-MATCH-F1-POC)")
class SubstitutionControllerE2ETest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST without auth — 401 UNAUTHORIZED (SecurityConfig rule)")
    void substitute_unauthenticated_returns401() {
        String matchId = UUID.randomUUID().toString();
        String body = """
            {"playerOffId":"off","playerOnId":"on","minute":null}
            """;

        webTestClient.post().uri("/api/v1/match-engine/matches/{id}/substitutions", matchId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("POST with invalid matchId UUID — 400 BAD_REQUEST (controller-level validation)")
    void substitute_invalidUuid_returns400() {
        String userId = UUID.randomUUID().toString();
        // matchId is NOT a valid UUID
        String invalidMatchId = "not-a-uuid";
        String body = """
            {"playerOffId":"off","playerOnId":"on","minute":null}
            """;

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/matches/{id}/substitutions", invalidMatchId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("matchId must be a valid UUID"));
    }

    @Test
    @DisplayName("POST with auth + valid matchId but no live session — 409 CONFLICT")
    void substitute_noSession_returns409() {
        String userId = UUID.randomUUID().toString();
        String matchId = UUID.randomUUID().toString();
        String body = """
            {"playerOffId":"off","playerOnId":"on","minute":null}
            """;

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/matches/{id}/substitutions", matchId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("No active match session"));
    }

    @Test
    @DisplayName("POST with blank playerOffId — 400 BAD_REQUEST")
    void substitute_blankPlayerOffId_returns400() {
        String userId = UUID.randomUUID().toString();
        String matchId = UUID.randomUUID().toString();
        String body = """
            {"playerOffId":"","playerOnId":"on","minute":null}
            """;

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/matches/{id}/substitutions", matchId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("playerOffId"));
    }
}
