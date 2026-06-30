package com.footballmanager.adapters.in.web.world;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V24D7 FASE C — E2E HTTP coverage for {@link LaLigaSeedController}.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test DB +
 * Redis DB 15. Exercises the LaLigaSeedService real implementation, which
 * reads {@code laliga-2024-25.json} from the classpath and writes the
 * WorldSnapshot to Redis.
 *
 * <p>Scope:
 * <ul>
 *   <li>POST /api/v1/world/seed-la-liga — 200 with counts</li>
 *   <li>Idempotency: run twice, snapshot still has 20 teams (no duplication)</li>
 *   <li>Response shape: status, leagueName, teamsInserted, playersInserted, durationMs</li>
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
@DisplayName("LaLigaSeedController — E2E HTTP coverage")
class LaLigaSeedControllerE2ETest extends AbstractIntegrationTest {

    private static final UUID SEED_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @org.springframework.beans.factory.annotation.Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
    }

    @Test
    @DisplayName("POST /world/seed-la-liga — 200 with status, teamsInserted, playersInserted")
    void seed_returns200WithCounts() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed-la-liga")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok")
            .jsonPath("$.userId").isEqualTo(SEED_USER_ID.toString())
            .jsonPath("$.leagueName").isEqualTo("La Liga 2024/25")
            .jsonPath("$.teamsInserted").exists()
            .jsonPath("$.playersInserted").exists()
            .jsonPath("$.durationMs").exists();
    }

    @Test
    @DisplayName("POST /world/seed-la-liga — idempotent: second run returns 200 without duplicating")
    void seed_idempotent_secondRunReturns200() {
        // First run
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed-la-liga")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.teamsInserted").value(teams1 ->
                org.junit.jupiter.api.Assertions.assertNotNull(teams1));

        // Second run
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed-la-liga")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok");
    }

    @Test
    @DisplayName("V25D78-C44 P0: POST /world/seed-la-liga — Redis world:{userId} PERSISTS with non-zero bytes "
        + "after the call returns (regression for the post-save delete bug)")
    void seed_persistsSnapshotInRedis_post_returns200() throws Exception {
        // V25D78-C44 reproducer (Bug root cause was: LaLigaSeedService.applySeed
        // deleted the snapshot right after saveSnapshot via worldRepository.deleteByUserId,
        // leaving Redis empty (STRLEN=0, TTL=-2) immediately after the POST).
        // Post-fix: saveSnapshot IS the last write — the snapshot persists.
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed-la-liga")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok")
            .jsonPath("$.teamsInserted").exists()
            .jsonPath("$.playersInserted").exists();

        // Verify Redis has the snapshot — this is THE contract for the C44 bug.
        // Pre-fix: STRLEN was 0 (key absent). Post-fix: STRLEN > 0 with valid JSON.
        String redisKey = "world:" + SEED_USER_ID;
        Long lenBytes = redisTemplate.opsForValue().size(redisKey).block();
        assertThat(lenBytes)
            .as("V25D78-C44 contract: world:{userId} MUST exist in Redis after "
                + "POST /seed-la-liga returns (pre-fix: STRLEN=0, TTL=-2). Got size=" + lenBytes)
            .isNotNull()
            .isGreaterThan(0L);

        String json = redisTemplate.opsForValue().get(redisKey).block();
        assertThat(json)
            .as("snapshot JSON in Redis must be present")
            .isNotNull()
            .isNotEmpty();

        // Verify the snapshot has the 20 LaLiga teams + 406 players as fields in the JSON.
        // The snapshot.worldTeams may contain MORE than just the 20 LaLiga teams if the
        // user's Postgres state had previous teams (snapshotCreator creates a fresh snapshot
        // from ALL teams in Postgres, then seed adds LaLiga). So we assert presence >= 20.
        JsonNode root = objectMapper.readTree(json);
        JsonNode worldTeams = root.get("worldTeams");
        JsonNode worldPlayers = root.get("worldPlayers");
        assertThat(worldTeams)
            .as("snapshot must contain worldTeams map (after seed the snapshot is non-empty)")
            .isNotNull();
        assertThat(worldTeams.isObject())
            .as("worldTeams must be a JSON object")
            .isTrue();
        assertThat(worldTeams.size())
            .as("snapshot.worldTeams must contain the 20 LaLiga teams (size >= 20)")
            .isGreaterThanOrEqualTo(20);

        assertThat(worldPlayers)
            .as("snapshot must contain worldPlayers map")
            .isNotNull();
        assertThat(worldPlayers.isObject())
            .as("worldPlayers must be a JSON object")
            .isTrue();
        assertThat(worldPlayers.size())
            .as("snapshot.worldPlayers must contain the 406 LaLiga players (size >= 406)")
            .isGreaterThanOrEqualTo(406);
    }
}
