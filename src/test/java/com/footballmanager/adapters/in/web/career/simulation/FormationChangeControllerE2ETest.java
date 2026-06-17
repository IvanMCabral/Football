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
 * LIVE-MATCH-F2-LIVE F5 (B7): E2E HTTP coverage for {@link FormationChangeController}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>POST without auth → 401 UNAUTHORIZED.</li>
 *   <li>POST with invalid UUID in path → 400 BAD_REQUEST.</li>
 *   <li>POST with 12-player formation → 400 BAD_REQUEST (size validation).</li>
 *   <li>POST with no GK in formation → 400 BAD_REQUEST (GK validation).</li>
 *   <li>POST with auth + valid matchId but no live session → 409 CONFLICT.</li>
 *   <li>POST happy path with registered MatchSession + V24LiveSession fixture
 *       and a valid 4-4-2 formation → 200 OK with success=true.</li>
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
@DisplayName("FormationChangeController — E2E HTTP coverage (LIVE-MATCH-F2-F5)")
class FormationChangeControllerE2ETest extends AbstractIntegrationTest {

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
    @DisplayName("POST without auth — 401 UNAUTHORIZED")
    void changeFormation_unauthenticated_returns401() {
        String matchId = UUID.randomUUID().toString();
        String body = """
            {"players":[
              {"playerId":"p1","position":"GK"}
            ]}
            """;

        webTestClient.post().uri("/api/v1/match-engine/matches/{id}/formation", matchId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("POST with invalid matchId UUID — 400 BAD_REQUEST")
    void changeFormation_invalidUuid_returns400() {
        String userId = UUID.randomUUID().toString();
        String body = """
            {"players":[{"playerId":"p1","position":"GK"}]}
            """;

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/matches/{id}/formation", "not-a-uuid")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("matchId must be a valid UUID"));
    }

    @Test
    @DisplayName("POST with 12 players — 400 BAD_REQUEST (size validation)")
    void changeFormation_tooManyPlayers_returns400() {
        String userId = UUID.randomUUID().toString();
        String matchId = UUID.randomUUID().toString();
        StringBuilder body = new StringBuilder("{\"players\":[");
        for (int i = 0; i < 12; i++) {
            if (i > 0) body.append(",");
            body.append("{\"playerId\":\"p").append(i).append("\",\"position\":\"GK\"}");
        }
        body.append("]}");

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/matches/{id}/formation", matchId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body.toString())
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("between 10 and 11"));
    }

    @Test
    @DisplayName("POST with no GK — 400 BAD_REQUEST (GK validation)")
    void changeFormation_noGoalkeeper_returns400() {
        String userId = UUID.randomUUID().toString();
        String matchId = UUID.randomUUID().toString();
        StringBuilder body = new StringBuilder("{\"players\":[");
        for (int i = 0; i < 10; i++) {
            if (i > 0) body.append(",");
            body.append("{\"playerId\":\"p").append(i).append("\",\"position\":\"DEF\"}");
        }
        body.append("]}");

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/matches/{id}/formation", matchId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body.toString())
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("exactly 1 GK"));
    }

    @Test
    @DisplayName("POST with auth + valid matchId but no live session — 409 CONFLICT")
    void changeFormation_noSession_returns409() {
        String userId = UUID.randomUUID().toString();
        String matchId = UUID.randomUUID().toString();
        String body = build442Body("p");

        webTestClient.mutateWith(mockUser(userId))
            .post().uri("/api/v1/match-engine/matches/{id}/formation", matchId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error").value(org.hamcrest.Matchers.containsString("No active match session"));
    }

    @Test
    @DisplayName("POST happy path with 4-4-2 formation — 200 OK with success=true")
    void changeFormation_happyPath_returns200() {
        // Arrange: build a V24LiveSession fixture with 11 home starters (home-p0..home-p10)
        // and register a MatchSession.
        String homeTeamId = "home";
        String awayTeamId = "away";
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID homeTeamUuid = UUID.randomUUID();
        UUID awayTeamUuid = UUID.randomUUID();

        V24MatchContext context = buildHappyPathContext(homeTeamId, awayTeamId);
        V24LiveSession liveSession = new V24LiveSession(context, 12345L);
        liveSession.tick();

        matchSessionRegistry.getOrCreateSessionWithV24(
            userId, matchId, homeTeamUuid, awayTeamUuid, liveSession);

        // 4-4-2 formation using the 11 home starters
        String body = build442Body(homeTeamId + "-p");

        // Act + Assert
        webTestClient.mutateWith(mockUser(userId.toString()))
            .post().uri("/api/v1/match-engine/matches/{id}/formation", matchId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.minuteApplied").isEqualTo(1)
            .jsonPath("$.currentFormation.length()").isEqualTo(11)
            .jsonPath("$.error").doesNotExist();
    }

    // ========== Fixture helpers ==========

    private String build442Body(String idPrefix) {
        StringBuilder body = new StringBuilder("{\"players\":[");
        String[] positions = {"GK", "DEF", "DEF", "DEF", "DEF", "MID", "MID", "MID", "MID", "ATT", "ATT"};
        for (int i = 0; i < positions.length; i++) {
            if (i > 0) body.append(",");
            body.append("{\"playerId\":\"").append(idPrefix).append(i)
                .append("\",\"position\":\"").append(positions[i]).append("\"}");
        }
        body.append("]}");
        return body.toString();
    }

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
            "match-formation-fp",
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
            String id = prefix + i;
            SessionPlayer sp = SessionPlayer.custom(id, 25, position,
                70, 70, 70, 70, 70, 70, BigDecimal.valueOf(70000L));
            sp.setSessionPlayerId(id);
            sp.setEnergy(100);
            players.add(sp);
        }
        return players;
    }
}
