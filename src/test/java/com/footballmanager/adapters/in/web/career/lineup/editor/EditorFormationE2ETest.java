package com.footballmanager.adapters.in.web.career.lineup.editor;

import com.footballmanager.application.service.editor.FieldSubdivisionService;
import com.footballmanager.application.service.editor.FormationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * MVP1-lineup-cancha-1: E2E HTTP coverage para los 2 endpoints nuevos
 * del editor de formación.
 *
 * <p>Estrategia: SpringBootTest + WebTestClient con profile "test"
 * (DB {@code football_manager_test}, Redis DB 15, Flyway off).
 * Los endpoints son estáticos (no tocan DB) así que no necesitan mocks.
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
@DisplayName("Editor formation — E2E HTTP coverage")
class EditorFormationE2ETest {

    private static final UUID TEST_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Autowired
    private FieldSubdivisionService fieldSubdivisionService;

    @Autowired
    private FormationService formationService;

    @org.junit.jupiter.api.BeforeEach
    void cleanRedis() {
        reactiveRedisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands()
            .flushDb()
            .block();
    }

    // ---------- GET /api/v1/editor/subdivisions ----------

    @Test
    @DisplayName("GET /editor/subdivisions — 200 OK con 82 subdivisiones")
    void getSubdivisions_returns200_with82Elements() {
        webTestClient.mutateWith(mockUser(TEST_USER_ID.toString()))
            .get().uri("/api/v1/editor/subdivisions")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(82)
            .jsonPath("$[0].isGoalkeeper").isEqualTo(true)
            .jsonPath("$[0].zone").isEqualTo("GK")
            .jsonPath("$[0].subdivisionId").isEqualTo("GK-1")
            .jsonPath("$[1].isGoalkeeper").isEqualTo(false);
    }

    @Test
    @DisplayName("GET /editor/subdivisions — primera subdivisión GK tiene coordenadas válidas")
    void getSubdivisions_goalkeeperHasValidCoords() {
        webTestClient.mutateWith(mockUser(TEST_USER_ID.toString()))
            .get().uri("/api/v1/editor/subdivisions")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].left").exists()
            .jsonPath("$[0].top").exists()
            .jsonPath("$[0].width").exists()
            .jsonPath("$[0].height").exists()
            .jsonPath("$[0].sector").isEqualTo(26);
    }

    // ---------- GET /api/v1/editor/formations ----------

    @Test
    @DisplayName("GET /editor/formations — 200 OK con 12 formaciones")
    void getFormations_returns200_with4Elements() {
        // V25D75-C40 A4: endpoint returns 12 formations (was 4 — set grew over sprints).
        // Asserting exact count was too strict; the next test verifies the
        // 4 named formations are still present.
        webTestClient.mutateWith(mockUser(TEST_USER_ID.toString()))
            .get().uri("/api/v1/editor/formations")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(12);
    }

    @Test
    @DisplayName("GET /editor/formations — contiene las 4 formaciones esperadas")
    void getFormations_containsExpectedNames() {
        webTestClient.mutateWith(mockUser(TEST_USER_ID.toString()))
            .get().uri("/api/v1/editor/formations")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.name == '4-4-2')]").exists()
            .jsonPath("$[?(@.name == '4-3-3')]").exists()
            .jsonPath("$[?(@.name == '3-5-2')]").exists()
            .jsonPath("$[?(@.name == '4-2-3-1')]").exists();
    }

    @Test
    @DisplayName("GET /editor/formations/4-4-2 — 200 OK con shape completo")
    void getFormationByName_happyPath() {
        webTestClient.mutateWith(mockUser(TEST_USER_ID.toString()))
            .get().uri("/api/v1/editor/formations/4-4-2")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("4-4-2")
            .jsonPath("$.defenders").isEqualTo(4)
            .jsonPath("$.midfielders").isEqualTo(4)
            .jsonPath("$.attackers").isEqualTo(2)
            .jsonPath("$.outfieldPlayers").isEqualTo(10)
            .jsonPath("$.positions.length()").isEqualTo(11)
            .jsonPath("$.positions[0].role").isEqualTo("GK")
            .jsonPath("$.positions[0].subdivisionId").isEqualTo("GK-1");
    }

    @Test
    @DisplayName("GET /editor/formations/{name} — 500/422 para nombre desconocido")
    void getFormationByName_unknown_returnsError() {
        webTestClient.mutateWith(mockUser(TEST_USER_ID.toString()))
            .get().uri("/api/v1/editor/formations/5-5-5")
            .exchange()
            .expectStatus().is4xxClientError();
    }
}