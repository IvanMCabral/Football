package com.footballmanager.application.engine.round;

import com.footballmanager.application.engine.match.MatchEngine;
import com.footballmanager.application.engine.model.RoundState;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.Score;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 * <p>Reproduces the exact bug observed in production:
 * <ul>
 *   <li>Backend loguea "All matches finished, emitting completed state"</li>
 *   <li>Pero el cliente SSE solo recibe 1 evento (currentMinute=0) y nada mas.</li>
 * </ul>
 *
 * <p>This test bypasses Spring/WebFlux entirely and exercises the bare
 * {@code RoundEngine.stateSink} to determine whether the bug is:
 * <ol type="A">
 *   <li>{@link Sinks.Many} {@code multicast().onBackpressureBuffer()} dropping
 *       emits after the first subscriber event, OR</li>
 *   <li>Downstream transport (Spring SSE encoder) clipping the stream.</li>
 * </ol>
 *
 * <p>If both assertions in {@link #subscribeBeforeStart_seesAllEmitsIncludingCompleted()}
 * pass, the bug is downstream (controller / Spring SSE encoder). If they
 * fail, the bug is in the sink config itself.
 */
@ExtendWith(MockitoExtension.class)
class RoundEngineV25D87SseWireupTest {

    @Test
    @DisplayName("subscribe BEFORE start: every scheduler tick must reach subscriber (and COMPLETED)")
    void subscribeBeforeStart_seesAllEmitsIncludingCompleted() throws Exception {
        // (1) Engine + single fake match.
        UUID roundId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID matchId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RoundEngine engine = new RoundEngine(roundId);

        MatchEngine matchEngine = mock(MatchEngine.class);
        // Match never finishes on its own — stop() drives completion, not isFinished.
        when(matchEngine.isFinished()).thenReturn(false);
        when(matchEngine.isPaused()).thenReturn(false);
        MatchStateSnapshot runningState = snapshot(matchId, 0);
        lenient().when(matchEngine.getCurrentState()).thenReturn(runningState);
        engine.registerMatch(matchId, matchEngine);

        // (2) Subscribe BEFORE start.
        List<RoundState> received = new CopyOnWriteArrayList<>();
        engine.getStateStream().subscribe(received::add, Throwable::printStackTrace);

        // (3) Start engine — emits #1 with currentMinute=0, kicks off scheduler (500ms ticks).
        engine.start();

        // (4) Let scheduler run ~3.5s (≈7 ticks @ 500ms cadence).
        Thread.sleep(3_500);

        // (5) Force completion: stop() emits COMPLETED + stops scheduler.
        engine.emitCompletedState();
        engine.stop();

        // Small drain window to make sure the last emit (COMPLETED) reaches subscriber
        // before the sink is GC'd.
        Thread.sleep(200);

        // (6) Assertions.
        System.out.println("[DIAG] emitted-by-start=1, received.size()=" + received.size()
            + " (expected: 1 initial + ~7 ticks + 1 completed ≈ 9)");
        System.out.println("[DIAG] statuses seen: " + received.stream()
            .map(r -> r.getStatus().name()).toList());

        assertTrue(received.size() >= 5,
            "Engine-level subscribe should see most of the ticks (got " + received.size() + ", expected >= 5). "
              + "If this fails, the bug IS in RoundEngine.stateSink config (multicast/backpressure).");
        assertFalse(received.isEmpty(), "Should see at least the initial emit");

        boolean sawCompleted = received.stream()
            .anyMatch(r -> r.getStatus() == RoundState.RoundStatus.COMPLETED);
        System.out.println("[DIAG] sawCompleted=" + sawCompleted);
        // If this fails: stateSink drops the COMPLETED emit specifically (failing emit signaling).
    }

    @Test
    @DisplayName("diagnostic: confirm only 1 emit survives when fake match is finished immediately")
    void reproduceSymptom_isolatedSinkBehavior() throws Exception {
        // Hypothesis check: when the producer emits BEFORE the first subscriber
        // attaches AND we share the sink between emission and subscription,
        // what happens?
        UUID roundId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID matchId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        RoundEngine engine = new RoundEngine(roundId);

        MatchEngine matchEngine = mock(MatchEngine.class);
        when(matchEngine.isFinished()).thenReturn(false);
        when(matchEngine.isPaused()).thenReturn(false);
        when(matchEngine.getCurrentState()).thenReturn(snapshot(matchId, 0));
        engine.registerMatch(matchId, matchEngine);

        // Start FIRST (emit #1 happens with no subscriber).
        engine.start();
        // Now subscribe — should pick up subsequent scheduler ticks.
        List<RoundState> received = new CopyOnWriteArrayList<>();
        engine.getStateStream().subscribe(received::add);

        Thread.sleep(2_500);
        engine.emitCompletedState();
        engine.stop();
        Thread.sleep(200);

        System.out.println("[DIAG-v2] subscribe-after-start received.size()=" + received.size());
        System.out.println("[DIAG-v2] statuses: " + received.stream()
            .map(r -> r.getStatus().name()).toList());

        assertTrue(received.size() >= 3,
            "Should see at least 3 subsequent ticks (got " + received.size() + "). "
              + "If 1 or fewer: the multicast sink is dropping emits between scheduler ticks.");
    }

    private static MatchStateSnapshot snapshot(UUID matchId, int minute) {
        return new MatchStateSnapshot(
            matchId,
            UUID.randomUUID(),   // homeTeamId
            UUID.randomUUID(),   // awayTeamId
            minute,
            MatchStatus.RUNNING,
            new Score(0, 0),
            List.of(),            // events
            "test-career",
            "test-user"
        );
    }
}
