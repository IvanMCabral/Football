package com.footballmanager.domain.ports.out.career;

import java.util.Map;

/** Sanitized, observable accounting for one owner-scoped cleanup operation. */
public record CareerDataCleanupResult(
        int patternsEvaluated,
        long keysDiscovered,
        long uniqueKeys,
        long keysRequestedForDeletion,
        long keysActuallyDeleted,
        long missingAtDelete,
        long unexplainedShortfall,
        int batchCount,
        int maxBatchSize,
        String ownerHash,
        int careerCount,
        int ownershipMismatchCount,
        Map<String, FamilyCleanupResult> familyCounts,
        boolean partialFailure,
        String failedFamily,
        String failureReason,
        Status status,
        long durationMs,
        Map<String, String> diagnostics) {

    /** Backward-compatible constructor for adapters and tests that only need accounting. */
    public CareerDataCleanupResult(
            int patternsEvaluated,
            long keysDiscovered,
            long uniqueKeys,
            long keysRequestedForDeletion,
            long keysActuallyDeleted,
            long missingAtDelete,
            long unexplainedShortfall,
            int batchCount,
            int maxBatchSize,
            String ownerHash,
            int careerCount,
            int ownershipMismatchCount,
            Map<String, FamilyCleanupResult> familyCounts,
            boolean partialFailure,
            String failedFamily,
            String failureReason,
            Status status,
            long durationMs) {
        this(patternsEvaluated, keysDiscovered, uniqueKeys, keysRequestedForDeletion,
                keysActuallyDeleted, missingAtDelete, unexplainedShortfall, batchCount,
                maxBatchSize, ownerHash, careerCount, ownershipMismatchCount, familyCounts,
                partialFailure, failedFamily, failureReason, status, durationMs, Map.of());
    }

    public CareerDataCleanupResult {
        familyCounts = familyCounts == null ? Map.of() : Map.copyOf(familyCounts);
        failedFamily = failedFamily == null ? "" : failedFamily;
        failureReason = failureReason == null ? "" : failureReason;
        status = status == null ? Status.FAILED : status;
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }

    public String diagnostic(String key) {
        return diagnostics.getOrDefault(key, "");
    }

    public enum Status {
        NOT_STARTED,
        IN_PROGRESS,
        PARTIAL_RETRYABLE,
        COMPLETED,
        REJECTED_OWNERSHIP,
        FAILED
    }

    public record FamilyCleanupResult(long discovered, long requested, long deleted) {
    }
}
