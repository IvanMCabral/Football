package com.footballmanager.application.service.simulation.v24;

import java.util.Arrays;
import java.util.Objects;

/**
 * LIVE-MATCH-F2-LIVE Fase 1 — Task B2.
 *
 * <p>Maps each match minute (1-90) to the index in {@link CachingRandomWrapper}'s
 * double cache where that minute's draws begin. Used by
 * {@code V24LiveSession.replayFromMinute(int minute)} to translate a minute
 * argument into a precise cache-truncation index:
 * <pre>
 *   wrapper.invalidateFromIndex(index.indexForMinute(m));
 * </pre>
 *
 * <p><b>Boundary semantics (length 91)</b>:
 * <ul>
 *   <li>{@code boundaries[0]} = 0 — first draw of minute 1 (always 0).</li>
 *   <li>{@code boundaries[m]} for m in [1, 89] = first draw index of minute (m+1).</li>
 *   <li>{@code boundaries[90]} = {@code totalDoubles} — end-of-match sentinel
 *       (exclusive upper bound; not a real cache position, but useful for
 *       {@code invalidateFromIndex(boundaries[90])} to clear the whole cache).</li>
 * </ul>
 *
 * <p><b>Current approximation</b>: this first cut distributes the total
 * double count UNIFORMLY across the 90 minutes (i.e., ~150 doubles per
 * minute given the expected ~13.500 doubles per match). This is a safe
 * lower bound for replay invalidation: the engine consumes roughly the
 * same number of doubles per minute (chanceProbability roll, shot
 * onTarget/goal roll, foul/injury rolls, corner/offside rolls) — the
 * distribution is close enough to uniform that replay from minute
 * {@code m} will preserve the correct first-90-from-original-cache for
 * the previous minutes while discarding the right N doubles from the
 * new replay onward.
 *
 * <p><b>Future refinement (B4 / F2)</b>: when the engine is instrumented
 * with a {@code markMinuteBoundary()} callback (B4 of the F1 plan), the
 * wrapper can record the EXACT draw index at the end of each minute.
 * {@link #buildFromWrapperBoundaryMarks(int[])} accepts those precise
 * marks and stores them verbatim. For F1 the uniform approximation is
 * sufficient because:
 * <ol>
 *   <li>Replay from a manager action is monotonic (the cache prefix
 *       before minute M is always preserved verbatim).</li>
 *   <li>Determinism is preserved because the inner {@code Random} is
 *       seeded the same way on every replay — even if the boundary is
 *       off-by-N doubles, the sequence from the truncation point onward
 *       is still deterministic for a given seed + context.</li>
 *   <li>The 4 RED tests of F0 only validate that substitution TIMING
 *       matters (early vs late produce different results), not the exact
 *       number of doubles per minute.</li>
 * </ol>
 *
 * <p><b>Invariants</b>:
 * <ul>
 *   <li>Length = 91.</li>
 *   <li>Strictly non-decreasing.</li>
 *   <li>{@code boundaries[0] == 0}.</li>
 *   <li>{@code boundaries[90] == endOfMatchIndex() > boundaries[89]} (sentinel).</li>
 * </ul>
 */
public record DoubleCacheIndex(int[] minuteBoundaries) {

    public DoubleCacheIndex {
        Objects.requireNonNull(minuteBoundaries, "minuteBoundaries must not be null");
        if (minuteBoundaries.length != 91) {
            throw new IllegalArgumentException(
                "minuteBoundaries must have length 91 (0..89 = start of minute 1..90, "
                + "boundaries[90] = end-of-match sentinel), got " + minuteBoundaries.length);
        }
        if (minuteBoundaries[0] != 0) {
            throw new IllegalArgumentException(
                "minuteBoundaries[0] must be 0 (start of minute 1), got " + minuteBoundaries[0]);
        }
        for (int m = 1; m < minuteBoundaries.length; m++) {
            if (minuteBoundaries[m] < minuteBoundaries[m - 1]) {
                throw new IllegalArgumentException(
                    "minuteBoundaries must be non-decreasing; at index " + m
                    + " got " + minuteBoundaries[m] + " < " + minuteBoundaries[m - 1]);
            }
        }
    }

    /**
     * Approximate factory: distribute {@code totalDoubles} uniformly across
     * the 90 minutes, rounding each bucket to the nearest integer.
     *
     * <p>Boundaries (length 91):
     * <ul>
     *   <li>[0] = 0 (start of minute 1)</li>
     *   <li>[m] for m in [1, 89] = cumulative draw count at start of minute (m+1)</li>
     *   <li>[90] = totalDoubles (end-of-match sentinel)</li>
     * </ul>
     *
     * @param totalDoubles total number of doubles consumed in a full match
     *                     (typically ~13.500 per the refactor analysis)
     */
    public static DoubleCacheIndex buildUniformApproximation(int totalDoubles) {
        if (totalDoubles < 90) {
            throw new IllegalArgumentException(
                "totalDoubles must be >= 90 (at least 1 per minute), got " + totalDoubles);
        }
        int[] boundaries = new int[91];
        int perMinute = totalDoubles / 90;
        int remainder = totalDoubles - perMinute * 90;
        int cumulative = 0;
        for (int m = 0; m < 90; m++) {
            boundaries[m] = cumulative;
            cumulative += perMinute + (m < remainder ? 1 : 0);
        }
        // boundaries[90] = end-of-match sentinel = totalDoubles.
        boundaries[90] = totalDoubles;
        return new DoubleCacheIndex(boundaries);
    }

    /**
     * Precise factory (FUTURE — wired in B4 when the engine emits
     * {@code markMinuteBoundary()} callbacks).
     *
     * <p>Accepts an int[] of length 91 where {@code marks[0]=0},
     * {@code marks[m]} for m in [1,89] = first draw of minute (m+1),
     * and {@code marks[90]} = end-of-match sentinel. Cloned defensively.
     */
    public static DoubleCacheIndex buildFromWrapperBoundaryMarks(int[] boundaryMarks) {
        return new DoubleCacheIndex(boundaryMarks.clone());
    }

    /**
     * Return the cache index where minute {@code minute} (1-indexed, 1-90)
     * begins. Callers (typically {@code V24LiveSession.replayFromMinute})
     * pass this directly to {@code CachingRandomWrapper.invalidateFromIndex}.
     *
     * @param minute 1-indexed match minute in [1, 90]
     * @return the draw index of the first double consumed in {@code minute}
     * @throws IllegalArgumentException if {@code minute} is out of range
     */
    public int indexForMinute(int minute) {
        if (minute < 1 || minute > 90) {
            throw new IllegalArgumentException(
                "minute must be in [1, 90], got " + minute);
        }
        return minuteBoundaries[minute - 1];
    }

    /**
     * Return the end-of-match sentinel (= {@code boundaries[90]}).
     * Useful for callers that want to clear the whole cache:
     * {@code invalidateFromIndex(index.endOfMatchIndex())}.
     */
    public int endOfMatchIndex() {
        return minuteBoundaries[90];
    }

    /**
     * Defensive copy of the underlying array.
     */
    @Override
    public int[] minuteBoundaries() {
        return minuteBoundaries.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DoubleCacheIndex that)) return false;
        return Arrays.equals(minuteBoundaries, that.minuteBoundaries);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(minuteBoundaries);
    }
}
