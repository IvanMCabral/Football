package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1b: Unit tests for V24CareerMutationResult.
 * Immutable value object with 7 factory methods, defensive copy semantics, and partial-failure logic.
 * Focus: null safety, defensive copy isolation, partial-failure logic per factory.
 */
class V24CareerMutationResultTest {

    // ========== empty() ==========

    @Test
    void empty_returnsZeroCounts_emptyFailures_notPartial() {
        V24CareerMutationResult r = V24CareerMutationResult.empty();
        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertEquals(0, r.disciplineApplied());
        assertEquals(0, r.formApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    // ========== success() ==========

    @Test
    void success_returnsProvidedCounts_emptyFailures_notPartial() {
        V24CareerMutationResult r = V24CareerMutationResult.success(2, 5, 1, 3);
        assertEquals(2, r.injuriesApplied());
        assertEquals(5, r.fatigueApplied());
        assertEquals(1, r.disciplineApplied());
        assertEquals(3, r.formApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void success_withZeroCounts_isValid() {
        V24CareerMutationResult r = V24CareerMutationResult.success(0, 0, 0, 0);
        assertEquals(0, r.injuriesApplied());
        assertFalse(r.partialFailure());
    }

    // ========== failure(String) / failure(String, boolean) ==========

    @Test
    void failureSingleMessage_returnsOneFailure_notPartial() {
        V24CareerMutationResult r = V24CareerMutationResult.failure("DB error");
        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertEquals(1, r.failures().size());
        assertEquals("DB error", r.failures().get(0));
        assertFalse(r.partialFailure());
    }

    @Test
    void failureSingleMessage_partialTrue_setsPartial() {
        V24CareerMutationResult r = V24CareerMutationResult.failure("DB error", true);
        assertTrue(r.partialFailure());
        assertEquals(1, r.failures().size());
    }

    // ========== failure(int, int, String) — partial derived from counts ==========

    @Test
    void failureWithCounts_bothZero_notPartial() {
        V24CareerMutationResult r = V24CareerMutationResult.failure(0, 0, "transient");
        assertFalse(r.partialFailure());
        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
    }

    @Test
    void failureWithCounts_injuriesPositive_partial() {
        V24CareerMutationResult r = V24CareerMutationResult.failure(1, 0, "mid-error");
        assertTrue(r.partialFailure());
        assertEquals(1, r.injuriesApplied());
    }

    @Test
    void failureWithCounts_fatiguePositive_partial() {
        V24CareerMutationResult r = V24CareerMutationResult.failure(0, 1, "mid-error");
        assertTrue(r.partialFailure());
        assertEquals(1, r.fatigueApplied());
    }

    // ========== failure(List) and failure(List, boolean) — null safety ==========

    @Test
    void failureList_null_emptyListNoNpe() {
        V24CareerMutationResult r = V24CareerMutationResult.failure((List<String>) null);
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void failureList_nullPartialTrue_emptyListNoNpe() {
        V24CareerMutationResult r = V24CareerMutationResult.failure((List<String>) null, true);
        assertTrue(r.failures().isEmpty());
        assertTrue(r.partialFailure());
    }

    // ========== failure(int, int, List, boolean) — null safety ==========

    @Test
    void failureInjuriesFatigueList_nullMessages_emptyListNoNpe() {
        V24CareerMutationResult r = V24CareerMutationResult.failure(1, 0, null, true);
        assertTrue(r.failures().isEmpty());
        assertTrue(r.partialFailure());
        assertEquals(1, r.injuriesApplied());
    }

    // ========== failure(int, int, int, List, boolean) — null safety ==========

    @Test
    void failureInjuriesFatigueDisciplineList_nullMessages_emptyListNoNpe() {
        V24CareerMutationResult r = V24CareerMutationResult.failure(1, 0, 1, null, true);
        assertTrue(r.failures().isEmpty());
        assertTrue(r.partialFailure());
        assertEquals(1, r.injuriesApplied());
        assertEquals(1, r.disciplineApplied());
    }

    // ========== partial(...) factory — partial logic depends on failures + counts ==========

    @Test
    void partial_nullFailures_emptyListPartialFalse() {
        V24CareerMutationResult r = V24CareerMutationResult.partial(1, 1, 1, 1, null);
        assertTrue(r.failures().isEmpty());
        // partial is false because !defensive.isEmpty() is false
        assertFalse(r.partialFailure());
    }

    @Test
    void partial_emptyListFailures_allCountsNonZero_partialFalse() {
        V24CareerMutationResult r = V24CareerMutationResult.partial(1, 1, 1, 1, Collections.emptyList());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void partial_failuresAndZeroCounts_partialFalse() {
        V24CareerMutationResult r = V24CareerMutationResult.partial(0, 0, 0, 0, List.of("msg"));
        assertEquals(1, r.failures().size());
        // partial requires (injuries>0 || fatigue>0 || discipline>0 || form>0) — all zero → false
        assertFalse(r.partialFailure());
    }

    @Test
    void partial_failuresAndNonZeroCounts_partialTrue() {
        V24CareerMutationResult r = V24CareerMutationResult.partial(1, 0, 0, 0, List.of("msg"));
        assertEquals(1, r.failures().size());
        assertTrue(r.partialFailure());
    }

    // ========== Defensive copy ==========

    @Test
    void failureList_defensiveCopy_callerMutationDoesNotAffectResult() {
        ArrayList<String> source = new ArrayList<>();
        source.add("original");
        V24CareerMutationResult r = V24CareerMutationResult.failure(source);
        source.add("added-after-construction");
        // r must NOT see the later-added entry
        assertEquals(1, r.failures().size());
        assertEquals("original", r.failures().get(0));
    }

    @Test
    void failureList_isImmutable_throwsOnAdd() {
        V24CareerMutationResult r = V24CareerMutationResult.failure(List.of("x"));
        assertThrows(UnsupportedOperationException.class,
                () -> r.failures().add("y"));
    }

    // ========== toString ==========

    @Test
    void toString_includesAllSixFields() {
        V24CareerMutationResult r = V24CareerMutationResult.success(1, 2, 3, 4);
        String s = r.toString();
        assertTrue(s.contains("injuries"));
        assertTrue(s.contains("fatigue"));
        assertTrue(s.contains("discipline"));
        assertTrue(s.contains("form"));
        assertTrue(s.contains("failures"));
        assertTrue(s.contains("partial"));
    }
}
