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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * scenario.
 *
 * <p>Production runtime has 30 matches × detailed match simulation per tick. If any
 * single {@code engine.advanceTick()} throws (e.g., a Detailed corner case),
 * the exception propagates out of {@code RoundEngine.executeTick()}.
 * {@link java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate}
 * suppresses future task executions after the first observed exception,
 * silently killing the SSE emit pipeline.
 *
 * <p>Symptom: client receives the synchronous initial emit from
 * {@link RoundEngine#start()}, then nothing — exactly the runtime bug.
 *
 * <p>This test:
 * <ol>
 *   <li>Stands up a RoundEngine with 2 matches where match #0 throws on
 *       every advanceTick() and match #1 runs fine.</li>
 *   <li>Verifies that AFTER the first throwing tick, the scheduler
 *       keeps emitting subsequent ticks (match #1 keeps progressing).</li>
 * </ol>
 *
 * <p>Pre-fix: scheduler dies after match #0 throws. Only 1 emit ever
 * (the synchronous initial one).
 * <p>Post-fix: scheduler survives the throw, emits subsequent ticks.
 */
@ExtendWith(MockitoExtension.class)
class RoundEngineSchedulerSurvivesExceptionTest {

    @Test
    @DisplayName("scheduler survives an exception from one match's advanceTick")
    void schedulerSurvivesAdvanceTickException() throws Exception {
        UUID roundId = UUID.randomUUID();
        UUID match0Id = UUID.randomUUID();
        UUID match1Id = UUID.randomUUID();
        RoundEngine engine = new RoundEngine(roundId);

        // match #0: throws on every advanceTick()
        MatchEngine match0 = mock(MatchEngine.class);
        when(match0.isFinished()).thenReturn(false);
        when(match0.isPaused()).thenReturn(false);
        lenient().when(match0.getCurrentState()).thenReturn(new MatchStateSnapshot(
            match0Id, UUID.randomUUID(), UUID.randomUUID(),
            0, MatchStatus.RUNNING, new Score(0, 0), List.of(),
            "test-career", "test-user"
        ));
        org.mockito.Mockito.doAnswer(inv -> {
            throw new RuntimeException("[V25D87.1-RUNTIME-TEST] simulated Detailed failure");
        }).when(match0).advanceTick();

        // match #1: runs fine, increments a counter on each tick
        AtomicInteger match1Ticks = new AtomicInteger(0);
        MatchEngine match1 = mock(MatchEngine.class);
        when(match1.isFinished()).thenReturn(false);
        when(match1.isPaused()).thenReturn(false);
        lenient().when(match1.getCurrentState()).thenReturn(new MatchStateSnapshot(
            match1Id, UUID.randomUUID(), UUID.randomUUID(),
            0, MatchStatus.RUNNING, new Score(0, 0), List.of(),
            "test-career", "test-user"
        ));
        org.mockito.Mockito.doAnswer(inv -> {
            match1Ticks.incrementAndGet();
            return null;
        }).when(match1).advanceTick();

        engine.registerMatch(match0Id, match0);
        engine.registerMatch(match1Id, match1);

        // Subscribe BEFORE start so we don't miss any emits.
        List<RoundState> received = new CopyOnWriteArrayList<>();
        engine.getStateStream().subscribe(received::add, Throwable::printStackTrace);

        // Start.
        engine.start();

        // Wait for several ticks. With round-engine scheduler at 500ms and
        // match #1 doing nothing slow, we should see 3+ emits.
        Thread.sleep(2_500);
        engine.emitCompletedState();
        engine.stop();
        Thread.sleep(200);

        System.out.println("[SURVIVE] received=" + received.size() + " match1Ticks=" + match1Ticks.get());
        System.out.println("[SURVIVE] statuses: " + received.stream()
            .map(r -> r.getStatus().name()).toList());

        // The CRITICAL assertion: match #1 ticked at least TWICE after match
        // #0's first throw. Pre-fix the scheduler dies after one throw and
        // match1Ticks stays at 0. Post-fix it must keep growing.
        assertTrue(match1Ticks.get() >= 2,
            "Match #1 should have ticked >= 2 times (got " + match1Ticks.get()
                + "). This proves the scheduler survived match #0's exception.");

        // And we should see >= 3 emits via the SSE chain.
        assertTrue(received.size() >= 3,
            "Expected >= 3 SSE emits; got " + received.size()
                + ". Pre-fix would have been 1 (scheduler died). Statuses: "
                + received.stream().map(r -> r.getStatus().name()).toList());
    }
}
