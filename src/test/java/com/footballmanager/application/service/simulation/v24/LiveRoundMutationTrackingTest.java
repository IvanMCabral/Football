package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1b: Unit tests for LiveRoundMutationTracking.
 * Tests the per-round tracking container used by the live/UI/SSE path.
 * Concurrency-safe ConcurrentHashMap.newKeySet() usage is the key concern.
 */
class LiveRoundMutationTrackingTest {

    @Test
    void constructor_setsRoundAndSeason() {
        LiveRoundMutationTracking t = new LiveRoundMutationTracking(5, 2);
        assertEquals(5, t.roundNumber);
        assertEquals(2, t.seasonNumber);
    }

    @Test
    void allSets_initiallyEmpty() {
        LiveRoundMutationTracking t = new LiveRoundMutationTracking(1, 1);
        assertEquals(0, t.preRoundSuspendedPlayerIds.size());
        assertEquals(0, t.preRoundInjuredPlayerIds.size());
        assertEquals(0, t.newlySuspendedPlayerIds.size());
        assertEquals(0, t.newlyInjuredPlayerIds.size());
        assertEquals(0, t.participatedPlayerIds.size());
    }

    @Test
    void sets_acceptAdds() {
        LiveRoundMutationTracking t = new LiveRoundMutationTracking(1, 1);
        t.preRoundSuspendedPlayerIds.add("p1");
        t.preRoundInjuredPlayerIds.add("p2");
        t.newlySuspendedPlayerIds.add("p3");
        t.newlyInjuredPlayerIds.add("p4");
        t.participatedPlayerIds.add("p5");
        assertTrue(t.preRoundSuspendedPlayerIds.contains("p1"));
        assertTrue(t.preRoundInjuredPlayerIds.contains("p2"));
        assertTrue(t.newlySuspendedPlayerIds.contains("p3"));
        assertTrue(t.newlyInjuredPlayerIds.contains("p4"));
        assertTrue(t.participatedPlayerIds.contains("p5"));
    }

    @Test
    void multipleInstances_areIndependent() {
        LiveRoundMutationTracking a = new LiveRoundMutationTracking(1, 1);
        LiveRoundMutationTracking b = new LiveRoundMutationTracking(2, 1);
        a.preRoundSuspendedPlayerIds.add("p1");
        a.newlyInjuredPlayerIds.add("p2");
        // b must not see a's data (no static state)
        assertEquals(0, b.preRoundSuspendedPlayerIds.size());
        assertEquals(0, b.newlyInjuredPlayerIds.size());
        // round/season are independent
        assertEquals(1, a.roundNumber);
        assertEquals(2, b.roundNumber);
    }

    @Test
    void concurrentAdds_allRetained() throws Exception {
        LiveRoundMutationTracking t = new LiveRoundMutationTracking(1, 1);
        int threadCount = 6;
        int addsPerThread = 100;
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int threadIdx = i;
            futures.add(exec.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < addsPerThread; j++) {
                    t.participatedPlayerIds.add("p-t" + threadIdx + "-" + j);
                }
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
        exec.shutdown();
        // ConcurrentHashMap.newKeySet() must retain all entries; no data loss
        assertEquals(threadCount * addsPerThread, t.participatedPlayerIds.size());
    }
}
