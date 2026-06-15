package com.footballmanager.adapters.in.web.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.footballmanager.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * V24D7 FASE B — E2E HTTP coverage for {@link AuthController}.
 *
 * <p>Strategy: real {@code @SpringBootTest} against the isolated test DB.
 * Tests the actual {@code AuthUseCase} implementation, not a mock.
 *
 * <p>Scope:
 * <ul>
 *   <li>Register a new user → 200 "User registered"</li>
 *   <li>Login with the new user → 200 with JWT tokens</li>
 *   <li>Login with wrong password → 400</li>
 *   <li>Register an already-registered email → 409</li>
 *   <li>GET /me without auth → 401</li>
 *   <li>GET /me with auth (mockUser) → 200 with user info</li>
 *   <li>Refresh token roundtrip → 200 with new tokens</li>
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
@DisplayName("AuthController — E2E HTTP coverage")
class AuthControllerE2ETest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getReactiveConnection()
            .serverCommands().flushDb().block();
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }

    @Test
    @DisplayName("POST /register — 200 with JWT tokens (auto-login post-register)")
    void register_newUser_returns200() {
        webTestClient.post().uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"email\":\"%s\",\"username\":\"u-%s\",\"password\":\"pass1234\"}",
                uniqueEmail(), UUID.randomUUID().toString().substring(0, 8)))
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .value(json -> {
                String access = json.get("accessToken").asText();
                String refresh = json.get("refreshToken").asText();
                String type = json.get("tokenType").asText();
                org.junit.jupiter.api.Assertions.assertNotNull(access);
                org.junit.jupiter.api.Assertions.assertNotNull(refresh);
                org.junit.jupiter.api.Assertions.assertEquals("Bearer", type);
                org.junit.jupiter.api.Assertions.assertFalse(access.isBlank());
                org.junit.jupiter.api.Assertions.assertFalse(refresh.isBlank());
            });
    }

    @Test
    @DisplayName("POST /register — 409 Conflict when email already exists")
    void register_duplicateEmail_returns409() {
        String email = uniqueEmail();
        String username = "u-" + UUID.randomUUID().toString().substring(0, 12);
        String body = String.format(
            "{\"email\":\"%s\",\"username\":\"%s\",\"password\":\"pass1234\"}",
            email, username);

        // First registration: 200
        webTestClient.post().uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();

        // Second registration with same email: 409
        webTestClient.post().uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("POST /login — 200 with JWT tokens for valid credentials")
    void login_validCredentials_returnsJwtTokens() throws Exception {
        String email = uniqueEmail();
        String password = "pass1234";

        // Register first
        webTestClient.post().uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"email\":\"%s\",\"username\":\"u-%s\",\"password\":\"%s\"}",
                email, UUID.randomUUID().toString().substring(0, 8), password))
            .exchange()
            .expectStatus().isOk();

        // Login
        webTestClient.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"email\":\"%s\",\"password\":\"%s\"}", email, password))
            .exchange()
            .expectStatus().isOk()
            .expectBody(JsonNode.class)
            .value(json -> {
                String access = json.get("accessToken").asText();
                String refresh = json.get("refreshToken").asText();
                String type = json.get("tokenType").asText();
                org.junit.jupiter.api.Assertions.assertNotNull(access);
                org.junit.jupiter.api.Assertions.assertNotNull(refresh);
                org.junit.jupiter.api.Assertions.assertEquals("Bearer", type);
                org.junit.jupiter.api.Assertions.assertFalse(access.isBlank());
                org.junit.jupiter.api.Assertions.assertFalse(refresh.isBlank());
            });
    }

    @Test
    @DisplayName("POST /login — 400 with wrong password")
    void login_wrongPassword_returns400() {
        String email = uniqueEmail();

        webTestClient.post().uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"email\":\"%s\",\"username\":\"u-%s\",\"password\":\"rightone\"}",
                email, UUID.randomUUID().toString().substring(0, 8)))
            .exchange()
            .expectStatus().isOk();

        webTestClient.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(String.format(
                "{\"email\":\"%s\",\"password\":\"wrongone\"}", email))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("GET /me — 401 without authentication")
    void me_unauthenticated_returns401() {
        webTestClient.get().uri("/api/v1/auth/me")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("GET /me — 200 with mocked user (filter chain permitAll on this path)")
    void me_authenticated_returns200() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        // The /api/v1/auth/** path is permitAll in SecurityConfig, so the request
        // reaches the controller regardless of auth header. With mockUser the
        // Authentication is present and the controller returns the user info.
        webTestClient.mutateWith(mockUser(userId.toString()))
            .get().uri("/api/v1/auth/me")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(userId.toString());
    }
}
