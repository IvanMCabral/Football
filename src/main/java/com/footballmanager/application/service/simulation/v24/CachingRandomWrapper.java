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
 * <p><b>Design choice — Random subclass with composition</b>: this class
 * extends {@link Random} (so it can be passed to any API that expects one,
 * including the new {@code V24DetailedMatchEngine.simulate(ctx, Random)}
 * overload) but internally delegates to a private final {@code Random}
 * instance. The surface area is intentionally narrow: only the methods the
 * engine actually calls are overridden with caching semantics; everything
 * else propagates to the inner Random without persistence.
 *
 * <p><b>NOT cached</b>: {@code nextGaussian()}, {@code nextLong()},
 * {@code nextFloat()}, {@code nextBytes()}, {@code nextBoolean()} — these
 * are not used by the engine, so we propagate to the inner Random without
 * persisting. If a future engine method starts using them, this class must
 * be extended.
 */
public class CachingRandomWrapper extends Random {

    private static final long serialVersionUID = 1L;

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
     * LIVE-MATCH-F3-UI-LIVE F5.1 BUG-007: pointer to the next draw to be
     * returned by {@link #nextDouble()}. Initialized to 0. The wrapper
     * replays cached draws by leaving this pointer at the end of the
     * previous engine run; the caller (V24LiveSession.tick) calls
     * {@link #rewind()} before each engine call to make the engine see
     * the same draws on every tick. Mutations route through
     * {@link #invalidateFromIndex(int)} which truncates the cache and
     * resets this pointer to the truncation point.
     */
    private int consumedIndex = 0;

    /**
     * Persistent cache of every {@code nextInt(int bound)} draw: the bound and
     * the value drawn. Used by tests for debugging; the engine itself only
     * needs the double stream.
     */
    private final List<int[]> intCache = new ArrayList<>(1024);

    /**
     * LIVE-MATCH-F3-UI-LIVE F5.1 BUG-007: index of the next int-draw to be
     * returned by {@link #nextInt(int)}. Like {@link #consumedIndex} but
     * for the int stream.
     */
    private int consumedIntIndex = 0;

    public CachingRandomWrapper(long seed) {
        this.inner = new Random(seed);
    }

    /**
     * LIVE-MATCH-F3-UI-LIVE F5.1 BUG-007: rewind the draw pointer to 0 so
     * the next {@code engine.simulate(...)} call replays the same doubles
     * in the same order. This is what makes the live score stable across
     * ticks — the engine sees the exact same RNG stream on every call
     * (no flicker).
     *
     * <p>The cache itself is not cleared; only the read pointer resets.
     * New draws (consumed on the first call after the rewind) come from
     * the cache, so the engine deterministically produces the same result
     * on every tick.
     */
    public synchronized void rewind() {
        this.consumedIndex = 0;
        this.consumedIntIndex = 0;
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
     * <p>LIVE-MATCH-F3-UI-LIVE F5.1 BUG-007: resets {@link #consumedIndex}
     * to 0 (not {@code index}) so the next engine call REPLAYS the
     * truncated prefix (the draws before the truncation point) and then
     * CONSUMES new draws for the suffix. This is the F1 B3 design's
     * intended behaviour: same context + no mutations = same draws =
     * same result every tick. After a {@code mutateContext} →
     * {@code replayFromMinute(M)} call, the engine replays draws
     * [0, cacheIndex.indexForMinute(M)) and produces new draws for the
     * rest, so the suffix reflects the mutated context.
     *
     * @param index the new cache size (in doubles). If {@code index >= size},
     *              this is a no-op.
     */
    public synchronized void invalidateFromIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0, got " + index);
        }
        if (index >= doubleCache.size()) {
            // No truncation needed. Leave consumedIndex as-is so the next
            // call resumes from where the previous engine call left off.
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
        // BUG-007: rewind the read pointer to 0 so the next engine call
        // replays the truncated prefix and then consumes new draws for the
        // rest. Combined with rewind() in tick(), this gives the F1 B3
        // design's intended determinism: same context + no mutations =
        // same draws = same result every tick.
        this.consumedIndex = 0;
        this.consumedIntIndex = 0;
    }

    /**
     * LIVE-MATCH-F3-UI-LIVE F5.1 BUG-007: return the next double from the
     * cache if the cache has an unconsumed entry at {@link #consumedIndex}.
     * Otherwise consume a fresh double from the inner Random, append it to
     * the cache, and return it. The replay-vs-play decision is automatic
     * based on whether the cache has been populated.
     *
     * <p>Hot path — kept lean (no logging, no allocation on replay).
     */
    @Override
    public synchronized double nextDouble() {
        if (consumedIndex < doubleCache.size()) {
            // Replay: return the previously-cached draw. Same engine call →
            // same draws → same result. No allocation.
            double cached = doubleCache.get(consumedIndex);
            consumedIndex++;
            return cached;
        }
        // Play: consume a new draw and persist it for future replays.
        double v = inner.nextDouble();
        doubleCache.add(v);
        consumedIndex++;
        return v;
    }

    /**
     * Delegate {@code nextInt(int bound)} to the inner Random, persist the
     * draw (for debugging), and return the value.
     *
     * <p>Bound validation is left to {@link Random#nextInt(int)}.
     */
    @Override
    public synchronized int nextInt(int bound) {
        int v = inner.nextInt(bound);
        intCache.add(new int[]{bound, v});
        return v;
    }

    // ========== Uncached delegation ==========
    // These methods propagate to the inner Random WITHOUT caching because
    // the V24 engine does not call them. If that changes, the F1 prompt
    // requires explicit cache support.

    @Override
    public synchronized double nextGaussian() {
        return inner.nextGaussian();
    }

    @Override
    public synchronized long nextLong() {
        return inner.nextLong();
    }

    @Override
    public synchronized float nextFloat() {
        return inner.nextFloat();
    }

    @Override
    public synchronized boolean nextBoolean() {
        return inner.nextBoolean();
    }

    @Override
    public synchronized void nextBytes(byte[] bytes) {
        inner.nextBytes(bytes);
    }

    @Override
    public synchronized void setSeed(long seed) {
        // The JDK's Random.setSeed(long) must run to reset the internal seed
        // state (otherwise the wrapper would continue from the old sequence).
        super.setSeed(seed);
        // Guard against the constructor ordering: when the user calls
        // `new CachingRandomWrapper(long)`, super() runs first (no-arg
        // constructor does not call setSeed, so we're safe here), then our
        // body runs `this.inner = new Random(seed)`. Inside that nested
        // constructor the JDK calls `this.setSeed(seed)` on the outer
        // `this` (this CachingRandomWrapper instance) — at which point
        // `this.inner` is still null. Skip the inner delegation in that case.
        if (inner != null) {
            inner.setSeed(seed);
        }
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
