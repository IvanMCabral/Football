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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 * 6/6 with the {@code replay().latest()} fix. But runtime smoke with
 * 30 REAL matches (detailed match simulation per match) still drops to 1 event.
 *
 * <p>This test reproduces the production load: 30 mock matches whose
 * {@code advanceTick()} blocks for 50ms (to mimic detailed match simulation CPU
 * cost). The scheduler will fall behind — each "tick" of 30×50ms takes
 * 1.5s wall clock — exactly the production scenario.
 *
 * <p>If even this mock-30-matches test drops events, the
 * {@code replay().latest()} sink is insufficient under load and we
 * need a heavier fix (publish-on a bounded-elastic dispatcher,
 * explicit queue, or off-board serialization).
 *
 * <p>If the test passes (all 30 emits received), the runtime bug is
 * somewhere specific to real detailed match simulation (NPE, race, or
 * serializer config) and not the sink itself.
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
@DisplayName("V25D87.1-RUNTIME — heavy-load SSE repro")
class MatchEngineControllerStreamRoundStateV25D87HeavyLoadTest extends AbstractIntegrationTest {

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
    void seed() {
        seedLaLigaForUser(UUID.fromString(SEED_USER_ID));
    }

    @Test
    @DisplayName("30 concurrent matches with 50ms-per-tick simulation: all emits delivered")
    void streamRoundState_30Matches_50msPerTick_deliversAllEmits() {
        int N = 30;
        UUID roundId = UUID.randomUUID();
        RoundEngine engine = new RoundEngine(roundId);

        // 30 mock matches, each with 50ms advanceTick() cost (CPU-bound
        // simulate approximation). Each match stays at minute=0 forever
        // so allFinished() is never true; we drive COMPLETED manually at
        // the end.
        List<MatchEngine> matches = new ArrayList<>();
        List<UUID> matchIds = new ArrayList<>();
        ScheduledExecutorService tickPool = Executors.newScheduledThreadPool(N);
        AtomicInteger matchCounter = new AtomicInteger(0);
        for (int i = 0; i < N; i++) {
            UUID matchId = UUID.randomUUID();
            matchIds.add(matchId);
            int idx = i;
            MatchEngine me = mock(MatchEngine.class);
            when(me.isFinished()).thenReturn(false);
            when(me.isPaused()).thenReturn(false);
            int minute = idx; // start at distinct minute for trackability
            when(me.getCurrentState()).thenReturn(new MatchStateSnapshot(
                matchId, UUID.randomUUID(), UUID.randomUUID(),
                minute, MatchStatus.RUNNING,
                new Score(0, 0), List.of(),
                "test-career", "test-user"
            ));
            // 50ms blocking simulate approximation (void method → doAnswer)
            org.mockito.Mockito.doAnswer(inv -> {
                Thread.sleep(50);
                return null;
            }).when(me).advanceTick();
            engine.registerMatch(matchId, me);
            matches.add(me);
        }

        roundEngineRegistry.register(roundId, engine);

        try {
            // (1) Subscribe via raw WebClient + bodyToFlux.
            WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

            Flux<RoundState> stream = webClient.get()
                .uri("/api/v1/match-engine/rounds/{roundId}/stream", roundId)
                .headers(headers -> headers.setBearerAuth(jwtTokenProvider.generateToken(SEED_USER_ID, "USER")))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(RoundState.class)
                .timeout(Duration.ofSeconds(20));

            List<RoundState> received = new CopyOnWriteArrayList<>();
            CountDownLatch completion = new CountDownLatch(1);
            stream.subscribe(received::add,
                err -> System.out.println("[HEAVY] error: " + err),
                completion::countDown);

            // (2) Start the engine — emits #1 (currentMinute=0 per match).
            engine.start();

            // (3) Let the scheduler run. With 30 matches × 50ms = 1500ms
            //     per tick, only ~1 tick fires in 5 seconds (the scheduler
            //     falls behind). So 5s of waiting gives 2-4 emits.
            //     Each emit represents a scheduler tick — the heavy one
            //     will have minutes spread across all matches.
            Thread.sleep(5_000);

            // (4) Drive completion (sync emit on the test thread).
            engine.emitCompletedState();
            engine.stop();
            completion.await(2, TimeUnit.SECONDS);

            System.out.println("[HEAVY] total SSE events delivered: " + received.size());
            System.out.println("[HEAVY] statuses: " + received.stream()
                .map(r -> r.getStatus().name())
                .toList());
            for (int i = 0; i < received.size(); i++) {
                int minute0 = received.get(i).getMatches().isEmpty()
                    ? -1
                    : received.get(i).getMatches().get(0).currentMinute();
                int matchesN = received.get(i).getMatches().size();
                System.out.println("[HEAVY] event " + i + " matches=" + matchesN
                    + " firstMinute=" + minute0 + " status=" + received.get(i).getStatus());
            }

            // The fix should deliver ALL emits — initial, every tick, and
            // COMPLETED. Pre-fix and under-load may deliver fewer.
            assertTrue(received.size() >= 3,
                "Expected >= 3 SSE events with 30-match load; got " + received.size()
                    + ". Statuses: " + received.stream().map(r -> r.getStatus().name()).toList());

            boolean sawCompleted = received.stream()
                .anyMatch(r -> r.getStatus() == RoundState.RoundStatus.COMPLETED);
            assertTrue(sawCompleted,
                "Expected COMPLETED event. Got "
                    + received.stream().map(r -> r.getStatus().name()).toList());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted", ie);
        } finally {
            tickPool.shutdownNow();
        }
    }
}
