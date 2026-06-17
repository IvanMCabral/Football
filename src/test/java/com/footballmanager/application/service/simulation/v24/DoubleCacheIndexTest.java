package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE-MATCH-F2-LIVE Fase 1 — Task B2 unit tests for {@link DoubleCacheIndex}.
 *
 * <p>The index is the bridge between minute-level reasoning (in
 * {@code V24LiveSession}) and draw-level cache manipulation (in
 * {@code CachingRandomWrapper}). These tests validate the indexing
 * invariants that the replay path relies on.
 */
class DoubleCacheIndexTest {

    @Test
    @DisplayName("buildUniformApproximation(13500) produces 91 boundaries (0..90), end-of-match sentinel == 13500")
    void buildUniform_totalMatchesInput() {
        int total = 13500;
        DoubleCacheIndex idx = DoubleCacheIndex.buildUniformApproximation(total);

        assertEquals(91, idx.minuteBoundaries().length);
        assertEquals(0, idx.minuteBoundaries()[0],
            "minute 1 must start at index 0");
        assertEquals(total, idx.endOfMatchIndex(),
            "endOfMatchIndex() must equal totalDoubles (" + total + ")");
    }

    @Test
    @DisplayName("buildUniformApproximation boundaries are strictly non-decreasing")
    void buildUniform_boundariesMonotonic() {
        DoubleCacheIndex idx = DoubleCacheIndex.buildUniformApproximation(13500);
        int[] boundaries = idx.minuteBoundaries();
        for (int m = 1; m < boundaries.length; m++) {
            assertTrue(boundaries[m] >= boundaries[m - 1],
                "Boundaries must be non-decreasing: at index " + m
                + " got " + boundaries[m] + " < previous " + boundaries[m - 1]);
        }
    }

    @Test
    @DisplayName("buildUniformApproximation: each bucket (1..90) gets total/90 ± 1 (rounding)")
    void buildUniform_bucketSizeCorrect() {
        int total = 13500;
        int expectedPerMinute = total / 90; // 150
        DoubleCacheIndex idx = DoubleCacheIndex.buildUniformApproximation(total);
        int[] boundaries = idx.minuteBoundaries();
        // boundaries[0..90] inclusive. boundaries[90] = end-of-match sentinel.
        // Each minute (1..90) consumes a bucket of size ~expectedPerMinute.
        for (int m = 0; m < 90; m++) {
            int bucketSize = boundaries[m + 1] - boundaries[m];
            assertTrue(bucketSize == expectedPerMinute || bucketSize == expectedPerMinute + 1,
                "Bucket " + m + " (minute " + (m + 1) + ") size " + bucketSize
                + " not in [" + expectedPerMinute + ", " + (expectedPerMinute + 1) + "]");
        }
    }

    @Test
    @DisplayName("buildUniformApproximation with total < 90 throws IllegalArgumentException")
    void buildUniform_tooSmall_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> DoubleCacheIndex.buildUniformApproximation(50));
    }

    @Test
    @DisplayName("indexForMinute(1) returns boundaries[0] (always 0)")
    void indexForMinute_first_returnsZero() {
        DoubleCacheIndex idx = DoubleCacheIndex.buildUniformApproximation(9000);
        assertEquals(0, idx.indexForMinute(1));
    }

    @Test
    @DisplayName("indexForMinute(90) returns boundaries[89] (start of minute 90)")
    void indexForMinute_last_returnsStartOfLastMinute() {
        int total = 9000;
        DoubleCacheIndex idx = DoubleCacheIndex.buildUniformApproximation(total);
        // indexForMinute(90) = boundaries[89] = start of minute 90
        // (NOT totalDoubles — that's boundaries[90] = endOfMatchIndex()).
        int expected = total - (total / 90); // 9000 - 100 = 8900
        assertEquals(expected, idx.indexForMinute(90));
        assertEquals(total, idx.endOfMatchIndex());
    }

    @Test
    @DisplayName("indexForMinute(m) < indexForMinute(m+1) for any m in [1, 89]")
    void indexForMinute_monotonic() {
        DoubleCacheIndex idx = DoubleCacheIndex.buildUniformApproximation(9000);
        for (int m = 1; m < 89; m++) {
            assertTrue(idx.indexForMinute(m) < idx.indexForMinute(m + 1),
                "Index must be strictly increasing: minute " + m + " = " + idx.indexForMinute(m)
                + " >= minute " + (m + 1) + " = " + idx.indexForMinute(m + 1));
        }
    }

    @Test
    @DisplayName("indexForMinute(out of range) throws IllegalArgumentException")
    void indexForMinute_outOfRange_throws() {
        DoubleCacheIndex idx = DoubleCacheIndex.buildUniformApproximation(9000);
        assertThrows(IllegalArgumentException.class, () -> idx.indexForMinute(0));
        assertThrows(IllegalArgumentException.class, () -> idx.indexForMinute(91));
        assertThrows(IllegalArgumentException.class, () -> idx.indexForMinute(-1));
    }

    @Test
    @DisplayName("buildFromWrapperBoundaryMarks accepts a length-91 array and exposes it (clone)")
    void buildFromBoundaryMarks_works() {
        int[] marks = new int[91];
        for (int m = 0; m < 90; m++) marks[m] = m * 10;
        marks[90] = 900; // end-of-match sentinel
        DoubleCacheIndex idx = DoubleCacheIndex.buildFromWrapperBoundaryMarks(marks);

        assertEquals(91, idx.minuteBoundaries().length);
        assertEquals(0, idx.indexForMinute(1));
        assertEquals(890, idx.indexForMinute(90));
        assertEquals(900, idx.endOfMatchIndex());
    }

    @Test
    @DisplayName("Constructor rejects arrays of length != 91")
    void constructor_wrongLength_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new DoubleCacheIndex(new int[90]));
        assertThrows(IllegalArgumentException.class,
            () -> new DoubleCacheIndex(new int[92]));
    }

    @Test
    @DisplayName("Constructor rejects arrays with non-zero first element")
    void constructor_nonZeroFirst_throws() {
        int[] bad = new int[91];
        bad[0] = 5; // minute 1 must start at 0
        assertThrows(IllegalArgumentException.class, () -> new DoubleCacheIndex(bad));
    }

    @Test
    @DisplayName("Constructor rejects arrays with decreasing boundaries")
    void constructor_decreasing_throws() {
        int[] bad = new int[91];
        // bad[0] = 0 (valid), bad[1] = 100 (valid), bad[2] = 50 (decreasing → invalid)
        bad[1] = 100;
        bad[2] = 50;
        assertThrows(IllegalArgumentException.class, () -> new DoubleCacheIndex(bad));
    }

    @Test
    @DisplayName("equality and hashCode follow array semantics")
    void equality() {
        DoubleCacheIndex a = DoubleCacheIndex.buildUniformApproximation(9000);
        DoubleCacheIndex b = DoubleCacheIndex.buildUniformApproximation(9000);
        DoubleCacheIndex c = DoubleCacheIndex.buildUniformApproximation(9100);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("minuteBoundaries() returns a defensive copy (mutating it does not affect the index)")
    void defensiveCopy() {
        DoubleCacheIndex idx = DoubleCacheIndex.buildUniformApproximation(9000);
        int[] snapshot = idx.minuteBoundaries();
        snapshot[0] = 999; // attempt to mutate
        assertEquals(0, idx.minuteBoundaries()[0],
            "Internal array must not be mutated via the snapshot");
    }
}
