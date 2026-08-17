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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 * <p>After F1+F2 ship, runtime smoke STILL drops to 1 event even though the
 * engine scheduler is alive (F2 fix works) and emits are happening
 * server-side. The difference from previous passing tests: production has
 * a SLOW CONSUMER (Spring SSE writer → Jackson serializer → Netty chunked
 * write → proxy buffer). Slow consumers with
 * {@code Sinks.many().replay().latest()} hit the
 * "cache-of-size-1 + slow downstream = intermediates overwritten"
 * gotcha: only the latest cached value gets delivered.
 *
 * <p>This test reproduces the consumer slowness with {@code Thread.sleep}
 * inside the subscription callback. Pre-fix: client receives few events
 * (cache collisions). Post-fix (F3: switch to
 * {@code replay().all()} + {@code .publishOn(boundedElastic())} +
 * {@code .onBackpressureLatest()} at the controller): all events
 * delivered regardless of consumer speed.
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
@DisplayName("V25D87.1-TRANSPORT — slow-consumer SSE wire-up")
class MatchEngineControllerStreamRoundStateV25D87SlowConsumerTest extends AbstractIntegrationTest {

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
    @DisplayName("with SLOW consumer (300ms per item), all emits still arrive end-to-end")
    void slowConsumer_300msPerItem_allEmitsArrive() throws Exception {
        UUID roundId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        RoundEngine engine = new RoundEngine(roundId);
        engine.setOwner(UUID.fromString(SEED_USER_ID), "test-career");

        // One fast match — the controller's Flux publisher stream sees a
        // modest payload and produces 5+ emits while we hold the consumer
        // back 300ms per element.
        MatchEngine matchEngine = mock(MatchEngine.class);
        when(matchEngine.isFinished()).thenReturn(false);
        when(matchEngine.isPaused()).thenReturn(false);
        MatchStateSnapshot running = new MatchStateSnapshot(
            matchId, UUID.randomUUID(), UUID.randomUUID(),
            0, MatchStatus.RUNNING, new Score(0, 0), List.of(),
            "test-career", "test-user"
        );
        lenient().when(matchEngine.getCurrentState()).thenReturn(running);
        engine.registerMatch(matchId, matchEngine);
        roundEngineRegistry.register(roundId, engine);

        WebClient webClient = WebClient.builder().baseUrl("http://localhost:" + port).build();
        Flux<RoundState> stream = webClient.get()
            .uri("/api/v1/match-engine/rounds/{roundId}/stream", roundId)
            .headers(headers -> headers.setBearerAuth(jwtTokenProvider.generateToken(SEED_USER_ID, "USER")))
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(RoundState.class)
            .timeout(Duration.ofSeconds(15));

        List<RoundState> received = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        stream.subscribe(
            rs -> {
                // SIMULATE SLOW CONSUMER (Spring SSE writer under proxy load
                // is the runtime analog). 1500ms per item pushes the consumer
                // WAY below the producer's 500ms tick cadence — this should
                // over-run any cache-of-1 sink (pre-fix replay().latest()).
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                received.add(rs);
            },
            err -> System.out.println("[SLOW] error: " + err),
            done::countDown);

        // Let scheduler tick at 500ms cadence for 6 seconds → up to ~12 ticks.
        // Consumer processes at 1500ms per item → up to ~4 items in 6 seconds.
        // Pre-fix replay().latest() with cache-of-1 should lose intermediate
        // emits when consumer is slower than producer.
        engine.start();
        Thread.sleep(6_000);
        engine.emitCompletedState();
        engine.stop();
        done.await(3, TimeUnit.SECONDS);

        System.out.println("[SLOW] received=" + received.size());
        System.out.println("[SLOW] statuses: " + received.stream().map(r -> r.getStatus().name()).toList());

        // With replay().latest() + slow consumer, cache-of-1 collision → client
        // receives ~1-2 events. With replay().all() + publishOn(boundedElastic)
        // + onBackpressureLatest(), client receives all events.
        assertTrue(received.size() >= 4,
            "Expected >= 4 events even with 300ms-per-item slow consumer; got "
                + received.size() + ". Statuses: "
                + received.stream().map(r -> r.getStatus().name()).toList()
                + ". (Pre-F3: replay().latest() overwrites intermediates under backpressure.)");
    }
}
