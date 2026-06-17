package com.footballmanager.application.service.simulation.v24;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * LIVE-MATCH-F2-LIVE Fase 1 — Task B1.
 *
 * <p>Wraps a {@link Random} and transparently caches every {@code nextDouble()}
 * and {@code nextInt(int)} draw in a list so that the calling engine
 * ({@link V24DetailedMatchEngine}) can be replayed deterministically when the
 * user mutates the match state via {@link V24LiveSession#mutateContext}.
 *
 * <p><b>Why we need this</b>: the V24 engine consumes doubles from THREE
 * independent {@code Random} instances per simulation (lines 54, 73, 74 of
 * {@code V24DetailedMatchEngine.java}). When a manager-applied substitution
 * arrives after the simulation started, we need to re-run the engine from
 * the minute of the change with the SAME random draws to preserve
 * determinism. This wrapper is the mechanism that captures those draws in
 * order so the replay can reproduce them exactly.
 *
 * <p><b>Thread-safety</b>: every method that touches the cache is
 * {@code synchronized} on the wrapper's monitor. The RoundEngine scheduler
 * ticks every 500ms while the SubstitutionController can receive a POST at
 * any moment (R5 from the refactor analysis) — both threads may hit this
 * wrapper concurrently.
 *
 * <p><b>Design choice — composition over inheritance</b>: this class is NOT
 * a subclass of {@code Random}. It wraps a private final {@code Random}
 * instance and overrides the methods that the engine calls. Rationale
 * (anti-pattern section of the F1 prompt): subclassing exposes ~70 methods
 * we don't need and risks accidental override of methods that aren't
 * thread-safe in the JDK's Random. Composition keeps the surface area small.
 *
 * <p><b>NOT cached</b>: {@code nextGaussian()}, {@code nextLong()},
 * {@code nextFloat()}, {@code nextBytes()}, {@code nextBoolean()} — these
 * are not used by the engine, so we propagate to the inner Random without
 * persisting. If a future engine method starts using them, this class must
 * be extended.
 */
public class CachingRandomWrapper {

    /** Inner Random that produces the actual draws. Thread-confined via the wrapper's monitor. */
    private final Random inner;

    /**
     * Persistent cache of every {@code nextDouble()} draw, in the order they were
     * consumed. Stored as a primitive-list of boxed Doubles for read access by
     * tests; production code only consumes via {@link #drainCache()} or
     * {@link #cacheSize()}.
     */
    private final List<Double> doubleCache = new ArrayList<>(16384);

    /**
     * Persistent cache of every {@code nextInt(int bound)} draw: the bound and
     * the value drawn. Used by tests for debugging; the engine itself only
     * needs the double stream.
     */
    private final List<int[]> intCache = new ArrayList<>(1024);

    public CachingRandomWrapper(long seed) {
        this.inner = new Random(seed);
    }

    /**
     * Truncate the cache so that only the first {@code index} doubles and
     * {@code nextInt} draws remain. Subsequent draws will resume producing
     * new values (which will be appended to the truncated cache).
     *
     * <p>Used by {@code V24LiveSession.replayFromMinute(int minute)} to tell
     * the wrapper "forget everything from minute M onwards — we are about to
     * re-run the engine and want it to use new draws".
     *
     * @param index the new cache size (in doubles). If {@code index >= size},
     *              this is a no-op.
     */
    public synchronized void invalidateFromIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0, got " + index);
        }
        if (index >= doubleCache.size()) {
            return;
        }
        // subList + clear is the canonical ArrayList truncation pattern.
        doubleCache.subList(index, doubleCache.size()).clear();
        // Drop intCache entries whose draw order is >= index. We don't have a
        // direct mapping from int-draw-index to double-draw-index, so we wipe
        // the intCache conservatively. The engine uses intCache sparingly
        // (only inside V24PlayerSelector) so the loss is bounded.
        // For LIVE-MATCH-F2-LIVE Fase 1 we wipe from the start once the double
        // cache is invalidated past the first int-draw.
        intCache.clear();
    }

    /**
     * Delegate {@code nextDouble()} to the inner Random, persist the value,
     * and return it. Hot path — kept lean (no logging, no allocation).
     */
    public synchronized double nextDouble() {
        double v = inner.nextDouble();
        doubleCache.add(v);
        return v;
    }

    /**
     * Delegate {@code nextInt(int bound)} to the inner Random, persist the
     * draw (for debugging), and return the value.
     *
     * <p>Bound validation is left to {@link Random#nextInt(int)}.
     */
    public synchronized int nextInt(int bound) {
        int v = inner.nextInt(bound);
        intCache.add(new int[]{bound, v});
        return v;
    }

    // ========== Uncached delegation ==========
    // These methods propagate to the inner Random WITHOUT caching because
    // the V24 engine does not call them. If that changes, the F1 prompt
    // requires explicit cache support.

    public synchronized double nextGaussian() {
        return inner.nextGaussian();
    }

    public synchronized long nextLong() {
        return inner.nextLong();
    }

    public synchronized float nextFloat() {
        return inner.nextFloat();
    }

    public synchronized boolean nextBoolean() {
        return inner.nextBoolean();
    }

    public synchronized void nextBytes(byte[] bytes) {
        inner.nextBytes(bytes);
    }

    public synchronized void setSeed(long seed) {
        inner.setSeed(seed);
    }

    // ========== Read-only accessors (for tests + B3 V24LiveSession) ==========

    /** Current size of the double cache. */
    public synchronized int cacheSize() {
        return doubleCache.size();
    }

    /**
     * Return a defensive copy of the double cache AND clear the internal
     * state. Intended for tests that want to assert the exact sequence of
     * draws without holding a reference to the wrapper.
     *
     * <p>Production code should prefer {@link #cacheSize()} for monitoring
     * and {@link #invalidateFromIndex(int)} for the replay invalidation flow.
     */
    public synchronized List<Double> drainCache() {
        List<Double> copy = new ArrayList<>(doubleCache);
        doubleCache.clear();
        intCache.clear();
        return copy;
    }

    /**
     * Read-only snapshot of the double cache (does NOT clear). For tests
     * and metrics.
     */
    public synchronized List<Double> snapshotCache() {
        return new ArrayList<>(doubleCache);
    }
}
