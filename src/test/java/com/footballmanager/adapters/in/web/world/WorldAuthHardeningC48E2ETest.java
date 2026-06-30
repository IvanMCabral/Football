package com.footballmanager.adapters.in.web.world;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
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
 * V25D78-C48 — E2E coverage for SecurityConfig hardening of /api/v1/world/**.
 *
 * <p><b>Scope:</b> 5 tests verifying:
 * <ol>
 *   <li>Anonymous → POST /world/seed-la-liga → 401 (SecurityConfig reject)</li>
 *   <li>Authenticated user A → POST /world/seed-la-liga?userId=B → 403 (C47 impersonation, regression guard)</li>
 *   <li>Admin user (role=ADMIN) → POST /admin/world/seed-la-liga?userId=B → 200 OK (admin escape hatch)</li>
 *   <li>Anonymous → DELETE /world/snapshot → 401 (SecurityConfig reject)</li>
 *   <li>Authenticated user A → DELETE /world/snapshot → 200 OK own, 403 other (C48 + DELETE impersonation)</li>
 * </ol>
 *
 * <p><b>Testing strategy for ADMIN role:</b> WebFlux Spring Security Test
 * (6.2.1) doesn't provide an {@code authentication()} configurer — that configurer
 * is only available for the servlet (MockMvc) side. To test the admin endpoint,
 * we generate a REAL JWT via {@link JwtTokenProvider} (with role=ADMIN) and pass
 * it as a Bearer token in the Authorization header. The production SecurityConfig
 * JWT filter parses it and creates an Authentication with ROLE_ADMIN authority.
 * This exercises the real auth path end-to-end.
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
@DisplayName("V25D78-C48 — /world/** auth hardening + admin endpoint")
class WorldAuthHardeningC48E2ETest extends AbstractIntegrationTest {

    private static final UUID SEED_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID OTHER_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    private static final UUID ADMIN_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
    }

    /** Helper: configure WebTestClient with a Bearer token generated for the given user + role. */
    private WebTestClient withJwt(String userId, String role) {
        String token = jwtTokenProvider.generateToken(userId, role);
        return webTestClient.mutate()
            .defaultHeader("Authorization", "Bearer " + token)
            .build();
    }

    @Test
    @DisplayName("V25D78-C48 Test #1: Anonymous → POST /world/seed-la-liga → 401 (SecurityConfig reject)")
    void anonymous_seedLaLiga_returns401() {
        // SecurityConfig.java:144 changed from permitAll to authenticated. The JWT
        // filter rejects the request before reaching the controller.
        webTestClient.post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed-la-liga")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("V25D78-C48 Test #2: Authenticated user A → POST /world/seed-la-liga?userId=B "
        + "→ 403 IMPERSONATION_FORBIDDEN (C47 regression guard — auth check must still run after SecurityConfig change)")
    void authenticatedImpostor_seedLaLiga_returns403() {
        // C47 contract regression guard: changing SecurityConfig must NOT remove the
        // impersonation check added by C47. Authenticated + mismatched userId must
        // still get 403.
        withJwt(SEED_USER_ID.toString(), "USER")
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/seed-la-liga")
                .queryParam("userId", OTHER_USER_ID)
                .build())
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo("IMPERSONATION_FORBIDDEN")
            .jsonPath("$.status").isEqualTo(403);
    }

    @Test
    @DisplayName("V25D78-C48 Test #3: Admin user (role=ADMIN) → POST /admin/world/seed-la-liga?userId=B → 200 OK "
        + "(admin escape hatch — can seed any user)")
    void admin_seedLaLigaForOtherUser_returns200() {
        // AdminWorldController.adminSeedLaLiga is gated by @PreAuthorize("hasRole('ADMIN')")
        // and does NOT enforce the impersonation check (admin can seed any user by design).
        withJwt(ADMIN_USER_ID.toString(), "ADMIN")
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/admin/world/seed-la-liga")
                .queryParam("userId", OTHER_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok")
            .jsonPath("$.userId").isEqualTo(OTHER_USER_ID.toString())
            .jsonPath("$.adminEndpoint").isEqualTo(true);

        // Verify Redis world:{OTHER_USER_ID} now has data
        String key = "world:" + OTHER_USER_ID;
        Long bytes = redisTemplate.opsForValue().size(key).block();
        org.assertj.core.api.Assertions.assertThat(bytes)
            .as("V25D78-C48 admin endpoint MUST persist world:{userId} to Redis")
            .isNotNull()
            .isGreaterThan(0L);
    }

    @Test
    @DisplayName("V25D78-C48 Test #4: Anonymous → DELETE /world/snapshot → 401 (SecurityConfig reject)")
    void anonymous_deleteSnapshot_returns401() {
        webTestClient.delete().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/snapshot")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("V25D78-C48 Test #5: Authenticated user A → DELETE /world/snapshot → 200 OK own, 403 other")
    void authenticated_deleteSnapshot_ownReturns200_otherReturns403() {
        // OWN snapshot: same UUID in JWT and query param → 200 OK (regenerated)
        withJwt(SEED_USER_ID.toString(), "USER")
            .delete().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/snapshot")
                .queryParam("userId", SEED_USER_ID)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("regenerated");

        // OTHER user's snapshot: mismatched UUID → 403 IMPERSONATION_FORBIDDEN
        withJwt(SEED_USER_ID.toString(), "USER")
            .delete().uri(uriBuilder -> uriBuilder
                .path("/api/v1/world/snapshot")
                .queryParam("userId", OTHER_USER_ID)
                .build())
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo("IMPERSONATION_FORBIDDEN")
            .jsonPath("$.status").isEqualTo(403);
    }

    @Test
    @DisplayName("V25D78-C48 Bonus: Non-admin user → /admin/world/seed-la-liga → 403 "
        + "(@PreAuthorize rejects non-ADMIN role at method level)")
    void nonAdmin_adminEndpoint_returns403() {
        // SecurityConfig allows /api/v1/admin/world/** to authenticated users, but
        // @PreAuthorize on the method rejects non-ADMIN with 403.
        withJwt(SEED_USER_ID.toString(), "USER")
            .post().uri(uriBuilder -> uriBuilder
                .path("/api/v1/admin/world/seed-la-liga")
                .queryParam("userId", OTHER_USER_ID)
                .build())
            .exchange()
            .expectStatus().isForbidden();
    }
}