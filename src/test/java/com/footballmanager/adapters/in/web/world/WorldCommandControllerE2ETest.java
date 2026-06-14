package com.footballmanager.adapters.in.web.world;

import com.footballmanager.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V24D7 FASE C — E2E HTTP coverage for {@link WorldCommandController}.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test DB +
 * Redis DB 15. Exercises the {@code WorldSnapshotService.reloadFromDatabase}
 * path which rebuilds the WorldSnapshot from PostgreSQL data.
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
@DisplayName("WorldCommandController — E2E HTTP coverage")
class WorldCommandControllerE2ETest extends AbstractIntegrationTest {

    private static final UUID SEED_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @org.springframework.beans.factory.annotation.Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
    }

    @Test
    @DisplayName("DELETE /world/snapshot?userId=... — 200 with status=regenerated and counts")
    void deleteSnapshot_returns200WithCounts() {
        webTestClient.mutateWith(mockUser(SEED_USER_ID.toString()))
            .delete().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/snapshot")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("regenerated")
            .jsonPath("$.leagues").exists()
            .jsonPath("$.teams").exists()
            .jsonPath("$.players").exists();
    }
}
