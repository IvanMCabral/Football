package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupWarningDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.PlayerLineupDTO;
import com.footballmanager.application.exception.NotEnoughPlayersException;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.lineup.LineupRules;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.port.in.lineup.LineupCommandUseCase;
import com.footballmanager.domain.port.in.lineup.LineupQueryUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * V24D7 FASE B — E2E HTTP coverage for {@link LineupController}.
 *
 * <p>Replaces the {@code @Disabled} placeholder from V24D6T.
 *
 * <p>Strategy: {@code @SpringBootTest} with the isolated test profile
 * (DB {@code football_manager_test}, Redis DB 15, Flyway off, RANDOM_PORT)
 * + {@code @MockBean} for the controller's collaborators. The full Spring
 * context boots so {@code SecurityConfig} (which marks
 * {@code /api/v1/career/**} as {@code permitAll}) actually runs, and the
 * global exception handler maps errors to 422 as in production.
 *
 * <p>For each test we stub the use case or {@code CareerSessionService}
 * (which carries the {@code CareerPhase} guard) and assert on the HTTP
 * status code and the JSON body shape that the UI consumes.
 *
 * <p>The test deliberately does NOT use {@code @Nested} classes: with
 * {@code @SpringBootTest} and a single {@code WebTestClient} bean, inner
 * classes have caused the {@code webHttpHandlerBuilder} to be null
 * (NPE on mutateWith). Flattening the tests avoids that issue.
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
@DisplayName("LineupController — E2E HTTP coverage")
class LineupControllerE2ETest {

    private static final UUID TEST_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @MockBean
    private LineupCommandUseCase lineupCommandUseCase;

    @MockBean
    private LineupQueryUseCase lineupQueryUseCase;

    @MockBean
    private CareerSessionService careerSessionService;

    @BeforeEach
    void cleanRedis() {
        reactiveRedisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands()
            .flushDb()
            .block();
    }

    // ---------- helpers ----------

    private CareerSave careerInPhase(CareerPhase phase) {
        CareerSave career = new CareerSave();
        TournamentState ts = new TournamentState();
        ts.setCareerPhase(phase);
        career.setTournamentState(ts);
        return career;
    }

    private void stubCareerInPhase(CareerPhase phase) {
        when(careerSessionService.getCareerFromCache(eq(TEST_USER_ID)))
            .thenReturn(Mono.just(careerInPhase(phase)));
    }

    private LineupDTO lineupWith11Players() {
        List<PlayerLineupDTO> players = List.of(
            new PlayerLineupDTO("p1", "GK", "GK", 80, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p2", "LB", "LB", 75, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p3", "CB1", "CB", 78, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p4", "CB2", "CB", 78, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p5", "RB", "RB", 75, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p6", "CM1", "CM", 76, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p7", "CM2", "CM", 76, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p8", "LW", "LW", 77, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p9", "ST1", "ST", 82, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p10", "RW", "RW", 77, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p11", "ST2", "ST", 80, 100, false, 25, 0, 0, false, 0)
        );
        return new LineupDTO("4-4-2", players, false, List.of());
    }

    private LineupDTO lineupShortHanded(int available) {
        List<PlayerLineupDTO> players = new ArrayList<>(List.of(
            new PlayerLineupDTO("p1", "GK", "GK", 80, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p2", "LB", "LB", 75, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p3", "CB1", "CB", 78, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p4", "CB2", "CB", 78, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p5", "RB", "RB", 75, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p6", "CM1", "CM", 76, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p7", "CM2", "CM", 76, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p8", "LW", "LW", 77, 100, false, 25, 0, 0, false, 0),
            new PlayerLineupDTO("p9", "ST1", "ST", 82, 100, false, 25, 0, 0, false, 0)
        ));
        while (players.size() > available) {
            players.remove(players.size() - 1);
        }
        return new LineupDTO("4-4-1-1", players, false,
            List.of(LineupWarningDTO.shortHanded(available)));
    }

    // ---------- POST /auto-select ----------

    @Test
    @DisplayName("POST /auto-select — 200 OK when phase=PRE_MATCH and use case returns a valid lineup")
    void autoSelect_happyPath() {
        stubCareerInPhase(CareerPhase.PRE_MATCH);
        when(lineupCommandUseCase.autoSelectLineup(eq(TEST_USER_ID), eq("4-4-2")))
            .thenReturn(Mono.just(lineupWith11Players()));

        webTestClient.mutateWith(
                org.springframework.security.test.web.reactive.server
                    .SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/auto-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-4-2\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.formation").isEqualTo("4-4-2")
            .jsonPath("$.players.length()").isEqualTo(11)
            .jsonPath("$.confirmed").isEqualTo(false)
            .jsonPath("$.warnings.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("POST /auto-select — 200 OK with short-handed warning when fewer than 11 players")
    void autoSelect_shortHanded_returnsWarning() {
        stubCareerInPhase(CareerPhase.PRE_MATCH);
        when(lineupCommandUseCase.autoSelectLineup(eq(TEST_USER_ID), eq("4-4-1-1")))
            .thenReturn(Mono.just(lineupShortHanded(LineupRules.MIN_AVAILABLE_PLAYERS)));

        webTestClient.mutateWith(
                org.springframework.security.test.web.reactive.server
                    .SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/auto-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-4-1-1\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.formation").isEqualTo("4-4-1-1")
            .jsonPath("$.players.length()").isEqualTo(LineupRules.MIN_AVAILABLE_PLAYERS)
            .jsonPath("$.warnings[0].code").isEqualTo(LineupWarningDTO.CODE_SHORT_HANDED);
    }

    @Test
    @DisplayName("POST /auto-select — 422 with LINEUP_STATE_ERROR when phase is not PRE_MATCH")
    void autoSelect_wrongPhase_returns422() {
        stubCareerInPhase(CareerPhase.IN_MATCH);

        webTestClient.mutateWith(
                org.springframework.security.test.web.reactive.server
                    .SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/auto-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-4-2\"}")
            .exchange()
            .expectStatus().isEqualTo(422)
            .expectBody()
            .jsonPath("$.code").isEqualTo("LINEUP_STATE_ERROR");
    }

    @Test
    @DisplayName("POST /auto-select — 422 with LINEUP_VALIDATION_ERROR when formation is blank")
    void autoSelect_blankFormation_returns422() {
        webTestClient.mutateWith(
                org.springframework.security.test.web.reactive.server
                    .SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/auto-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"\"}")
            .exchange()
            .expectStatus().isEqualTo(422)
            .expectBody()
            .jsonPath("$.code").isEqualTo("LINEUP_VALIDATION_ERROR");
    }

    @Test
    @DisplayName("POST /auto-select — 422 with LINEUP_MINIMUM_PLAYERS_NOT_MET on NotEnoughPlayers")
    void autoSelect_notEnoughPlayers_returns422() {
        stubCareerInPhase(CareerPhase.PRE_MATCH);
        when(lineupCommandUseCase.autoSelectLineup(eq(TEST_USER_ID), eq("4-4-2")))
            .thenReturn(Mono.error(new NotEnoughPlayersException("Only 6 available")));

        webTestClient.mutateWith(
                org.springframework.security.test.web.reactive.server
                    .SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/auto-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-4-2\"}")
            .exchange()
            .expectStatus().isEqualTo(422)
            .expectBody()
            .jsonPath("$.code").isEqualTo("LINEUP_MINIMUM_PLAYERS_NOT_MET")
            .jsonPath("$.minimumRequired").isEqualTo(LineupRules.MIN_AVAILABLE_PLAYERS);
    }

    // ---------- POST /manual-select ----------

    @Test
    @DisplayName("POST /manual-select — 200 OK with 11 valid player IDs and phase=PRE_MATCH")
    void manualSelect_happyPath() {
        stubCareerInPhase(CareerPhase.PRE_MATCH);
        List<String> playerIds = List.of("p1","p2","p3","p4","p5","p6","p7","p8","p9","p10","p11");
        when(lineupCommandUseCase.manualSelectLineup(eq(TEST_USER_ID), eq("4-4-2"), eq(playerIds)))
            .thenReturn(Mono.just(lineupWith11Players()));

        webTestClient.mutateWith(
                org.springframework.security.test.web.reactive.server
                    .SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/manual-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-4-2\",\"playerIds\":[\"p1\",\"p2\",\"p3\",\"p4\",\"p5\",\"p6\",\"p7\",\"p8\",\"p9\",\"p10\",\"p11\"]}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.players.length()").isEqualTo(11);
    }

    @Test
    @DisplayName("POST /manual-select — 422 when playerIds size != 11")
    void manualSelect_wrongSize_returns422() {
        webTestClient.mutateWith(
                org.springframework.security.test.web.reactive.server
                    .SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/manual-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-4-2\",\"playerIds\":[\"p1\",\"p2\"]}")
            .exchange()
            .expectStatus().isEqualTo(422)
            .expectBody()
            .jsonPath("$.code").isEqualTo("LINEUP_VALIDATION_ERROR");
    }

    @Test
    @DisplayName("POST /manual-select — 422 when playerIds is empty")
    void manualSelect_emptyList_returns422() {
        webTestClient.mutateWith(
                org.springframework.security.test.web.reactive.server
                    .SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/manual-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-4-2\",\"playerIds\":[]}")
            .exchange()
            .expectStatus().isEqualTo(422)
            .expectBody()
            .jsonPath("$.code").isEqualTo("LINEUP_VALIDATION_ERROR");
    }

    // ---------- POST /confirm ----------

    @Test
    @DisplayName("POST /confirm — 200 OK when phase=PRE_MATCH and use case completes")
    void confirm_happyPath() {
        stubCareerInPhase(CareerPhase.PRE_MATCH);
        when(lineupCommandUseCase.confirmLineup(eq(TEST_USER_ID)))
            .thenReturn(Mono.empty());

        webTestClient.mutateWith(
                org.springframework.security.test.web.reactive.server
                    .SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/confirm")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("POST /confirm — 422 with LINEUP_STATE_ERROR when phase is IN_MATCH")
    void confirm_wrongPhase_returns422() {
        stubCareerInPhase(CareerPhase.IN_MATCH);

        webTestClient.mutateWith(
                org.springframework.security.test.web.reactive.server
                    .SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()))
            .post().uri("/api/v1/career/lineup/confirm")
            .exchange()
            .expectStatus().isEqualTo(422)
            .expectBody()
            .jsonPath("$.code").isEqualTo("LINEUP_STATE_ERROR");
    }

    // ---------- GET /current ----------

    @Test
    @DisplayName("GET /current — 200 OK with empty lineup when none has been saved")
    void getCurrent_emptyLineup() {
        LineupDTO empty = new LineupDTO("4-3-3", List.of(), false);
        when(lineupQueryUseCase.getCurrentLineup(eq(TEST_USER_ID)))
            .thenReturn(Mono.just(empty));

        webTestClient.mutateWith(
                org.springframework.security.test.web.reactive.server
                    .SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()))
            .get().uri("/api/v1/career/lineup/current")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.formation").isEqualTo("4-3-3")
            .jsonPath("$.players.length()").isEqualTo(0)
            .jsonPath("$.confirmed").isEqualTo(false);
    }

    @Test
    @DisplayName("GET /current — 200 OK with full 11-player lineup")
    void getCurrent_fullLineup() {
        when(lineupQueryUseCase.getCurrentLineup(eq(TEST_USER_ID)))
            .thenReturn(Mono.just(lineupWith11Players()));

        webTestClient.mutateWith(
                org.springframework.security.test.web.reactive.server
                    .SecurityMockServerConfigurers.mockUser(TEST_USER_ID.toString()))
            .get().uri("/api/v1/career/lineup/current")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.players.length()").isEqualTo(11);
    }
}
