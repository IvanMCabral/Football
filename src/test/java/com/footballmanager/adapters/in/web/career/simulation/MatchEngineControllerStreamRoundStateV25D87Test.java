package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.engine.match.MatchEngine;
import com.footballmanager.application.engine.model.RoundState;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.Score;
import com.footballmanager.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 * <p>Pre-fix symptom (per task file):
 * <ul>
 *   <li>Cliente conecta a {@code /api/v1/match-engine/rounds/{roundId}/stream}.</li>
 *   <li>Solo 1 evento inicial llega al cliente.</li>
 *   <li>Los 89 eventos siguientes (currentMinute=1→90) NO llegan.</li>
 *   <li>El {@code COMPLETED} final NO llega.</li>
 * </ul>
 *
 * <p>F1 root cause: {@code RoundEngine.stateSink} was using
 * {@code Sinks.many().multicast().onBackpressureBuffer()}, which the
 * downstream Spring {@code ServerSentEventHttpMessageWriter} +
 * {@code Jackson2JsonEncoder} back-pressures against. The producer's
 * {@code tryEmitNext()} returns {@code FAIL_OVERFLOW} when the underlying
 * serializer pauses, and emissions are silently dropped.
 *
 * <p>F1 fix: switch to {@code Sinks.many().replay().latest()} (same pattern
 * as {@code CareerNotificationService}, the proven-working SSE sink).
 * Caches the latest value and delivers every subsequent emit to all
 * currently-subscribed consumers.
 *
 * <p>This test uses raw {@link WebClient} (not {@code WebTestClient} —
 * the latter's {@code exchange()} blocks on body completion and is the
 * wrong tool for infinite SSE streams) to validate the fix end-to-end.
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
@DisplayName("V25D87.1 — MatchEngineController.streamRoundState SSE wire-up regression")
class MatchEngineControllerStreamRoundStateV25D87Test extends AbstractIntegrationTest {

    private static final String SEED_USER_ID = "00000000-0000-0000-0000-000000000001";

    @LocalServerPort
    private int port;

    @Autowired
    private RoundEngineRegistry roundEngineRegistry;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void cleanup() {
        if (roundEngineRegistry.getActiveRoundCount() > 0) {
            roundEngineRegistry.stopAllEngines();
        }
    }

    @BeforeEach
    void seedLaLiga() {
        seedLaLigaForUser(UUID.fromString(SEED_USER_ID));
    }

    @Test
    @DisplayName("Pre-subscribed SSE client receives all 3+ emits (initial + ticks + COMPLETED) through full Spring chain")
    void streamRoundState_fullSpringChain_deliversAllEmits() {
        // (1) Stand up a RoundEngine + 1 fake match under a random roundId.
        //     We do this manually (bypassing RoundController.startRound) so
        //     the test is deterministic and doesn't depend on game/Career seeding.
        UUID roundId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        RoundEngine engine = new RoundEngine(roundId);

        MatchEngine matchEngine = mock(MatchEngine.class);
        when(matchEngine.isFinished()).thenReturn(false);
        when(matchEngine.isPaused()).thenReturn(false);
        MatchStateSnapshot runningState = new MatchStateSnapshot(
            matchId,
            UUID.randomUUID(), UUID.randomUUID(),
            0, MatchStatus.RUNNING, new Score(0, 0), List.of(),
            "test-career", "test-user"
        );
        lenient().when(matchEngine.getCurrentState()).thenReturn(runningState);
        engine.registerMatch(matchId, matchEngine);

        // Register BEFORE start so the SSE controller's lookup succeeds.
        roundEngineRegistry.register(roundId, engine);

        try {
            // (2) Build a raw WebClient pointed at the random-port test server.
            //     WebClient treats text/event-stream as a Flux of decoded items
            //     via .bodyToFlux(...) — no blocking on body completion.
            WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

            Flux<RoundState> stream = webClient.get()
                .uri("/api/v1/match-engine/rounds/{roundId}/stream", roundId)
                .headers(headers -> headers.setBearerAuth(jwtTokenProvider.generateToken(SEED_USER_ID, "USER")))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(RoundState.class)
                .timeout(Duration.ofSeconds(10));   // safety timeout so a stuck stream doesn't hang the JVM

            List<RoundState> received = new CopyOnWriteArrayList<>();
            stream.subscribe(received::add, err -> {
                System.out.println("[V25D87.1-INT] stream error: " + err);
            });

            // (3) Start the engine (emits #1 with currentMinute=0) + let scheduler run.
            engine.start();
            Thread.sleep(2_500);  // ~5 ticks @ 500ms cadence

            // (4) Drive completion.
            engine.emitCompletedState();
            engine.stop();
            Thread.sleep(500);

            // (5) Validate the full Spring SSE chain delivered the events.
            System.out.println("[V25D87.1-INT] SSE stream delivered " + received.size() + " events");
            System.out.println("[V25D87.1-INT] statuses: " + received.stream()
                .map(r -> r.getStatus().name())
                .toList());

            // Pre-fix would deliver exactly 1 event (currentMinute=0). Post-fix
            // delivers >= 5 events incl. at least one COMPLETED.
            assertTrue(received.size() >= 3,
                "Expected >= 3 events through full Spring SSE chain; got " + received.size()
                    + " (pre-fix this would have been 1). Statuses: "
                    + received.stream().map(r -> r.getStatus().name()).toList());

            boolean sawCompleted = received.stream()
                .anyMatch(r -> r.getStatus() == RoundState.RoundStatus.COMPLETED);
            assertTrue(sawCompleted,
                "Expected COMPLETED event in SSE stream. Got "
                    + received.stream().map(r -> r.getStatus().name()).toList());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for ticks", e);
        }
    }
}
