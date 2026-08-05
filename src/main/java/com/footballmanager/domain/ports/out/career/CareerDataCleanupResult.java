package com.footballmanager.domain.ports.out.career;

import java.util.Map;

/** Sanitized, observable accounting for one owner-scoped cleanup operation. */
public record CareerDataCleanupResult(
        int patternsEvaluated,
        long keysDiscovered,
        long uniqueKeys,
        long keysRequestedForDeletion,
        long keysActuallyDeleted,
        int batchCount,
        int maxBatchSize,
        String ownerHash,
        int careerCount,
        Map<String, FamilyCleanupResult> familyCounts,
        boolean partialFailure,
        String failedFamily,
        long durationMs) {

    public CareerDataCleanupResult {
        familyCounts = familyCounts == null ? Map.of() : Map.copyOf(familyCounts);
        failedFamily = failedFamily == null ? "" : failedFamily;
    }

    public record FamilyCleanupResult(long discovered, long requested, long deleted) {
    }
}
