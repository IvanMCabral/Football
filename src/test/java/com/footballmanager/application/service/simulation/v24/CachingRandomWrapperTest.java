package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE-MATCH-F2-LIVE Fase 1 — Task B1 unit tests for {@link CachingRandomWrapper}.
 *
 * <p>The wrapper must:
 * <ol>
 *   <li>Produce the EXACT same {@code nextDouble()} sequence as a vanilla
 *       {@code new Random(seed)} (deterministic contract).</li>
 *   <li>Truncate the cache on {@code invalidateFromIndex(N)} — first N draws
 *       preserved, later draws discarded.</li>
 *   <li>Be thread-safe under concurrent draws (0 race conditions with 100
 *       threads × 1000 draws each).</li>
 * </ol>
 *
 * <p>If any of these fails, the engine replay path (B3) is broken —
 * substitutions that should produce the same result will diverge.
 */
class CachingRandomWrapperTest {

    @Test
    @DisplayName("nextDouble() sequence matches new Random(seed) exactly for 10K draws")
    void nextDouble_matchesVanillaRandom_for10kDraws() {
        long seed = 42L;
        Random reference = new Random(seed);
        CachingRandomWrapper wrapper = new CachingRandomWrapper(seed);

        int draws = 10_000;
        for (int i = 0; i < draws; i++) {
            double refValue = reference.nextDouble();
            double wrapperValue = wrapper.nextDouble();
            assertEquals(refValue, wrapperValue,
                "Mismatch at draw " + i + ": reference=" + refValue + " wrapper=" + wrapperValue);
        }

        assertEquals(draws, wrapper.cacheSize(),
            "Wrapper cache should hold exactly " + draws + " doubles");
    }

    @Test
    @DisplayName("invalidateFromIndex(N) preserves first N draws and discards the rest")
    void invalidateFromIndex_preservesPrefixDiscardsSuffix() {
        CachingRandomWrapper wrapper = new CachingRandomWrapper(123L);
        int total = 100;
        for (int i = 0; i < total; i++) {
            wrapper.nextDouble();
        }
        assertEquals(total, wrapper.cacheSize());

        // Snapshot the prefix before invalidation.
        List<Double> prefix = wrapper.snapshotCache().subList(0, 30);

        wrapper.invalidateFromIndex(30);
        assertEquals(30, wrapper.cacheSize(),
            "After invalidateFromIndex(30), cache should hold exactly 30 draws");

        // Verify the prefix is preserved verbatim.
        for (int i = 0; i < 30; i++) {
            assertEquals(prefix.get(i), wrapper.snapshotCache().get(i),
                "Draw " + i + " should be unchanged after invalidation");
        }

        // Continue drawing — the wrapper should resume producing new values
        // (appended starting at index 30).
        double nextValue = wrapper.nextDouble();
        assertEquals(31, wrapper.cacheSize(),
            "After one more nextDouble(), cache should hold 31 draws");
        assertNotNull(nextValue);
        assertTrue(nextValue >= 0.0 && nextValue < 1.0,
            "nextDouble() must be in [0, 1), got " + nextValue);
    }

    @Test
    @DisplayName("invalidateFromIndex(N) where N >= size is a no-op")
    void invalidateFromIndex_pastEnd_isNoOp() {
        CachingRandomWrapper wrapper = new CachingRandomWrapper(456L);
        for (int i = 0; i < 50; i++) wrapper.nextDouble();
        assertEquals(50, wrapper.cacheSize());

        wrapper.invalidateFromIndex(50); // exactly at size → no-op
        assertEquals(50, wrapper.cacheSize());

        wrapper.invalidateFromIndex(100); // past size → no-op
        assertEquals(50, wrapper.cacheSize());
    }

    @Test
    @DisplayName("invalidateFromIndex(-1) throws IllegalArgumentException")
    void invalidateFromIndex_negative_throws() {
        CachingRandomWrapper wrapper = new CachingRandomWrapper(789L);
        assertThrows(IllegalArgumentException.class,
            () -> wrapper.invalidateFromIndex(-1));
    }

    @Test
    @DisplayName("nextInt(bound) returns values in [0, bound) and integrates with drainCache")
    void nextInt_cachesBoundAndValue() {
        CachingRandomWrapper wrapper = new CachingRandomWrapper(321L);
        for (int i = 0; i < 50; i++) {
            int v = wrapper.nextInt(11);
            assertTrue(v >= 0 && v < 11, "nextInt(11) must be in [0, 11), got " + v);
        }
        // nextInt alone does NOT populate the double cache — drainCache()
        // resets both caches (doubles + ints) so we just assert the wrapper
        // is in a usable state afterwards (can keep drawing).
        wrapper.drainCache();
        assertEquals(0, wrapper.cacheSize(), "drainCache() must reset the double cache");

        // After drain, the wrapper keeps producing valid draws.
        double next = wrapper.nextDouble();
        assertTrue(next >= 0.0 && next < 1.0);
        assertEquals(1, wrapper.cacheSize());
    }

    @Test
    @DisplayName("100 threads × 1000 draws each: 0 race conditions, cache has 100K doubles")
    void concurrentDraws_100threads_1000each_noRaceConditions() throws InterruptedException {
        CachingRandomWrapper wrapper = new CachingRandomWrapper(999L);
        int threads = 100;
        int drawsPerThread = 1000;
        int expectedTotal = threads * drawsPerThread;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < drawsPerThread; i++) {
                        double v = wrapper.nextDouble();
                        if (v < 0.0 || v >= 1.0) {
                            throw new AssertionError("nextDouble() out of range: " + v);
                        }
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "Threads did not finish in 30s");
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertFalse(failure.get() != null, "Concurrent draw failed: " + failure.get());
        assertEquals(expectedTotal, wrapper.cacheSize(),
            "After " + threads + " threads × " + drawsPerThread + " draws, cache should have "
            + expectedTotal + " doubles (got " + wrapper.cacheSize() + ")");
    }

    @Test
    @DisplayName("nextDouble() during concurrent invalidateFromIndex: no exceptions, final state consistent")
    void concurrentDrawsAndInvalidates_consistentState() throws InterruptedException {
        CachingRandomWrapper wrapper = new CachingRandomWrapper(111L);
        // Pre-populate so invalidations have something to truncate.
        for (int i = 0; i < 1000; i++) wrapper.nextDouble();

        int workers = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // 10 draw-only threads + 10 invalidate-only threads racing.
        for (int t = 0; t < 10; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 500; i++) wrapper.nextDouble();
                } catch (Throwable e) { failure.compareAndSet(null, e); }
                finally { done.countDown(); }
            });
        }
        for (int t = 0; t < 10; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 100; i++) {
                        // Invalidate to various indices — never negative.
                        wrapper.invalidateFromIndex(i * 10);
                    }
                } catch (Throwable e) { failure.compareAndSet(null, e); }
                finally { done.countDown(); }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertFalse(failure.get() != null, "Concurrent invalidate race: " + failure.get());
        assertTrue(wrapper.cacheSize() >= 0,
            "cacheSize must be >= 0 after concurrent invalidates, got " + wrapper.cacheSize());
    }

    @Test
    @DisplayName("cachedRandom produces IDENTICAL sequence to a fresh wrapper with the same seed (independence)")
    void independentWrappers_sameSeed_sameSequence() {
        long seed = 777L;
        CachingRandomWrapper a = new CachingRandomWrapper(seed);
        CachingRandomWrapper b = new CachingRandomWrapper(seed);

        List<Double> seqA = new ArrayList<>();
        List<Double> seqB = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            seqA.add(a.nextDouble());
            seqB.add(b.nextDouble());
        }
        assertEquals(seqA, seqB,
            "Two independent wrappers with the same seed must produce identical double sequences");
    }
}
