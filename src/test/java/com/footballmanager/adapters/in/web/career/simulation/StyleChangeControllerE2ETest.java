package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * LIVE-MATCH-F2-LIVE F5 (B7): E2E HTTP coverage for {@link StyleChangeController}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>POST without auth → 401 UNAUTHORIZED (SecurityConfig rule).</li>
 *   <li>POST with invalid UUID in path → 400 BAD_REQUEST (controller-level validation).</li>
 *   <li>POST with null body → 400 BAD_REQUEST (controller-level validation).</li>
 *   <li>POST with null newStyle → 400 BAD_REQUEST (controller-level validation).</li>
 *   <li>POST with auth + valid matchId but no live session → 409 CONFLICT
 *       (TacticalChangeService throws IllegalStateException on missing session).</li>
 *   <li>POST happy path with registered MatchSession + V24LiveSession fixture
 *       → 200 OK with success=true and the requested style.</li>
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
@DisplayName("StyleChangeController — E2E HTTP coverage (LIVE-MATCH-F2-F5)")
class StyleChangeControllerE2ETest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MatchSessionRegistry matchSessionRegistry;

    @AfterEach
    void clearSessions() {
        if (matchSessionRegistry.getActiveSessionCount() > 0) {
            matchSessionRegistry.clearAllSessions();
        }
    }

    @Test
    @DisplayName("POST without auth — 401 UNAUTHORIZED (SecurityConfig rule)")
    void changeStyle_unauthenticated_returns401() {
        String matchId = UUID.randomUUID().toString();
        String body = """
            {"newStyle":"ATTACKING"}
            """;

        webTestClient.post().uri("/api/v1/match-engine/matches/{id}/style", matchId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("POST with invalid matchId UUID — 400 BAD_REQUEST (controller-level validation)")
    void changeStyle_invalidUuid_returns400() {
        String userId = UUID.randomUUID().toString();
        String body = """
            {"newStyle":"ATTACKING"}
            """;

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/matches/{id}/style", "not-a-uuid")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("matchId must be a valid UUID"));
    }

    @Test
    @DisplayName("POST with null newStyle — 400 BAD_REQUEST")
    void changeStyle_nullStyle_returns400() {
        String userId = UUID.randomUUID().toString();
        String matchId = UUID.randomUUID().toString();
        String body = """
            {"newStyle":null}
            """;

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/matches/{id}/style", matchId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("newStyle"));
    }

    @Test
    @DisplayName("POST with auth + valid matchId but no live session — 409 CONFLICT")
    void changeStyle_noSession_returns409() {
        String userId = UUID.randomUUID().toString();
        String matchId = UUID.randomUUID().toString();
        String body = """
            {"newStyle":"ATTACKING"}
            """;

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/matches/{id}/style", matchId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("No active match session"));
    }

    @Test
    @DisplayName("POST happy path with registered MatchSession+V24LiveSession — 200 OK with success=true")
    void changeStyle_happyPath_returns200WithNewStyle() {
        // Arrange: build a V24LiveSession fixture and register a MatchSession.
        String homeTeamId = "home";
        String awayTeamId = "away";
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID homeTeamUuid = UUID.randomUUID();
        UUID awayTeamUuid = UUID.randomUUID();

        V24MatchContext context = buildHappyPathContext(homeTeamId, awayTeamId);
        V24LiveSession liveSession = new V24LiveSession(context, 12345L);
        liveSession.tick(); // pre-simulate + advance to minute 1

        matchSessionRegistry.getOrCreateSessionWithV24(
            userId, matchId, homeTeamUuid, awayTeamUuid, liveSession);

        String body = """
            {"newStyle":"ATTACKING"}
            """;

        // Act + Assert: 200 OK with success=true, currentStyle=ATTACKING, minuteApplied=1
        webTestClient.mutateWith(mockUser(userId.toString()))
            .post().uri("/api/v1/match-engine/matches/{id}/style", matchId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.currentStyle").isEqualTo("ATTACKING")
            .jsonPath("$.minuteApplied").isEqualTo(1)
            .jsonPath("$.error").doesNotExist();
    }

    // ========== Fixture helpers ==========

    private V24MatchContext buildHappyPathContext(String homeTeamId, String awayTeamId) {
        SessionTeam homeTeam = SessionTeam.custom(homeTeamId, "Home FC FP", "ARG",
            BigDecimal.valueOf(1_000_000L), "4-3-3");
        homeTeam.setSessionTeamId(homeTeamId);
        SessionTeam awayTeam = SessionTeam.custom(awayTeamId, "Away FC FP", "BRA",
            BigDecimal.valueOf(1_000_000L), "4-4-2");
        awayTeam.setSessionTeamId(awayTeamId);

        List<SessionPlayer> homeStarting = makePlayers(homeTeamId, 11);
        List<SessionPlayer> homeBench = makePlayers(homeTeamId + "-bench", 5);
        List<SessionPlayer> awayStarting = makePlayers(awayTeamId, 11);
        List<SessionPlayer> awayBench = makePlayers(awayTeamId + "-bench", 5);

        return new V24MatchContext(
            "match-style-fp",
            homeTeamId,
            awayTeamId,
            homeTeam,
            awayTeam,
            homeStarting,
            awayStarting,
            homeBench,
            awayBench,
            "4-3-3",
            "4-4-2",
            TeamStyle.BALANCED,
            TeamStyle.BALANCED
        );
    }

    private List<SessionPlayer> makePlayers(String prefix, int count) {
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String position = (i == 0) ? "GK"
                : (i <= 4) ? "DEF"
                : (i <= 7) ? "MID"
                : (i <= 9) ? "WINGER" : "ATT";
            String id = prefix + "-p" + i;
            SessionPlayer sp = SessionPlayer.custom(id, 25, position,
                70, 70, 70, 70, 70, 70, BigDecimal.valueOf(70000L));
            sp.setSessionPlayerId(id);
            sp.setEnergy(100);
            players.add(sp);
        }
        return players;
    }
}
