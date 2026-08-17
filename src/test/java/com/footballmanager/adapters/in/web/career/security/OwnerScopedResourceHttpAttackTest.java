package com.footballmanager.adapters.in.web.career.security;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.engine.match.MatchEngine;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

/** Real SecurityWebFilterChain cross-owner checks for private runtime routes. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.flyway.enabled=false",
        "spring.data.redis.database=15"
    }
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class OwnerScopedResourceHttpAttackTest extends AbstractIntegrationTest {

    private static final UUID OWNER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_B = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RoundEngineRegistry roundEngineRegistry;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanPrivateFixtures() {
        redisTemplate.getConnectionFactory().getReactiveConnection().serverCommands().flushDb().block();
    }

    @AfterEach
    void stopRounds() {
        roundEngineRegistry.stopAllEngines();
    }

    @Test
    void foreignCareerStatsReturns404ThroughSecurityChain() {
        String careerId = UUID.randomUUID().toString();
        ownCareer(OWNER_B, careerId);

        webTestClient.get()
                .uri("/api/v1/careers/{careerId}/seasons/1/player-stats", careerId)
                .headers(headers -> headers.setBearerAuth(token(OWNER_A)))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void staleCanonicalJwtIsRejectedBeforePrivateRoute() {
        String staleSubject = UUID.randomUUID().toString();

        webTestClient.get()
                .uri("/api/v1/careers/{careerId}/seasons/1/player-stats", UUID.randomUUID())
                .headers(headers -> headers.setBearerAuth(token(UUID.fromString(staleSubject))))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void foreignRoundStreamReturns404BeforeSubscription() {
        UUID roundId = UUID.randomUUID();
        RoundEngine engine = new RoundEngine(roundId);
        engine.setOwner(OWNER_B, "career-b");
        roundEngineRegistry.register(roundId, engine);

        webTestClient.get()
                .uri("/api/v1/match-engine/rounds/{roundId}/stream", roundId)
                .headers(headers -> headers.setBearerAuth(token(OWNER_A)))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void foreignPauseAndResumeReturn404WithoutMutation() {
        UUID roundId = UUID.randomUUID();
        RoundEngine engine = new RoundEngine(roundId);
        engine.setOwner(OWNER_B, "career-b");
        roundEngineRegistry.register(roundId, engine);

        webTestClient.post()
                .uri("/api/v1/career/{careerId}/round/{roundId}/pause", "career-b", roundId)
                .headers(headers -> headers.setBearerAuth(token(OWNER_A)))
                .exchange()
                .expectStatus().isNotFound();
        webTestClient.post()
                .uri("/api/v1/career/{careerId}/round/{roundId}/resume", "career-b", roundId)
                .headers(headers -> headers.setBearerAuth(token(OWNER_A)))
                .exchange()
                .expectStatus().isNotFound();
        assertFalse(engine.isPaused());
    }

    @Test
    void foreignMatchRoundResolutionReturns404() {
        UUID roundId = UUID.randomUUID();
        RoundEngine engine = new RoundEngine(roundId);
        engine.setOwner(OWNER_B, "career-b");
        UUID matchId = UUID.randomUUID();
        engine.registerMatch(matchId, mock(MatchEngine.class));
        roundEngineRegistry.register(roundId, engine);

        webTestClient.get()
                .uri("/api/v1/match-engine/matches/{matchId}/roundId", matchId)
                .headers(headers -> headers.setBearerAuth(token(OWNER_A)))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    private void ownCareer(UUID ownerId, String careerId) {
        redisTemplate.opsForValue().set("career-owner:" + careerId, ownerId.toString()).block();
        redisTemplate.opsForValue().set("career-generation:" + careerId, "test-generation").block();
        redisTemplate.opsForSet().add("user:" + ownerId + ":career-ids", careerId).block();
    }

    private String token(UUID ownerId) {
        return jwtTokenProvider.generateToken(ownerId.toString(), "USER");
    }
}
